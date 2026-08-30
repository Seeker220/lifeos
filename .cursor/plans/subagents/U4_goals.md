---
title: "U4 — Goals screen redesign"
wave: 1
parallel: true
blocked_by: "U0"
ai_credentials: none
timebox: "70 minutes"
---

# U4 — Goals

> Goals is where the product thesis either becomes visible or stays invisible. LifeOS claims a goal turns into real enforcement — timeouts, blocks, alarms. Right now the Goals screen shows a title, a flat percentage, and one grey line of text. Your job is to make the goal → enforcement lineage the most legible thing on screen.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.4.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/goals/**`

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, other `ui/screens/**`, `core/**`, `domain/**`, `app/**`.

---

## Build this

**The goal card.** `LifeOsCard(level = 1, onClick = expand)`. Top row: title at `titleLarge` plus a hardness `Pill` (`HARD` → `DangerWash`, `SOFT` → `MintWash`). A `RiskRing` from U0 replaces the flat `RiskBadge` text — mint below 40%, warn below 70%, danger above, with the animated percentage inside the ring. Deadline rendered as relative-plus-absolute: "29 Sep · 30 days left", and past due as "Overdue by 2 days" in `Danger`.

**Lineage chips — the important part.** A `FlowRow` under the deadline showing what this goal actually spawned, derived by scanning `CanonicalLifeState` for entities whose `sourceGoalId` matches:

- `appTimeouts` → `Caps: YouTube 45m` (one chip per timeout)
- `scheduleBlocks` → `2 study blocks`
- `alarms` → `1 alarm`
- `habits` → `1 habit`
- `tasks` → `3 tasks`

Every entity in `Entities.kt` carries `sourceGoalId` precisely so this is possible. Chips are tappable and navigate to the owning screen via the existing `onNavigate` parameter.

**Expansion.** Tapping the card expands it with `Motion.emphasized` to reveal the linked todos, blocks, and timeouts as indented rows. Collapse on second tap. Keep expansion state in the composable, not the ViewModel.

**Todos.** Replace the bare checkbox rows with proper `Surface1` rows: U0's `AnimatedCheckbox`, title, a `LineageChip` when `sourceGoalId` is set, an estimate pill (`30m`), and a due date when present. Add swipe-to-complete via `SwipeToDismissBox`. Completed todos strike through and fade before collapsing.

**Gamification header.** A compact `LifeOsCard(level = 2)` at the top: XP as a large `displayMedium` number driven by `animateIntAsState` so it counts up, and the streak as a flame glyph plus day count. Read from `state.gamification`.

**Empty state.** U0's `EmptyState` — "No goals yet", with an action navigating to `chat` and copy that hints at what to say ("Try: crack the Google interview in 1 month").

## Contracts you consume

`GoalsViewModel` already exposes goals, todos, and risk — read it first and keep its public surface where you can. Risk comes from `UiPorts.value.risk.riskPercent(state, goalId)`. Completion goes through `executor.execute` with the appropriate `Action`, not by mutating the store.

## Acceptance criteria

- Every goal shows an animated risk ring, not flat text.
- The seeded "Crack Google interview" goal displays lineage chips including its YouTube cap, and each chip navigates.
- Cards expand and collapse smoothly.
- Todos complete via both tap and swipe, animated.
- XP counts up rather than snapping.
