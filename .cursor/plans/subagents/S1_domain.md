---
title: "S1 — :domain, state mutations and read models"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "85 minutes"
---

# S1 — `:domain`

> You own the only code in the app permitted to mutate `CanonicalLifeState`. Everything the user sees on Today, Goals, and Wellbeing is produced by your five classes. You need no emulator and no API key: verify with JVM unit tests, which run in seconds.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Implement `ActionExecutor` over all 25 actions, plus `ProjectionBuilder`, `Compactor`, `RiskCalculator`, and `TimelineMerger`. Deterministic, side-effect-ordered, and defensive against garbage input from the model.

## AI credentials

**None.** Nothing in `:domain` may call an LLM. Risk scores and timelines are arithmetic precisely so they are instant and never wrong on stage.

---

## Files you own

- `domain/src/main/kotlin/com/lifeos/domain/**`
- `domain/src/test/kotlin/com/lifeos/domain/**`

## Files you must NOT touch

`core/**` (the API is frozen — if you think it needs a change, stop and report), `agent/**`, `enforce/**`, `email/**`, `ui/**`, `app/**`, any `build.gradle.kts`.

S0 has already created your five classes as stubs with correct constructors. Fill in the bodies; do not rename or re-sign them.

---

## Contracts you implement

```kotlin
class ActionExecutor(
    private val store: LifeStateStore,
    private val enforce: EnforceGateway,
    private val apps: AppCatalog,
) : ActionExecutorPort {
    override suspend fun execute(actions: List<Action>, origin: ActionOrigin): ExecuteReport
}

class ProjectionBuilder : ProjectionPort {
    override fun build(state: CanonicalLifeState): LifeStateProjection
}

class TimelineMerger : TimelinePort {
    override fun forDate(state: CanonicalLifeState, dateIso: String): List<TimelineItem>
}

class RiskCalculator : RiskPort {
    override fun riskPercent(state: CanonicalLifeState, goalId: String): Int
}

class Compactor(
    private val chat: ChatStore,
    private val maxMessages: Int = 12,
) : CompactorPort {
    override suspend fun ensureWindow()
}
```

## Contracts you consume

All from `:core`, all already stubbed by S0 so you are never blocked:

- `LifeStateStore.state: StateFlow<CanonicalLifeState>` and `suspend fun mutate(block: (CanonicalLifeState) -> CanonicalLifeState)`
- `EnforceGateway` — `startFocus`, `stopFocus`, `applyRules`, `scheduleAlarm`, `cancelAlarm`, `startNetworkGuard`, `stopNetworkGuard`, `usageTodayMinutes`
- `AppCatalog.resolveOrSubstitute(nameOrPackage: String): String?`
- `ChatStore.transcript` and `mutate`
- `Time`, `Ids`, `DemoPackages`, `LifeOsLog` helpers

---

## Step 1 — `ActionExecutor`

### Execution model

One `execute` call is **one atomic state mutation followed by side effects**, in that order. Do not call `store.mutate` once per action: 15 sequential DataStore writes during a goal expansion produce visible UI flicker and 15 disk writes.

```
1. read current = store.state.value
2. fold every action into a working copy, collecting AppliedChange / SkippedAction
3. store.mutate { working }              // exactly one write
4. replay the collected side effects against EnforceGateway
5. return ExecuteReport(applied, skipped)
```

Side effects are collected during the fold as a `List<() -> Unit>` and fired after the write, so `:enforce` always observes state that is already committed.

### Defensive rules — non-negotiable

The model will emit malformed actions. A single bad field must never lose the other fourteen good actions in an expansion.

- Wrap each action's handling in `runCatching`. On failure, append `SkippedAction(type, throwable.message)` and continue.
- Blank or missing `title` → skip with reason `"missing title"`.
- Unparseable dates → treat as `null` rather than throwing. `Time.parseIsoOrNull` already returns null.
- `id == null` on any `create_*` → generate with `Ids.new("goal" | "task" | "event" | ...)`.
- Duplicate `create_goal` with an identical case-insensitive trimmed title → **update in place** instead of inserting. The model re-states goals often and a duplicate list looks broken on stage.
- `limitMinutes <= 0` on `set_app_timeout` → skip.
- Unknown enum strings → fall back to the field default and log.

### Package resolution

Every action carrying package names (`set_app_timeout`, `clear_app_timeout`, `focus_set_apps`, `focus_start`, `set_focus_windows`, `network_set_apps`) must pass each entry through resolution before storing:

