---
title: LifeOS UI + Technical Implementation Plan
description: Screen-by-screen UI, app modules, feature-to-code mapping, and ordered implementation plan for the codebase
---

# LifeOS — UI Interface & Technical Implementation Plan

Companion to [`lifeos_complete_hackathon_plan.md`](lifeos_complete_hackathon_plan.md).  
This document is the **build blueprint**: how the UI looks and behaves, how the Android project is modularized, and the exact implementation order to land P0 in the codebase.

**Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, DataStore + kotlinx.serialization, Coroutines/Flow, Gemini Flash HTTP, single `:app` module (packages as logical modules — no multi-module Gradle split for hackathon speed).

---

## 1. UI design system

### 1.1 Visual direction

- **Brand:** “LifeOS” is a hero-level wordmark on onboarding and Chat empty state — not a tiny nav label.
- **Theme:** Material 3 **dark-first** productivity shell (OLED-friendly near-black surfaces). Accent: **electric teal** (`#2EE6A6`) on charcoal (`#0E1116`), secondary amber for risk/warnings (`#F5A524`). Avoid purple-glow / cream-serif AI clichés.
- **Typography:** `Display` = Space Grotesk (or similar via downloadable font); `Body` = IBM Plex Sans. Fallback: `FontFamily.SansSerif` if font fetch costs time.
- **Motion (2–3 intentional):**
  1. Chat action chips slide/fade in when executor applies actions.
  2. Focus/timeout overlay enters with short scale+fade.
  3. Tab content crossfade (Navigation Compose default).
- **Cards:** Prefer flat list rows with hairline dividers. Cards only where interaction needs a clear hit target (goal expandable, email candidate confirm).

### 1.2 Design tokens (`ui/theme/`)

| Token | Value |
| --- | --- |
| `md_bg` | `#0E1116` |
| `md_surface` | `#161B22` |
| `md_primary` | `#2EE6A6` |
| `md_on_primary` | `#04140F` |
| `md_danger` | `#FF5C5C` |
| `md_warn` | `#F5A524` |
| `radius_sm` | 8dp |
| `space` | 4 / 8 / 16 / 24 |

### 1.3 Navigation shell

```text
MainActivity
 └─ LifeOsApp
     ├─ OnboardingNav (first launch / missing grants)
     └─ MainScaffold
         ├─ TopBar (context title + persona chip)
         ├─ NavHost (6 tabs)
         └─ NavigationBar
```

| Route | Label | Icon (Material) |
| --- | --- | --- |
| `chat` | Chat | `Icons.Outlined.AutoAwesome` |
| `today` | Today | `Icons.Outlined.CalendarToday` |
| `goals` | Goals | `Icons.Outlined.Flag` |
| `inbox` | Inbox | `Icons.Outlined.Mail` |
| `wellbeing` | Focus | `Icons.Outlined.Shield` |
| `more` | More | `Icons.Outlined.MoreHoriz` |

Start destination: **`chat`**.

---

## 2. Screen-by-screen UI

### 2.0 Onboarding (`feature/onboarding`)

**Purpose:** Unlock enforcement permissions before Focus/timeouts/VPN.

**Layout (single scroll):**

1. Hero: **LifeOS** + one line: “Plans that enforce themselves.”
2. Personality picker (3 segmented chips): Supportive / Strict / Coach.
3. Permission rows (status leading icon + Grant button):
   - Notifications
   - Exact alarms
   - Usage access
   - Display over other apps
   - Network guard (VPN prepare)
4. Primary CTA: **Continue** (enabled when notifications + usage + overlay granted; VPN optional but warned).

**States:** `missing` / `granted` per row; re-check on `ON_RESUME`.

---

### 2.1 Chat — agent home (`feature/chat`)

```text
┌─────────────────────────────┐
│ LifeOS          [Strict ▾]  │
├─────────────────────────────┤
│                             │
│  (empty) “Tell me a goal.”  │
│   chips: Google interview / │
│   Focus whitelist / Email   │
│                             │
│  ┌ user bubble ──────────┐  │
│  └───────────────────────┘  │
│  ┌ agent bubble ─────────┐  │
│  │ reply text            │  │
│  │ [Applied: 6 actions]  │  │
│  │ Goal · Timeout · …    │  │
│  │ [Undo expansion]      │  │
│  └───────────────────────┘  │
├─────────────────────────────┤
│ [ Ask LifeOS…        ][➤]   │
└─────────────────────────────┘
```

