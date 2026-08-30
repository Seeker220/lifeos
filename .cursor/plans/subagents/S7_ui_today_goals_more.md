---
title: "S7 — :ui Today, Goals, More"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "85 minutes"
---

# S7 — `:ui` Today, Goals, More

> These three screens are the **proof** that the chat did something real. A judge who sees a reply bubble thinks "chatbot"; a judge who sees the reply bubble and then a filled calendar and a risk percentage thinks "system". You also own the More screen, which contains the single most differentiating demo beat: pressing "Compact chat" and showing the goal count did not move.

Design reference: [`../lifeos_ui_technical_implementation.md`](../lifeos_ui_technical_implementation.md) §2.2, §2.3, §2.6. Architecture: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Three screens with one ViewModel each, all reading the same `StateFlow<CanonicalLifeState>` and all writing through `ActionExecutorPort` so a tap and a sentence travel identical code paths.

## AI credentials

**None.**

---

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/today/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/goals/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/more/**`

## Files you must NOT touch

- `ui/.../components/**` — S5's. Call them; do not edit them.
- `ui/.../theme/**`, `ui/.../nav/**` — S0's.
- `ui/.../screens/chat/**` (S6), `.../wellbeing|inbox/**` (S8)
- `core/**` (frozen), `domain/**`, `agent/**`, `enforce/**`, `email/**`, `app/**`, any `build.gradle.kts`

S0 published three fixed entry points:

```kotlin
@Composable fun TodayScreen(onNavigate: (LifeOsDestination) -> Unit)
@Composable fun GoalsScreen(onNavigate: (LifeOsDestination) -> Unit)
@Composable fun MoreScreen(onNavigate: (LifeOsDestination) -> Unit)
```

---

## Contracts you consume

All via `UiPorts.value`:

```kotlin
lifeState: LifeStateStore     // state: StateFlow<CanonicalLifeState>
chat: ChatStore               // transcript: StateFlow<ChatTranscript>
executor: ActionExecutorPort
timeline: TimelinePort        // forDate(state, dateIso): List<TimelineItem>
risk: RiskPort                // riskPercent(state, goalId): Int
compactor: CompactorPort      // ensureWindow()
enforce: EnforceGateway       // startFocus, stopFocus
system: SystemAccess          // permissions()
```

S5's components you will use: `SectionHeader`, `EmptyState`, `RiskBadge`.

While S1 is still working, `TimelinePort` and `RiskPort` are S0's stubs returning empty and zero. **Build the empty states first** — they are what a judge sees on a fresh install, and they are the only thing that renders until S1 lands.

---

## Step 1 — `TodayScreen` (30 minutes)

### Header

The date in `titleLarge` — `"Sunday 30 Aug"` — with a subtle "Today" label above it. Add left and right chevrons stepping `selectedDate` by one day, held in `remember`. This gives you a poor man's calendar for nearly no cost and lets the presenter show tomorrow's study block.

### Timeline

Call `timeline.forDate(state, selectedDate)` and render each `TimelineItem` as a row:

- **Left gutter**, 56.dp wide: `timeHhmm` in `labelLarge`, tabular. Items with no time show `"--:--"` in `onSurfaceVariant`.
- **Kind indicator**: a 2.dp vertical rule coloured by kind — `ALARM` warn, `EVENT` danger when `hard` else primary, `BLOCK` primary, `HABIT` primary at 60% alpha, `TASK` `onSurfaceVariant`.
- **Title** in `bodyLarge`, struck through when `done`.
- **Subtitle** in `bodySmall` at 70% alpha.
- **Trailing checkbox** for `HABIT` and `TASK` kinds only. Alarms, events, and blocks are not completable.

Checkbox dispatch, through the executor so XP and streak update exactly as they would from chat:

- `TASK` → `Action.CompleteTask(id = item.refId)`
- `HABIT` → `Action.CompleteHabitToday(id = item.refId)`

Group the list under `SectionHeader`s: **Morning** before 12:00, **Afternoon** 12:00 to 17:00, **Evening** after 17:00, and **Anytime** for untimed items. Skip empty groups.

### Start Focus FAB

An extended FAB reading **"Start Focus"**, or **"Stop Focus"** when `state.focus.active`.

- Start → `Action.FocusStart(mode = null, packages = null, minutes = 50)`. Passing nulls keeps whatever mode and package list the Wellbeing screen or a chat expansion already configured — this screen must not silently overwrite the user's rules.
- Stop → `Action.FocusStop`.
- Before starting, check `system.permissions().enforcementReady`. If false, show a snackbar — *"Grant usage access and overlay first"* — with an action navigating to `MORE`. Starting a focus session that cannot enforce anything is the worst possible demo failure, because it looks like it worked.

### Empty state

`EmptyState("Nothing scheduled.", "Ask LifeOS for a goal and it will fill this in.", actionLabel = "Open chat")` navigating to `CHAT`.

---

## Step 2 — `GoalsScreen` (30 minutes)

Two sections: **Goals** then **Todos**.

### Goal cards

One card per non-archived goal, expandable via `remember { mutableStateSetOf<String>() }`:

**Collapsed:**

- Title in `titleMedium`
- A metadata row: deadline formatted as `"Due 30 Sep"`, a `HARD`/`SOFT` chip, and `RiskBadge(risk.riskPercent(state, goal.id))`
- When the goal has `appTimeouts` referencing it via `sourceGoalId`, a line in `labelSmall` primary: `"Caps: Instagram 30m · YouTube 45m"`. Resolve package names to friendly labels using `DemoPackages.ALIASES` inverted, falling back to the last dot-segment of the package. **Do not** call `PackageManager` from a composable.
- A trailing count: `"3 open"`

**Expanded**, additionally:

- Nested task rows with checkboxes, filtered by `goalId == goal.id || sourceGoalId == goal.id`
- Habits belonging to the goal, listed with their days and time
- A `TextButton` **"Undo expansion"** dispatching `Action.RevertExpansion(goal.id)`, shown only when at least one entity carries this `sourceGoalId`

That last button matters: it is the honest answer to "what if the AI schedules something stupid", and it is a question you will be asked.

### Todos section

Every task not attached to a goal, plus overdue tasks from any goal, with checkboxes. Sort: overdue first, then by `dueIso` ascending, then untimed. Show `"overdue"` in danger colour as the subtitle for anything past due.

Add a small `+` in the section header opening an `AlertDialog` with a single text field to create a task via `Action.CreateTask(title = ...)`. Manual entry is a P1 nicety but it costs ten minutes and it answers "can I use this without the AI".

### Empty state

`EmptyState("No goals yet.", "Tell LifeOS what you're trying to do.", actionLabel = "Open chat")`.

---

## Step 3 — `MoreScreen` (25 minutes)

A settings-style list. Sections in this order:

### 1. Gamification strip

`state.gamification.xp` XP and `streakDays` day streak as two large stat cells. Cheap, and it fills the top of an otherwise dull screen.

### 2. Life state integrity — **the differentiating demo beat**

This is the visible proof of the compaction-proof architecture, so build it carefully.

A card headed "Life state" showing live counts read from `state`: goals, tasks, events, habits, blocks, alarms, timeouts, memory facts. Below it, chat counts read from `chat.transcript`: messages and summary length.

Then a button **"Compact chat"** calling `compactor.ensureWindow()`.

The point is what does *not* change. Before compaction, note the counts; after, the chat message count drops and every life-state count is identical. Make that legible: capture the counts into `remember` when the button is pressed and show a result line — *"Chat 40 → 12 messages. Life state unchanged."* Compute "unchanged" by actually comparing the before and after snapshots rather than hardcoding the word, because if the comparison ever fails you want to find out here rather than on stage.

### 3. Persona

Three `FilterChip`s from `Personas.ALL`, dispatching `Action.SetPersona(id)`.

### 4. Memory facts

The `memoryFacts` list, newest first, each in a small surface row. This is "persistent life memory" made visible, and it takes five minutes.

### 5. Permissions

A summary row per permission from `system.permissions()` with a granted/missing indicator, and a **"Review permissions"** button navigating to onboarding. Re-read on `ON_RESUME` with the same `DisposableEffect` pattern S5 uses, because the user may have changed a grant in Settings.

### 6. Demo controls

Three items, all worth their tiny cost during a live pitch:

- **"Strict demo timeouts"** `Switch` writing `settings.demoStrictTimeouts` via `lifeState.mutate`. S3's `TimeoutMonitor` clamps every cap to one minute when this is on, so the overlay fires in 60 seconds instead of 30 minutes. Subtitle: *"Clamps every app cap to 1 minute so blocking is demoable."*
- **"Test alarm in 60s"** dispatching `Action.SetAlarm(label = "demo", timeHhmm = "", triggerAtEpochMs = now + 60_000, personaLine = "Time's up. Back to work.")`.
- **"Reset demo data"** with a confirmation dialog, clearing state via `lifeState.mutate { CanonicalLifeState() }` and the transcript via `chat.mutate { ChatTranscript() }`.

Use `lifeState.mutate` directly for the two settings toggles and the reset. Those are UI-local preferences with no enforcement side effect and there is no action type for them. Everything with a consequence — alarms, focus, timeouts, tasks — goes through the executor.

### 7. About

Version, and one line: *"LifeOS — plans that enforce themselves."*

---

## ViewModels

One per screen, plain `androidx.lifecycle.ViewModel`, constructed with `viewModel { XViewModel(UiPorts.value) }`. Each exposes a single `StateFlow<XUiState>` mapped from the ports' flows, and intent methods that `viewModelScope.launch` an executor call. Keep them thin — all real logic lives in `:domain`.

`TodayUiState` needs `selectedDate` held in the ViewModel rather than the composable, so the date survives tab switches.

---

## Verification

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity
```

Cap yourself at two or three Gradle builds; seven other sessions share the lock. On `Timeout waiting to lock`, wait 20 seconds and retry.

Acceptance checklist:

- [ ] All three screens render without crashing while `TimelinePort` and `RiskPort` are still stubs
- [ ] All three empty states appear on a freshly cleared install and their action buttons navigate to Chat
- [ ] Once S1 lands, the interview expansion fills Today with study blocks and habits at the right times
- [ ] Date chevrons move the timeline and recurring blocks appear on the correct weekdays
- [ ] Timeline items are grouped into Morning / Afternoon / Evening / Anytime with empty groups hidden
- [ ] Checking a task strikes it through, and XP on More increases
- [ ] Checking a habit marks it done for today only — step to tomorrow and it is unchecked
- [ ] Goal cards show a risk badge whose colour matches its band, and expanding shows nested tasks
- [ ] A goal with caps shows the `"Caps: ..."` line with friendly app names, not raw package ids
- [ ] "Undo expansion" removes the created tasks, habits, blocks, alarms, and timeouts, and the Wellbeing screen's caps disappear too
- [ ] The Start Focus FAB toggles to Stop Focus, and with permissions missing it shows the snackbar instead of pretending to work
- [ ] **"Compact chat" reduces the message count and every life-state count is provably identical**
- [ ] "Test alarm in 60s" produces a firing alarm
- [ ] "Strict demo timeouts" makes an overlay fire after roughly a minute of using a capped app
- [ ] "Reset demo data" clears everything and every screen returns to its empty state
- [ ] Manual `+` task creation works and the new task appears in Todos
- [ ] Rotating the device preserves the selected date and expanded goal set

## Timebox

85 minutes: Today 30, Goals 30, More 25.

If behind at 65 minutes, cut in this order: the date chevrons (today only), then manual `+` task creation, then the memory-facts list, then goal card expansion (keep the collapsed card with its risk badge). **Never** cut: the timeline itself, the risk badge, the Compact chat proof, or the two demo controls. The compaction proof and the strict-timeout switch are load-bearing for the demo script.

## Handoff notes for S9

Confirm the exact `Action` constructors you used for the demo alarm and the focus start, since S9's seed routine reuses them. Report whether the compaction comparison ever showed a life-state change — if it did, that is a `:domain` bug S9 must fix before the run.