1. Lowercase-trim and look up `DemoPackages.ALIASES` so `"instagram"` becomes `com.instagram.android`.
2. Call `apps.resolveOrSubstitute(pkg)`. If it returns a different package, log the substitution at `LifeOS/Exec` and store the substitute.
3. If it returns `null` (not installed, no substitute), still store the original — the timeout is harmless and the user may install the app later.
4. Never store a package from `DemoPackages.ALWAYS_ALLOW` into a blacklist or a timeout. Silently drop it. Blocking SystemUI mid-demo is unrecoverable.

This is why the emulator having no Instagram does not break the demo: the model says Instagram, the executor stores YouTube, and the overlay still fires.

### Per-action behaviour

**`create_goal`** — insert or update-by-title. Set `createdAtIso = Time.nowIso()`. Applied chip: `"Goal: <title>"`, kind `GOAL`, refId the goal id. Return the id to the caller path so `AgentTurnResult.expansionGoalId` can be populated.

**`update_goal`** — patch only non-null fields. Skip if the id is unknown.

**`archive_goal`** — set `archived = true`. Do not delete; Today and Goals filter on it.

**`create_task`** — insert. If `goalId` is null but `sourceGoalId` is set, use `sourceGoalId` as `goalId` too, so expansion tasks nest under their goal on the Goals screen.

**`complete_task`** — match by id, else by case-insensitive title contains. Set `done = true`, `completedAtIso`. Award XP (see below). Chip `"Done: <title>"`.

**`create_event`** — insert with `source = EntitySource.AGENT` unless `origin == ActionOrigin.EMAIL`, in which case `EntitySource.EMAIL`.

**`create_habit`** — insert. Normalise `daysOfWeek` to distinct sorted values in 1..7; empty means every day. If `remindMinutesBefore != null`, also queue an `AlarmSpec` side effect at `timeHhmm` minus the offset, labelled `"habit:<habitId>"`, and add it to `state.alarms`. That is how "remind me 15 minutes before gym" becomes a real alarm.

**`complete_habit_today`** — append `Time.todayIso()` to `completedDates` if absent. Award XP.

**`add_schedule_block`** — insert. If both `daysOfWeek` is empty and `dateIso` is null, default `dateIso` to today so the block is visible immediately rather than invisible forever.

**`remember`** — append to `memoryFacts` if not already present (case-insensitive). Cap the list at 40, dropping oldest.

**`set_persona`** — validate through `Personas.byId`.

**`set_alarm`** — resolve trigger time: use `triggerAtEpochMs` if present, else `Time.nextOccurrenceEpochMs(timeHhmm)`. Store in `state.alarms`, then side effect `enforce.scheduleAlarm(spec)`. If `label` is blank, derive one from `timeHhmm`.

**`cancel_alarm`** — match by id or label, remove from state, side effect `enforce.cancelAlarm(id)`.

**`set_app_timeout`** — upsert by `packageName`. If `state.settings.demoStrictTimeouts` is true, store the real `limitMinutes` but note that `:enforce` will clamp for the demo — clamping is `:enforce`'s job, not yours, so state always shows the honest number. Chip `"Timeout: <label> <limit>m"`.

**`clear_app_timeout`** — remove by `packageName`, or remove all sharing a `sourceGoalId`.

**`focus_start`** — set `focus.active = true`, apply `mode` and `packages` if supplied (otherwise keep existing), set `startedAtEpochMs = now`, and `endsAtEpochMs = now + minutes*60_000` when `minutes != null`. Side effect `enforce.startFocus(FocusSession(...))`.

**`focus_stop`** — set `active = false`, clear timestamps, side effect `enforce.stopFocus()`.

**`focus_set_apps`** — replace `mode` and `packages`. If a session is already active, also re-fire `enforce.applyRules(...)` so the change takes effect without a restart.

**`set_focus_windows`** — replace `focus.windows`.

**`network_set_mode`** — set `network.mode`. Side effect: `OFF` → `enforce.stopNetworkGuard()`, otherwise `enforce.startNetworkGuard(rules)`.

**`network_set_apps`** — replace `network.packages`, and if `mode != OFF` re-fire `startNetworkGuard`.

**`promote_email`** — find the candidate, mark `status = PROMOTED`, and create an `Event` from it, honouring the override fields. Chip `"Scheduled: <title>"`, kind `EMAIL`.

