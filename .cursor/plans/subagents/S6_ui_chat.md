---
title: "S6 — :ui Chat, the agent surface"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "70 minutes"
---

# S6 — `:ui` Chat

> Chat is the home screen and the first thing a judge sees. It is also where the product's whole claim gets made: one sentence in, a visible pile of enforced changes out. Your job is to make those changes **legible** — a reply bubble alone looks like every other AI todo app.

Design reference: [`../lifeos_ui_technical_implementation.md`](../lifeos_ui_technical_implementation.md) §2.1. Architecture: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Build the chat screen and its ViewModel: message list, composer, typing state, applied-action chips that navigate, suggestion chips for the demo prompts, undo-expansion, and a pending-email banner.

## AI credentials

**None.** You call `AgentPort.send(text)` and it decides between Azure and offline fallbacks internally. `:ui` never touches an API key or an HTTP client.

---

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/chat/**` — `ChatScreen.kt`, `ChatViewModel.kt`, and any private composables you need

## Files you must NOT touch

- `ui/.../components/**` — S5 owns these. Call them; do not edit them. If one is missing something, work around it and note it in your handoff.
- `ui/.../theme/**` and `ui/.../nav/**` — S0's.
- `ui/.../screens/today|goals|more/**` (S7), `.../wellbeing|inbox/**` (S8)
- `core/**` (frozen), `domain/**`, `agent/**`, `enforce/**`, `email/**`, `app/**`, any `build.gradle.kts`

S0 published your entry point and it is fixed: `@Composable fun ChatScreen(onNavigate: (LifeOsDestination) -> Unit)`.

---

## Contracts you consume

All via `UiPorts.value` (S0's holder), all from `:core`:

```kotlin
agent: AgentPort              // suspend fun send(userText: String): AgentTurnResult
chat: ChatStore               // transcript: StateFlow<ChatTranscript>
lifeState: LifeStateStore     // state: StateFlow<CanonicalLifeState>
executor: ActionExecutorPort  // suspend fun execute(actions, origin): ExecuteReport
```

Types you render: `ChatMessage(id, role, text, atEpochMs, appliedChips, expansionGoalId)`, `AppliedChange(label, kind, refId)`, `AgentTurnResult(reply, actions, report, source, expansionGoalId)`, `Personas`.

While S1 and S2 are still working, `AgentPort` is S0's stub returning an empty result. **Build and test against that first** — an empty-response path that renders correctly is exactly what you need when the real agent misbehaves on stage.

---

## Step 1 — `ChatViewModel` (20 minutes)

A plain `androidx.lifecycle.ViewModel`. No Hilt; construct it with `viewModel { ChatViewModel(UiPorts.value) }`.

```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val pendingEmailCount: Int = 0,
    val personaName: String = "Strict",
    val lastReport: ExecuteReport? = null,
    val lastExpansionGoalId: String? = null,
)

class ChatViewModel(private val ports: Ports) : ViewModel() {
    val uiState: StateFlow<ChatUiState>
    fun send(text: String)
    fun undoExpansion(goalId: String)
}
```

Build `uiState` by combining `ports.chat.transcript` and `ports.lifeState.state` and mapping into `ChatUiState`. Derive `pendingEmailCount` from `emailCandidates.count { it.status == PENDING }` and `personaName` from `Personas.byId(state.personaId).name`.

`send(text)`:

1. Ignore blank input.
2. Set `sending = true`.
3. `viewModelScope.launch { runCatching { ports.agent.send(text) } }`.
4. On success, store `report` and `expansionGoalId` in state for the chips and the undo affordance.
5. On failure, append nothing and log — `AgentController` already swallows its own errors, so reaching here means something structural. Do not show a raw stack trace to a judge.
6. Always clear `sending` in a `finally`.

The user and assistant messages themselves are appended by `AgentController` into `ChatStore`, so they arrive through the flow. **Do not append them yourself** — you will get duplicates. The only local state you own is `sending`.

`undoExpansion(goalId)` calls `ports.executor.execute(listOf(Action.RevertExpansion(goalId)), ActionOrigin.USER)`.

---

## Step 2 — Message list (20 minutes)

A `LazyColumn` with `reverseLayout = false`, and an auto-scroll to the newest item:

```kotlin
LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
}
```

**User bubble** — right-aligned, primary container background, max width 80%, rounded 16.dp with the bottom-end corner at 4.dp.

**Assistant bubble** — left-aligned, `surface` background with a hairline border, same rounding mirrored. Contains, stacked:

1. The reply text in `bodyLarge`.
2. When `appliedChips` is non-empty, a divider and a small label: `"Applied ${appliedChips.size} change${plural}"`.
3. `ActionChipRow(chips, onChipClick)` — see Step 3.
4. When `expansionGoalId != null`, a `TextButton` reading **"Undo expansion"** calling `vm.undoExpansion(it)`.

`ChatMessage.appliedChips` is a `List<String>` (labels only), but `ActionChipRow` takes `List<AppliedChange>`. For historical messages you only have the labels, so reconstruct `AppliedChange(label, kind = inferKindFromLabel(label), refId = null)`. Infer from the label prefix that S1 produces — `"Goal: "` → `GOAL`, `"Timeout: "` → `TIMEOUT`, `"Done: "` → `TASK`, `"Scheduled: "` → `EVENT`, `"Reverted"` → `REVERT`, and so on, defaulting to `MEMORY`.

For the message that just arrived, prefer `state.lastReport.applied` because it carries real `refId`s and correct kinds. This asymmetry is deliberate: the freshest response — the one on screen during the demo — gets fully working chips, and history degrades gracefully.

**Typing indicator** — when `sending`, append a footer item: an assistant-styled bubble with three animated dots using `rememberInfiniteTransition`. Do not use a spinner; it reads as loading, not thinking.

**Empty state** — `EmptyState("Tell me a goal.", "I'll turn it into a schedule and enforce it.")` plus the suggestion chips from Step 4.

---

## Step 3 — Chip navigation

`onChipClick` maps `ChangeKind` to a destination and calls `onNavigate`:

- `GOAL`, `TASK`, `XP` → `LifeOsDestination.GOALS`
- `EVENT`, `HABIT`, `BLOCK`, `ALARM` → `TODAY`
- `TIMEOUT`, `FOCUS`, `NETWORK` → `WELLBEING`
- `EMAIL` → `INBOX`
- `MEMORY`, `PERSONA`, `REVERT` → `MORE`

This is a small feature with an outsized demo payoff: the presenter taps "Timeout: Instagram 30m" and lands on the Wellbeing screen showing the cap attributed to the goal. It closes the loop from sentence to enforcement in one gesture, so do not cut it.

---

## Step 4 — Composer and suggestions (15 minutes)

**Composer** — a bottom bar, `OutlinedTextField` with placeholder "Ask LifeOS…", `maxLines = 4`, `imeAction = Send`, plus a filled circular send `IconButton`. Disable the button when blank or `sending`. Clear the field on send. Use `WindowInsets.ime` padding so the keyboard does not cover it.

**Suggestion chips** — shown only when `messages.isEmpty()`, three `SuggestionChip`s wired to fill and send:

1. `"Crack a Google interview in 1 month"`
2. `"Focus mode, only Chrome and Docs"`
3. `"Check my email for exams"`

These are exactly the utterances S2's offline fallbacks match. That is not a coincidence — **the presenter should never have to type during the demo**, and a typo on stage in front of a keyword matcher is a real risk.

**Pending-email banner** — when `pendingEmailCount > 0`, a dismissible bar above the composer: `"$n email${plural} need a decision"` with a "Review" action calling `onNavigate(INBOX)`. Keep the dismissal in local `remember` state; it need not persist.

**Persona chip** — S0's top bar already shows the persona. Do not add a second one.

---

## Verification

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity
adb -s emulator-5554 logcat -s LifeOS/Agent LifeOS/Exec
```

Cap yourself at two or three Gradle builds; seven other sessions share the lock. On `Timeout waiting to lock`, wait 20 seconds and retry.

Acceptance checklist:

- [ ] Chat is the start destination and shows the empty state with three suggestion chips
- [ ] Tapping a suggestion chip sends it without typing
- [ ] The typing indicator appears while `sending` and disappears after
- [ ] With `AgentPort` still stubbed, sending produces no crash and no duplicate bubbles
- [ ] Once S1 and S2 land, the interview prompt yields a reply bubble with 10 or more chips
- [ ] Tapping the timeout chip navigates to Wellbeing; tapping the goal chip navigates to Goals
- [ ] "Undo expansion" appears on the expansion message and removes the created entities
- [ ] The list auto-scrolls to the newest message
- [ ] Messages survive a process kill — `adb shell am force-stop com.lifeos.app`, relaunch, history intact
- [ ] After 40 messages the list still scrolls smoothly and older messages compact away without goals disappearing from Goals
- [ ] The keyboard does not cover the composer
- [ ] The pending-email banner appears when candidates exist and navigates to Inbox
- [ ] A blank send is a no-op

That process-kill test is worth doing properly: it is the only place the compaction-proof design becomes visible, and it is the second beat of the demo script.

## Timebox

70 minutes: ViewModel 20, message list 20, chips and navigation 15, composer and suggestions 15.

If behind at 55 minutes, cut in this order: the animated typing dots (use a static "…"), then the pending-email banner, then chip navigation (keep the chips as non-interactive labels). **Never** cut: the chips themselves, the suggestion chips, or auto-scroll. Invisible actions make the app look like a plain chatbot, which is the one impression that loses the room.

## Handoff notes for S9

Report which `ExecuteReport` chip label prefixes you relied on for kind inference, so S9 can confirm they match what S1 actually shipped. Also state whether `ActionChipRow` needed anything S5 did not provide.
