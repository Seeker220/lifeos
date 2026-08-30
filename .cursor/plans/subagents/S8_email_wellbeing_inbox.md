---
title: "S8 — :email module plus :ui Wellbeing and Inbox"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: "optional — regex classifier is the P0 path"
timebox: "85 minutes"
---

# S8 — `:email`, Wellbeing, Inbox

> Two features of very different value. **Wellbeing is P0** — it is where enforcement becomes visible and controllable, and it is a beat in the demo script. **Email is third on the cut list.** Build Wellbeing first; if the sprint tightens, ship a seed-only Inbox or none at all.

Design reference: [`../lifeos_ui_technical_implementation.md`](../lifeos_ui_technical_implementation.md) §2.4, §2.5. Architecture: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

The Wellbeing screen (focus mode, per-app daily caps with goal attribution, app picker, network guard), plus the `:email` module and Inbox screen that turn a seeded mailbox into confirmed calendar events.

## AI credentials

**Optional, and unused in P0.** The classifier is regex plus date parsing. Do **not** wire `LlmClient` into `:email` — that would make the email beat depend on Azure being reachable, and only `:agent` is permitted to call a model.

---

## Files you own

- `email/src/main/kotlin/com/lifeos/email/**` — `SeedMailbox`, `SeedMailboxSync`, `EmailClassifier`, and optionally `ImapMailboxSync`
- `email/src/test/kotlin/com/lifeos/email/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/wellbeing/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/inbox/**`

## Files you must NOT touch

- `ui/.../components/**` — S5's. Call them; do not edit them.
- `ui/.../theme/**`, `ui/.../nav/**` — S0's.
- `ui/.../screens/chat/**` (S6), `.../today|goals|more/**` (S7)
- `core/**` (frozen), `domain/**`, `agent/**`, `enforce/**`, `app/**`, any `build.gradle.kts`

S0 published two fixed entry points:

```kotlin
@Composable fun WellbeingScreen(onNavigate: (LifeOsDestination) -> Unit)
@Composable fun InboxScreen(onNavigate: (LifeOsDestination) -> Unit)
```

---

## Contracts you implement

```kotlin
class SeedMailboxSync : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>>
}

class EmailClassifier : EmailClassifierPort {
    override suspend fun classify(messages: List<RawMessage>): List<EmailCandidate>
}
```

`:email` is a **pure JVM module**. The seed mailbox is a Kotlin `const` string, not an Android asset — that is deliberate, so your classifier is unit-testable in milliseconds without an emulator.

## Contracts you consume

Via `UiPorts.value`:

```kotlin
lifeState: LifeStateStore
executor: ActionExecutorPort
enforce: EnforceGateway       // usageTodayMinutes(packages): Map<String, Int>
system: SystemAccess          // permissions()
apps: AppCatalog              // launchableApps(), resolveOrSubstitute()
mailbox: MailboxSync
classifier: EmailClassifierPort
```

S5's components: `SectionHeader`, `EmptyState`, `TimeoutBar`, `AppToggleRow`.

---

## Step 1 — `WellbeingScreen` (45 minutes, do this first)

### Section A — Focus session

- A status row: a filled dot plus **"Active"** in primary when `state.focus.active`, otherwise **"Off"** in `onSurfaceVariant`. When active and `endsAtEpochMs` is set, append a live countdown — `"Active · 34m left"` — recomputed with a `LaunchedEffect` ticking every 30 seconds. A visibly counting timer sells "this is really running" better than any label.
- A segmented control, `SingleChoiceSegmentedButtonRow`, for **Whitelist** / **Blacklist**, dispatching `Action.FocusSetApps(mode, currentPackages)`.
- A **Start** / **Stop** button dispatching `Action.FocusStart(mode, packages, minutes = 50)` or `Action.FocusStop`.
- If `!system.permissions().enforcementReady`, replace the Start button with a warning row — *"Usage access and overlay required"* — and an action navigating to `MORE`. Never let the user start a session that cannot enforce anything; that failure looks like success and it is the worst thing that can happen live.

### Section B — App daily caps

The section that makes goal-driven enforcement legible, and the one a judge will point at.

For each `AppTimeout` in `state.appTimeouts`, render `TimeoutBar`:

- `label` — a friendly app name. Resolve it once into a `remember`ed map by calling `apps.launchableApps()` in a `LaunchedEffect` and indexing by package. Fall back to the last dot-segment of the package id. **Never call `PackageManager` from a composable.**
- `usedMinutes` — from `enforce.usageTodayMinutes(packages)`, refreshed in a `LaunchedEffect` every 15 seconds. Do not poll faster; this crosses into `UsageStatsManager` and the numbers barely move.
- `limitMinutes` — the honest stored value. S3 clamps to one minute internally when `demoStrictTimeouts` is on, and **you should still display the real 30**. The mismatch is intentional: the screen tells the truth about the user's rule while the demo gets a fast overlay.
- `sourceLabel` — `"From: <goal title>"` looked up from `state.goals` by `sourceGoalId`, or `null`.