**`dismiss_email`** — mark `status = DISMISSED`.

**`revert_expansion`** — remove every `Todo`, `Habit`, `ScheduleBlock`, `AlarmSpec`, and `AppTimeout` whose `sourceGoalId` matches, drop matching `FocusWindow`s, archive the goal, and fire `enforce.cancelAlarm` for each removed alarm. One chip: `"Reverted <n> items"`, kind `REVERT`.

**`award_xp`** — add to `gamification.xp`.

### Always fire `applyRules` at the end

After the state write, if any focus, timeout, or window action was in the batch, build one `EnforcementRules` snapshot from the committed state and call `enforce.applyRules(it)`. Compute `activeGoalLabel` and `activeGoalDeadlineIso` from the nearest-deadline non-archived hard goal — that is what produces the overlay subtitle "Two days left" instead of generic copy.

### XP and streak

Centralise in one private helper so the numbers stay consistent:

- Completing a task: `+10`, plus `+5` when completed before `dueIso`.
- Completing a habit occurrence: `+5`.
- Ending a focus session that ran at least 10 minutes: `+15`.
- Streak: if `lastActiveDateIso` is yesterday, `streakDays + 1`; if it is today, unchanged; otherwise reset to 1. Then set `lastActiveDateIso = Time.todayIso()`.

---

## Step 2 — `ProjectionBuilder`

Produces the compaction-proof context injected into **every** LLM turn. This is the mechanism that makes the agent's memory survive chat summarisation, so it must be complete and small.

Emit compact JSON (not pretty-printed), hard-capped at **4000 characters**, containing:

- `today` (ISO date) and `now` (ISO datetime) — the model cannot compute dates and will invent them otherwise
- `persona`
- `goals` — non-archived only: `id`, `title`, `deadlineIso`, `hardness`, `riskPercent`
- `openTasks` — not done, nearest 15 by due date: `id`, `title`, `dueIso`, `estMinutes`, `goalId`
- `eventsNext7Days`
- `habits` — `id`, `title`, `daysOfWeek`, `timeHhmm`, `doneToday`
- `scheduleBlocks` — recurring plus any dated in the next 7 days
- `alarms` — enabled only: `label`, `timeHhmm`
- `appTimeouts` — `packageName`, `limitMinutes`, `sourceGoalId`
- `focus` — `active`, `mode`, `packages`, `windowCount`
- `network` — `mode`, `packages`
- `pendingEmailCount`
- `memoryFacts` — most recent 12
- `xp`, `streakDays`

Truncation order when over the cap: drop `memoryFacts` tail, then `scheduleBlocks`, then `eventsNext7Days`, then `openTasks` tail. **Never** drop `goals`, `appTimeouts`, `focus`, or `today` — those are the fields the model needs to avoid contradicting reality.

Use `RiskCalculator` internally so the model sees the same risk number the user sees.

---

## Step 3 — `TimelineMerger`

`forDate(state, dateIso)` merges five sources into one time-sorted list:

- **Alarms** — enabled, whose next occurrence falls on `dateIso`. `TimelineKind.ALARM`, subtitle from `label`.
- **Events** — `startIso` date matches. `EVENT`, `hard = hardness == HARD`.
- **Schedule blocks** — recurring when `Time.isoDayOfWeek(dateIso)` is in `daysOfWeek`, or one-off when `dateIso` matches. `BLOCK`, subtitle `"<startHhmm>–<endHhmm>"`.
- **Habits** — `daysOfWeek` contains the day. `HABIT`, `done = completedDates.contains(dateIso)`.
- **Tasks** — not done and `dueIso` date matches; also include overdue tasks when `dateIso` is today, with subtitle `"overdue"`. `TASK`.

Sort by `timeHhmm` ascending; entries without a time sort last under `"--:--"`. `refId` must always be the source entity id so the UI can complete an item by tapping it.

Include a companion `fun todayFor(state)` convenience that calls `forDate(state, Time.todayIso())`.

---

## Step 4 — `RiskCalculator`

Deterministic, no LLM, and it must never divide by zero on stage.

```
deadline = goal.deadlineIso -> null means risk 0
remainingMin  = sum(estMinutes) over open tasks where goalId or sourceGoalId == goal.id
availableMin  = daysUntilDeadline * FOCUS_MINUTES_PER_DAY        // FOCUS_MINUTES_PER_DAY = 240
completion7d  = completedInLast7Days / max(1, completedInLast7Days + overdueOpenTasks)
pressure      = remainingMin / max(1, availableMin)
risk          = clamp(0, 100, round(100 * (0.70 * pressure + 0.30 * (1 - completion7d))))
```