**Behaviors:**

- Sending shows typing indicator; response always `{reply, actions}` applied via executor before showing chips.
- Action chips navigate deep-link style: Timeout chip → Wellbeing; Goal → Goals; Event → Today.
- Soft banner if `emailCandidates.pending > 0`: “2 emails need a decision” → Inbox.
- Offline: same UI; fallback JSON drives demo.

---

### 2.2 Today — day schedule (`feature/today`)

```text
│ Tuesday 30 Aug        [Focus FAB] │
│ 07:00  Alarm · Wake               │
│ 10:00  Event · OS Midterm   HARD  │
│ 19:00  Block · Interview grind    │
│ 19:00  Habit · LeetCode daily [✓] │
│ 22:30  Alarm · Bedtime DSA        │
```

**Behaviors:**

- Merge `events`, `habits` (today’s occurrence), `scheduleBlocks`, `tasks` due today, `alarms` into time-sorted `TimelineItem`.
- Checkbox completes task/habit → XP.
- FAB **Start Focus** uses current Wellbeing mode/packages.
- Empty: “No schedule — ask Chat to add a goal.”

---

### 2.3 Goals — goals + todos (`feature/goals`)

```text
│ Goals                             │
│ ┌ Crack Google interview ─────┐   │
│ │ Due 30 Sep · HARD · Risk 41%│   │
│ │ Timeouts: IG 30m · YT 45m   │   │
│ │ ▸ 3 open tasks              │   │
│ └─────────────────────────────┘   │
│ Todos                             │
│ ☐ Graph practice · tonight        │
│ ☑ System design notes             │
```

**Behaviors:**

- Expand goal → nested tasks with `sourceGoalId`.
- Risk color: green &lt;40, amber &lt;70, red otherwise.
- Manual `+` for task (P1); Chat remains primary.

---

### 2.4 Inbox — email review (`feature/inbox`)

```text
│ Inbox          [Load sample] [Sync]│
│ Needs decision (2)                 │
│ ┌ OS Midterm — Fri 14:00 ─────┐   │
│ │ from: courses@uni.edu  0.91 │   │
│ │ [Add to schedule] [Dismiss] │   │
│ └─────────────────────────────┘   │
│ Handled / Noise (collapsed)        │
```

**Behaviors:**

- Add to schedule → `promote_email` → event on Today + snackbar.
- Dismiss → status dismissed.
- Account strip: Seed / IMAP status.

---

### 2.5 Wellbeing / Focus (`feature/wellbeing`)

```text
│ Focus session     [● Active] [Stop]│
│ Mode: ( Whitelist | Blacklist )    │
│                                    │
│ App timeouts                       │
│ Instagram  ████░░  22/30m          │
│   From: Google interview           │
│ YouTube    ██░░░░  10/45m          │
│                                    │
│ Apps                               │
│ ☑ Chrome   whitelist               │
│ ☑ Docs                             │
│ ☐ Instagram                        │
│                                    │
│ Network guard  [Off|Black|White]   │
│ [Request VPN permission]           │
```

**Behaviors:**

- Toggles write `FocusRules` / `appTimeouts` / `NetworkRules` (same store Chat writes).
- Progress bars from UsageStats today.
- Demo: “1 min Instagram limit” debug switch in More.

---

### 2.6 More (`feature/more`)

- Persona, permission checklist (re-open onboarding rows)
- Life state counts + **Compact chat** button (proof)
- Email accounts (IMAP form P0-stretch)
- Auto-schedule toggle
- Demo reset / Demo strict timeouts
- XP + streak

### 2.7 System UI (non-Compose)

| Surface | Tech | Copy |
| --- | --- | --- |
| Block / timeout overlay | XML `FrameLayout` via WindowManager | Title + goal-aware subtitle + Back to work / Override |
| Alarm full-screen | `AlarmActivity` Compose or XML | Persona line + Dismiss; TTS on start |
| FGS notifications | Notification channels `focus`, `vpn`, `alarm` | “Focus active”, “Network guard on” |

---

## 3. Project structure (logical modules)

Single Gradle module `:app`. Packages = modules:

