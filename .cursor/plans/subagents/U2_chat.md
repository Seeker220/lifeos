---
title: "U2 — Chat screen redesign"
wave: 1
parallel: true
blocked_by: "U0"
ai_credentials: none
timebox: "80 minutes"
---

# U2 — Chat

> This is the flagship screen and currently the emptiest. A fresh install shows one exchange and roughly 600dp of black nothing. You are also redesigning the single most important moment in the product: the instant the agent reports what it just changed about the user's life.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.2.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/chat/**`

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, other `ui/screens/**`, `core/**`, `agent/**`, `app/**`.

---

## Build this

**The agent hero** replaces the dead space. An `AgentGradient` radial glow behind a large `AutoAwesome` glyph, the prompt "What should we change today?" at `headlineMedium`, and a 2×2 grid of suggestion cards — icon, title, one-line subtitle. Wire them to real capabilities the offline fallbacks already handle:

| Card | Sends |
| --- | --- |
| Crack Google interview | "help me crack the Google interview in 1 month" |
| Deep focus block | "start a 50 minute focus session" |
| Cap distractions | "cap Instagram at 30 minutes a day" |
| Triage my inbox | "check my email for anything important" |

The hero cross-fades out once `transcript.messages` is non-empty. Keep the existing suggestion-chip row for the non-empty state.

**Bubbles.** User: `colorScheme.primary` fill, `colorScheme.onPrimary` text, `Radius.lg` with the bottom-right corner tightened to `Radius.xs`. Assistant: `Surface1`, hairline, a 1px `AgentGradient` top edge, bottom-left corner tightened. Cap width at 88% so long messages do not span edge to edge. New messages enter with fade + scale-from-0.94.

**`AppliedChangesCard`** — this is the money shot, and today it is a hairline divider plus a grey "Applied 1 change" caption. Make it a nested `Surface2` block: an `AutoAwesome` icon plus "Applied 3 changes" header, then U0's `ActionChipRow`. Each chip navigates to the screen owning that change — `FOCUS`/`TIMEOUT` → `wellbeing`, `GOAL`/`TASK` → `goals`, `EVENT`/`BLOCK`/`ALARM`/`HABIT` → `today`, `EMAIL` → `inbox`, `MEMORY`/`PERSONA` → `more`. The existing `onNavigate: (LifeOsDestination) -> Unit` parameter is how you get there.

**Composer.** `Surface3` pill at `Radius.xl`, growing to 5 lines before scrolling internally. Circular `colorScheme.primary` send button that scales and rotates on dispatch and disables while `sending`. Keep the existing IME behaviour — the current screen already requests soft input correctly.

**Typing indicator.** Three `AccentVivid` dots on staggered `Motion.emphasized` scale, inside an assistant-shaped bubble so it reads as the agent composing.

**Pending-email banner.** `SuccessWash` card that slides in from the top when `emailCandidates` has `PENDING` entries, with a count and a tap-through to `inbox`.

**Undo expansion.** Keep the existing behaviour, restyled as a `GhostButton` with an `Undo` icon, shown inline on messages carrying an `expansionGoalId`.

## Contracts you consume

`ChatViewModel` already exposes what you need — read it before rewriting, and keep its public surface unless you have a reason. Data reaches you through `UiPorts.value`: `chat.transcript`, `lifeState.state`, and `agent.send(text)`. `agent.send` is suspending and may take seconds; the `sending` flag must gate the send button and drive the typing indicator.

Do not call `executor.execute` directly — user text goes through `agent.send`, which is what produces the applied-changes chips.

## Acceptance criteria

- Fresh install shows the hero and four working suggestion cards, no dead space.
- Sending a suggestion produces an `AppliedChangesCard` with tappable chips that navigate correctly.
- Typing indicator animates while the turn is in flight.
- List auto-scrolls to the newest message, animated.
- Long messages wrap and never overflow horizontally.