Each bar gets a trailing overflow menu with **Edit limit** (a dialog with a slider over 5–120 minutes, dispatching `Action.SetAppTimeout(pkg, minutes, existingSourceGoalId)`) and **Remove** (dispatching `Action.ClearAppTimeout(packageName = pkg)`).

Preserve `sourceGoalId` when editing. Dropping it silently breaks `RevertExpansion` and the attribution line.

Below the list, an **"Add cap"** button opening the app picker in cap-selection mode.

### Section C — Apps

`AppToggleRow` for each app from `apps.launchableApps()`, loaded once in a `LaunchedEffect` into a `remember`ed list. The switch means "in the current focus package list", so its semantics flip with the mode: in **Whitelist** mode checked means allowed, in **Blacklist** mode checked means blocked. Put that in the section header so it is unambiguous — `SectionHeader("Apps — checked apps are blocked")` versus `"Apps — checked apps are allowed"`.

Toggling dispatches `Action.FocusSetApps(currentMode, updatedPackages)`.

Filter out anything in `DemoPackages.ALWAYS_ALLOW` — those can never be blocked, so showing a switch for them is a lie. Add a search `TextField` above the list; on a real device this list is 100+ entries and scrolling to Instagram on stage is dead air.

### Section D — Network guard

- A three-way segmented control **Off** / **Blacklist** / **Whitelist**, dispatching `Action.NetworkSetMode(mode)`.
- A short explainer: *"Blocks network for selected apps using an on-device VPN. Nothing leaves your phone."*
- When `system.permissions().vpnConsented` is false, show a **"Grant VPN permission"** button. Call `VpnService.prepare(context)` and launch the returned `Intent` with `rememberLauncherForActivityResult`. Re-read permissions in the result callback.
- The apps used are `state.network.packages`; add a **"Same as focus list"** button dispatching `Action.NetworkSetApps(state.focus.packages)`. That is the realistic action and it saves a second app picker.
- If S4 cut the VPN, this section still functions as a state editor and the gateway call is a logged no-op. Do not add a "not implemented" message — leave it looking correct.

---

## Step 2 — `:email` module (25 minutes)

### `SeedMailbox`

A `const val SEED_JSON: String` holding six to eight realistic messages. Mix genuine signal and genuine noise, because a classifier that promotes everything is not a classifier:

1. `courses@uni.edu` — *"OS Midterm — Friday 14:00, LHC-3"* → EXAM, high confidence
2. `noreply@classroom.google.com` — *"DSA Assignment 4 due Tuesday 11:59pm"* → DEADLINE, high confidence
3. `placement@uni.edu` — *"Google campus drive registration closes in 3 days"* → EVENT, medium
4. `piazza@piazza.com` — *"12 new posts in CS3010"* → NOISE
5. `promotions@somestore.com` — *"70% off everything!"* → NOISE
6. `prof.rao@uni.edu` — *"Lab report resubmission by 5th, come to office hours"* → DEADLINE, medium
7. `no-reply@github.com` — *"Your build passed"* → NOISE

Dates must be **relative**, generated at fetch time with `Time.plusDaysIso`. Hardcoded dates mean the demo shows a midterm that already happened.

### `SeedMailboxSync`

Parses `SEED_JSON` with the `:core` `Json` instance, substitutes relative dates, returns `Result.success`. Add a 400 ms `delay` so the Inbox's loading spinner is visible — the sync should look like work, and instantaneous fake data reads as fake.

### `EmailClassifier`

Regex and keyword scoring only. No LLM.

Score per message: subject and body keywords for `exam|midterm|quiz|viva|test` → EXAM; `due|deadline|submit|submission|resubmission` → DEADLINE; `registration|drive|talk|seminar|workshop|closes` → EVENT. Sender-domain boosts for `.edu`, `classroom.google.com`, `placement`. Penalties for `promotions|noreply@.*store|unsubscribe|% off`.

Date extraction, in priority order:

1. Explicit `dd/mm` or `dd-mm` or `yyyy-mm-dd`
2. `"<Weekday> <HH:mm>"` → next occurrence of that weekday
3. Bare weekday name → next occurrence, time defaulted to `09:00`
4. `"in N days"` → today plus N
5. `"tomorrow"` / `"tonight"`

Confidence: normalise the score to 0.0–1.0. Classify below 0.35 as `NOISE`. Emit a candidate for **every** message including noise, with `kind = NOISE` — the Inbox collapses those into a "Noise" group, which demonstrates judgement rather than credulity.

`proposedTitle` should be a cleaned subject: strip `Re:`, `Fwd:`, and trailing course codes. `proposedStartIso` is the extracted date or `null`.

Wrap the whole classify in `runCatching` per message so one unparseable body does not lose the batch.

---

## Step 3 — `InboxScreen` (15 minutes)

### Header

An account strip showing `"Seed mailbox"` (or the IMAP address if you got that far), plus two buttons: **Sync** calling fetch-then-classify, and **Load sample** which does the same thing but is honestly labelled for the demo.

### Sync flow