```text
app/src/main/java/com/lifeos/app/
├── LifeOsApplication.kt
├── MainActivity.kt
├── di/                         # manual simple ServiceLocator (no Hilt — save time)
│   └── AppContainer.kt
├── data/
│   ├── model/                  # CanonicalLifeState, ChatTranscript, entities
│   ├── repo/
│   │   ├── LifeStateRepository.kt
│   │   └── ChatRepository.kt
│   ├── local/
│   │   ├── LifeStateDataStore.kt
│   │   ├── ChatDataStore.kt
│   │   └── SecretsStore.kt     # encrypted IMAP/OAuth refs
│   └── seed/
│       └── SeedMailbox.json + SeedMailboxSync.kt
├── domain/
│   ├── Action.kt               # sealed actions
│   ├── ActionExecutor.kt       # ONLY writer to life state + OS side effects
│   ├── RiskCalculator.kt
│   ├── TimelineMerger.kt
│   ├── Compactor.kt            # chat only
│   └── ProjectionBuilder.kt    # LifeStateProjection for prompts
├── agent/
│   ├── AgentModels.kt          # AgentResponse DTO
│   ├── GeminiClient.kt
│   ├── OfflineFallbacks.kt
│   ├── SystemPromptBuilder.kt
│   └── AgentController.kt      # sendMessage() orchestration
├── email/
│   ├── MailboxSync.kt          # interface
│   ├── SeedMailboxSync.kt
│   ├── ImapMailboxSync.kt      # stretch
│   ├── GmailMailboxSync.kt     # P1 stub OK
│   └── EmailClassifier.kt
├── enforce/
│   ├── FocusService.kt
│   ├── TimeoutMonitor.kt       # used by FocusService loop
│   ├── OverlayController.kt    # WindowManager XML
│   ├── LifeOsVpnService.kt
│   ├── AlarmScheduler.kt
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   └── AlarmActivity.kt
├── ui/
│   ├── theme/
│   ├── nav/LifeOsNav.kt
│   ├── components/             # ActionChips, RiskBadge, PermissionRow, …
│   └── feature/
│       ├── onboarding/
│       ├── chat/
│       ├── today/
│       ├── goals/
│       ├── inbox/
│       ├── wellbeing/
│       └── more/
└── util/
    ├── PackageHelper.kt        # launcher apps via <queries>
    ├── UsageStatsHelper.kt
    └── PermissionHelper.kt
```

`AndroidManifest.xml`: permissions, FGS, VPN service, receivers, queries, activities.

---

## 4. Feature → module implementation map

| Feature (from product plan) | UI | Domain / Data | OS / Enforce | Agent |
| --- | --- | --- | --- | --- |
| Agentic chat | `feature/chat` | `ChatRepository`, `ActionExecutor` | — | `AgentController`, Gemini, fallbacks |
| Goal expansion | Chat chips + Goals/Today/Wellbeing update | `create_*`, `set_app_timeout`, `sourceGoalId` | timeouts/focus/alarms via executor | Expansion playbook in `SystemPromptBuilder` |
| Canonical life state | All tabs observe Flow | `LifeStateRepository`, DataStore | — | `ProjectionBuilder` each turn |
| Chat compaction | More “Compact chat” | `Compactor` | — | Does not touch life state |
| Goals / todos | `feature/goals` | models + executor | — | actions |
| Today schedule | `feature/today` | `TimelineMerger` | FAB → FocusService | — |
| Habits | Today rows | `Habit` + occurrences | `AlarmScheduler` | `create_habit` |
| App timeouts | Wellbeing bars | `appTimeouts[]` | `TimeoutMonitor` + overlay | `set_app_timeout` |
| Focus session | Wellbeing + overlay | `FocusRules` | `FocusService` | `focus_start/stop/set_apps` |
| VPN network | Wellbeing toggle | `NetworkRules` | `LifeOsVpnService` | `network_*` |
| Email inbox | `feature/inbox` | `email/*`, seed JSON | INTERNET | classify + ask/promote |
| Alarms / TTS | AlarmActivity | `alarms[]` | `AlarmScheduler`, TTS | `set_alarm` |
| Onboarding perms | `feature/onboarding` | `PermissionHelper` | Settings intents + VPN prepare | — |
| XP / streak | Goals/More chips | `gamification` | — | on complete |
| Deadline risk | Goals badge | `RiskCalculator` | — | — |

---

## 5. Core class contracts (implement against these)

### 5.1 `Action` (sealed)