Special cases: deadline already passed with open tasks → `100`. No open tasks → `0`. Soft goals → scale the result by `0.6` so soft goals never read as critical.

Also expose `fun band(percent: Int): RiskBand` returning `ON_TRACK` under 40, `AT_RISK` under 70, `CRITICAL` otherwise. Put `RiskBand` in `:domain`, not `:core` — the UI reads the percent and colours it itself, per the design tokens.

---

## Step 5 — `Compactor`

**This class is the reason the architecture exists. It may only read and write `ChatTranscript`.** If you find yourself typing `LifeStateStore` in this file, stop.

```
ensureWindow():
  t = chat.transcript.value
  if (t.messages.size <= maxMessages) return
  overflow = t.messages.dropLast(maxMessages)
  kept     = t.messages.takeLast(maxMessages)
  summary  = mergeSummary(t.summary, overflow)
  chat.mutate { it.copy(messages = kept, summary = summary) }
```

`mergeSummary` is extractive and offline — no LLM call. Concatenate the previous summary with one line per overflowed message (`"user: <first 100 chars>"`), then cap the whole summary at 1500 characters by dropping from the front. The summary is a nicety; the projection is what actually carries the facts, which is exactly why a cheap summariser is acceptable here.

---

## Verification

JVM tests only. No emulator, no Gradle Android build, so you are the fastest agent in Wave 1 — use the spare time to make the tests real.

```bash
cd /home/sumit/lifeos
./gradlew :domain:test
```

If Gradle reports `Timeout waiting to lock`, another session is mid-build. Wait 20 seconds and retry; do not delete lock files.

Write a `FakeLifeStateStore` (a `MutableStateFlow` plus a `Mutex`), a `RecordingEnforceGateway` (appends each call to a list), and a `FakeAppCatalog` (returns `DemoPackages.SUBSTITUTES` behaviour) in `domain/src/test/kotlin/`. These fakes are yours; S9 will not need them.

Required tests:

- [ ] `create_goal` twice with the same title yields one goal, not two
- [ ] The full Google-interview expansion (goal + 2 habits + 2 blocks + 2 timeouts + focus windows + alarm + remember) produces **exactly one** `store.mutate` call
- [ ] Every created entity in that expansion carries `sourceGoalId`
- [ ] `revert_expansion` on that goal leaves zero tasks, habits, blocks, alarms, and timeouts referencing it, and fires one `cancelAlarm` per removed alarm
- [ ] An action with `type` present but a missing required field lands in `skipped` while its siblings land in `applied`
- [ ] `set_app_timeout` with alias `"instagram"` stores the substituted package on a catalog reporting Instagram absent
- [ ] `set_app_timeout` targeting `com.android.systemui` is dropped
- [ ] `focus_start` fires `startFocus` **after** the state write (assert the gateway saw `active = true`)
- [ ] `Compactor.ensureWindow` on 40 messages leaves 12 messages, a non-empty summary, **and an unchanged `CanonicalLifeState`** — assert the life-state flow never emitted
- [ ] `ProjectionBuilder.build` on a large state stays under 4000 chars and still contains every goal and every `appTimeout`
- [ ] `RiskCalculator` returns 100 for a passed deadline with open tasks, and 0 for a goal with no open tasks
- [ ] `TimelineMerger.forDate` returns items sorted by time with untimed items last

## Timebox

85 minutes. If behind at 70 minutes, cut in this order: `set_focus_windows` handling (store it, skip the enforcement wiring), then the extractive summary (truncate instead of merge), then the XP before-due bonus. **Never** cut: the single-mutate guarantee, `sourceGoalId` propagation, package substitution, or `ALWAYS_ALLOW` filtering.

## Handoff notes for S9

In your final message, list the exact constructor call S9 needs for `AppContainer`:

```kotlin
val executor = ActionExecutor(lifeState, enforce, apps)
val projection = ProjectionBuilder()
val timeline = TimelineMerger()
val risk = RiskCalculator()
val compactor = Compactor(chat, maxMessages = 12)
```

Also state whether any `ExecuteReport` chip label format changed from the spec above, since S6 renders those strings.