In the ViewModel:

```
1. loading = true
2. msgs = ports.mailbox.fetch(null).getOrElse { emptyList() }
3. candidates = ports.classifier.classify(msgs)
4. merge into state, skipping candidates whose messageId already exists
5. loading = false
```

Merging is a state write with no enforcement consequence and there is no action type for "add candidates", so write it through `lifeState.mutate`. Promotion and dismissal, which *do* create real calendar entities, go through the executor. That split is the rule: reversible inbox bookkeeping is a direct mutate, anything that creates enforced state is an `Action`.

### Candidate list

Group into **Needs decision** (`PENDING`, non-noise, sorted by confidence descending), then a collapsed **Noise** group, then a collapsed **Handled** group (`PROMOTED` or `DISMISSED`).

Each pending card shows:

- Subject in `bodyLarge`
- `"from: <sender>"` and the confidence as a percentage in `bodySmall`
- The proposed schedule line: `"Proposed: Fri 14:00"`, or *"No date found — tap Edit"* when null
- A kind chip: EXAM in danger, DEADLINE in warn, EVENT in primary
- Two buttons: **Add to schedule** dispatching `Action.PromoteEmail(candidateId)`, and **Dismiss** dispatching `Action.DismissEmail(candidateId)`
- An **Edit** text button opening a dialog for title and date, then promoting with the override fields

On promote, show a snackbar — *"Added to Today"* — with a **View** action navigating to `TODAY`. That is the beat that closes the email loop in the demo.

### Empty state

`EmptyState("Inbox is empty.", "Load the sample mailbox to see how LifeOS triages email.", actionLabel = "Load sample")`.

### IMAP — only if everything above is done and tested

If you genuinely have 15 spare minutes, add `ImapMailboxSync` using `javax.mail`… **you cannot**, because adding a dependency is forbidden mid-sprint and would stall seven other sessions. So real IMAP is out of scope for this session. Leave `ImapMailboxSync` as S0's stub returning `Result.failure(NotImplementedError())` and spend the time hardening the classifier's date extraction instead. That is the better trade: date extraction is what the demo shows.

---

## Verification

```bash
cd /home/sumit/lifeos
./gradlew :email:test
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Cap yourself at two or three Android builds; seven other sessions share the lock. `:email:test` is pure JVM and cheap, so iterate there freely. On `Timeout waiting to lock`, wait 20 seconds and retry.

Acceptance checklist — Wellbeing (P0):

- [ ] Focus status reflects `state.focus.active` and the countdown ticks down
- [ ] The mode segmented control persists across a tab switch and an app restart
- [ ] Start Focus is replaced by a permission warning when usage access or overlay is missing
- [ ] Caps set by a chat goal expansion appear with friendly app names and `"From: Crack Google interview"`
- [ ] `usedMinutes` is non-zero after actually using a capped app for a minute, and refreshes without leaving the screen
- [ ] A 30-minute cap still displays as 30 while `demoStrictTimeouts` is on
- [ ] Edit limit preserves `sourceGoalId` — verify by undoing the expansion afterwards and confirming the cap disappears
- [ ] Remove cap deletes it and the overlay stops firing for that app
- [ ] The app list loads, is searchable, and excludes `ALWAYS_ALLOW` packages
- [ ] Toggling an app updates the focus list, and the header text correctly describes the current mode's semantics
- [ ] The network segmented control persists, and the VPN grant button launches the system consent dialog

Acceptance checklist — Email (cuttable):

- [ ] `:email:test` passes with the classifier correctly labelling all seven seed messages
- [ ] Extracted dates are relative to today, never hardcoded
- [ ] Sync shows a spinner, then populates Needs decision, Noise, and Handled groups
- [ ] The two promotable messages are ranked above the noise
- [ ] Add to schedule creates an `Event` visible on Today at the proposed time
- [ ] Dismiss moves the candidate to Handled and it does not reappear after another Sync
- [ ] Edit-then-promote honours both overrides
- [ ] Syncing twice does not duplicate candidates
- [ ] An unparseable body produces a NOISE candidate rather than losing the batch

## Timebox

85 minutes: Wellbeing 45, `:email` 25, Inbox 15.

**Sequencing is not optional.** If you are at 50 minutes with Wellbeing incomplete, abandon email entirely — leave S0's stubs in place and tell S9 to remove the Inbox tab from the nav bar. A four-tab app that enforces things beats a five-tab app with a broken inbox.

If behind at 70 minutes, cut in this order: the Noise group (show pending only), then Edit-then-promote, then the app search field, then the focus countdown. **Never** cut: the caps section with goal attribution, or the enforcement-ready guard on Start Focus.

## Handoff notes for S9

Report the exact constructor calls:

```kotlin
val mailbox = SeedMailboxSync()
val classifier = EmailClassifier()
```

State clearly whether the Inbox shipped. If it did not, S9 must drop the `inbox` destination from S0's nav bar so no judge taps an empty tab. Also confirm whether `AppToggleRow` and `TimeoutBar` needed anything S5 did not deliver.