```kotlin
sealed class Action {
  data class CreateGoal(...) : Action()
  data class CreateTask(...) : Action()
  data class CreateHabit(...) : Action()
  data class CreateEvent(...) : Action()
  data class AddScheduleBlock(...) : Action()
  data class SetAlarm(...) : Action()
  data class SetAppTimeout(val packageName: String, val limitMinutes: Int, val sourceGoalId: String?) : Action()
  data class FocusStart(...) : Action()
  data class FocusStop : Action()
  data class FocusSetApps(...) : Action()
  data class SetFocusSchedule(...) : Action()
  data class NetworkMode(...) : Action()
  data class NetworkSetApps(...) : Action()
  data class Remember(val fact: String) : Action()
  data class PromoteEmail(...) : Action()
  data class DismissEmail(...) : Action()
  data class CompleteTask(...) : Action()
  data class RevertExpansion(val goalId: String) : Action()
  // …
}
```

Parse from Gemini JSON with kotlinx.serialization polymorphic `type` discriminator.

### 5.2 `ActionExecutor`

```text
suspend fun execute(actions: List<Action>): ExecuteReport
  → for each action: mutate LifeStateRepository atomically (Mutex)
  → side effects: AlarmScheduler, FocusService start/stop intents, VpnService, overlay rules
  → return list of AppliedChip for Chat UI
```

### 5.3 `AgentController.sendMessage(text)`

```text
1. Compactor.ensureWindow()
2. projection = ProjectionBuilder.from(lifeState)
3. prompt = SystemPromptBuilder.build(persona, projection, chatSummary, lastK, text)
4. raw = GeminiClient.generate(prompt) catch → OfflineFallbacks.match(text)
5. response = parse AgentResponse
6. report = ActionExecutor.execute(response.actions)
7. ChatRepository.append(user, assistant+report)
8. emit UI state
```

### 5.4 `FocusService` loop (pseudocode)

```text
every 800ms:
  fg = UsageStatsHelper.foregroundPackage()
  if focus.active && violates(fg, focus.mode, packages) → Overlay.show(FOCUS)
  else if timeoutExceeded(fg) → Overlay.show(TIMEOUT, goalSubtitle)
  else Overlay.hide()
  also: if now in set_focus_schedule window → ensure focus rules active
```

---

## 6. UI state & ViewModels

Prefer one VM per tab; share repos via `AppContainer`.

| VM | StateFlows | Intents |
| --- | --- | --- |
| `ChatViewModel` | messages, sending, banner | `send`, `undoExpansion`, `tapChip` |
| `TodayViewModel` | timeline, focusActive | `complete`, `startFocus` |
| `GoalsViewModel` | goalsWithTasks, risk | `completeTask` |
| `InboxViewModel` | candidates, accounts | `promote`, `dismiss`, `loadSeed`, `sync` |
| `WellbeingViewModel` | focus, timeouts+usage, network | toggles, `start/stop`, `requestVpn` |
| `MoreViewModel` | counts, persona, perms | `compact`, `reset`, `setPersona` |
| `OnboardingViewModel` | grant statuses | `request*`, `refresh`, `finish` |

All VMs collect `LifeStateRepository.state: StateFlow<CanonicalLifeState>` — single source of truth for UI.

---

## 7. Implementation plan (codebase completion order)

Work in **vertical slices**: each slice leaves the app installable.

### Slice 0 — Skeleton (blockers for everything)

1. Create Android Studio / Gradle Compose project `com.lifeos.app`.
2. Theme + `MainScaffold` + empty 6 tabs + Onboarding placeholder.
3. Manifest: all P0 permissions, `<queries>`, FGS service stubs, VPN service stub, receivers.
4. `AppContainer` + empty repositories.
5. **Exit:** `./gradlew :app:assembleDebug` + `adb install`.

### Slice 1 — Canonical state + executor (no LLM yet)

1. Models + DataStore read/write.
2. `ActionExecutor` for create_goal/task/habit/event/timeout/remember.
3. Unit-style smoke: apply hardcoded Google-interview actions in debug.
4. Goals + Today + Wellbeing **read-only** UI bound to Flow.
5. **Exit:** cold start shows seed empty; debug button applies expansion → UI fills.

### Slice 2 — Chat agent

1. `OfflineFallbacks` for 3 utterances (interview, focus whitelist, email check).
2. `GeminiClient` + schema parse (graceful fail → offline).
3. `SystemPromptBuilder` + `ProjectionBuilder` + `Compactor`.
4. Chat UI end-to-end.
5. **Exit:** type interview goal → Goals/Wellbeing update without API key.

### Slice 3 — Enforcement

1. `PermissionHelper` + Onboarding real grants.
2. `FocusService` + `OverlayController` XML + whitelist/blacklist.
3. `TimeoutMonitor` integrated; demo 1-minute limit.
4. `AlarmScheduler` + `AlarmActivity` + TTS + BootReceiver.
5. **Exit:** overlay on Chrome/YouTube; alarm T+60s from More.

### Slice 4 — Email + VPN

1. Seed mailbox JSON + Inbox UI + classifier (regex + optional Gemini).
2. Promote/dismiss → Events on Today.
3. `LifeOsVpnService` app-level allow/disallow + Wellbeing toggle.
4. **Exit:** sample midterm → schedule; VPN prepare dialog works.

### Slice 5 — Polish + demo

1. Action chips, risk colors, empty states, app icon.
2. Undo expansion, compaction proof on More.
3. Demo script dry-run on device via adb.
4. **Exit:** acceptance checklist in product plan green.

### Parallelization

| Person A | Person B |
| --- | --- |
| Slices 1–2 Chat/Goals/Today/Inbox | Slice 3–4 Focus/Timeout/VPN/Alarm/Onboarding |
| Meet at `Action` + `CanonicalLifeState` | |

---

## 8. File-level checklist (P0 create list)

**Must exist before demo:**

- [ ] `data/model/CanonicalLifeState.kt`
- [ ] `data/model/ChatModels.kt`
- [ ] `data/repo/LifeStateRepository.kt`
- [ ] `data/repo/ChatRepository.kt`
- [ ] `domain/Action.kt` + `ActionExecutor.kt`
- [ ] `domain/ProjectionBuilder.kt` + `Compactor.kt` + `TimelineMerger.kt` + `RiskCalculator.kt`
- [ ] `agent/AgentController.kt` + `GeminiClient.kt` + `OfflineFallbacks.kt` + `SystemPromptBuilder.kt`
- [ ] `email/MailboxSync.kt` + `SeedMailboxSync.kt` + `EmailClassifier.kt`
- [ ] `enforce/FocusService.kt` + `TimeoutMonitor.kt` + `OverlayController.kt`
- [ ] `enforce/LifeOsVpnService.kt`
- [ ] `enforce/AlarmScheduler.kt` + `AlarmReceiver.kt` + `BootReceiver.kt` + `AlarmActivity.kt`
- [ ] `ui/feature/*` six tabs + onboarding
- [ ] `res/layout/overlay_block.xml`
- [ ] `AndroidManifest.xml` complete
- [ ] `assets/seed_mailbox.json`

---

## 9. Testing plan (per slice)

| Slice | Proof |
| --- | --- |
| 0 | `adb install` launches Chat empty state with LifeOS branding |
| 1 | Debug expand → Goals shows risk + Wellbeing shows IG 30m |
| 2 | Offline chat utterance → same as debug expand; compact chat keeps goal count |
| 3 | Grant perms → open blocked app → overlay; alarm fires |
| 4 | Load sample inbox → Add to schedule → Today row; VPN toggle |
| 5 | Full 90s demo script recorded |

Instrument with Logcat tags: `LifeOS/Agent`, `LifeOS/Exec`, `LifeOS/Focus`, `LifeOS/VPN`, `LifeOS/Mail`.

---

## 10. Out of scope in code (do not start)

Multi-module Gradle, Hilt, Room, WorkManager-heavy sync, full Gmail OAuth UI polish, Compose overlay, AccessibilityService, iOS, backend, Health Connect, personality marketplace.

---

## 11. Relationship to other plan docs

| Doc | Role |
| --- | --- |
| [`lifeos_complete_hackathon_plan.md`](lifeos_complete_hackathon_plan.md) | Product, permissions, triage, demo |
| **This doc** | UI wire behavior, packages, class contracts, code completion order |
| [`lifeos_hackathon_prototype_7372d322.plan.md`](lifeos_hackathon_prototype_7372d322.plan.md) | Earlier shorter prototype — superseded for scope; keep for history |

When implementing, **follow this doc’s slices**; if product triage conflicts, P0 table in the complete hackathon plan wins.
