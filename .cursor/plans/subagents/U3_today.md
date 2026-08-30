---
title: "U3 — Today screen redesign"
wave: 1
parallel: true
blocked_by: "U0"
soft_depends_on: "U7 (calendar) — guard behind a null check"
ai_credentials: none
timebox: "80 minutes"
---

# U3 — Today

> Today is currently an unstyled flat list: bare times in a left column, plain text titles, no cards, no depth, and two naked chevrons for date navigation. It should be the screen that answers "what is happening to me right now" in under a second.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.3.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/today/**`

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, other `ui/screens/**`, `core/**`, `domain/**`, `app/**`.

---

## Build this

**The "Now" hero card.** Leading `LifeOsCard(level = 2, active = true)`. Resolve the current or next timeline item; show a `ProgressRing` of elapsed fraction, the title, a `TimeNumeric` countdown ("ends in 24m" / "starts in 1h 10m"), and an inline Start Focus CTA. When nothing is scheduled, it becomes a soft prompt to plan the day that navigates to `chat`. Recompute the countdown on a 30-second ticker — use a `LaunchedEffect` with `delay`, not a per-frame recomposition.

**Week strip** replacing the chevrons. Seven horizontally scrollable day pills showing weekday initial and date number. Today is a `Mint400` fill; the selected day gets a `MintWash` ring; days holding items get a small dot beneath. Keep the existing date-selection state in `TodayViewModel` — you are replacing the control, not the model.

**Timeline rail.** A real rail, not a list: `TimeNumeric` time in a fixed-width left gutter, a vertical `BorderSubtle` connector running the full height, a kind-colored dot centred on the line, and a `Surface1` card to the right holding title, subtitle, and a `LineageChip` when `sourceGoalId` is set. Color the dot per `TimelineItem.kind`. Group headers (`MORNING` / `AFTERNOON` / `EVENING`) use `SectionHeader` with a hairline.

**The "now" line.** A `Mint400` horizontal rule with a filled dot, injected at the correct chronological position between items — the single detail that makes a timeline feel live. Skip it when viewing a day other than today.

**Completion.** Tapping an item's checkbox strikes the title through, fades the row to 40%, and collapses it with `Motion.standard`. Use U0's `AnimatedCheckbox`.

**Empty state.** U0's `EmptyState` with "Nothing scheduled" and an action that navigates to `chat`.

## Fix while you are here

`TodayScreen.kt:127` keys list items on `"${group.title}-$index-${item.refId}-${item.kind}-${item.title}"`. Concatenating the index *and* the title into a key defeats the purpose — the key changes whenever the list reorders, so Compose cannot track items across updates and your enter/exit animations will not work. Key on something stable and genuinely unique: `"${item.kind}-${item.refId}"`, and if `refId` can collide across kinds, that composite is already sufficient. Drop the index.

## Calendar events (soft dependency on U7)

U7 adds `CalendarPort.readRange`. Render external device events **in the same rail**, tinted `Info`, with a "Google Calendar" source label so they are visibly not LifeOS-owned, and non-completable.

U7 may not have landed when you start. Guard it: resolve the port through a nullable lookup and render nothing when absent. Do not add a hard dependency, do not edit `core/`, and do not block on U7.

## Contracts you consume

`UiPorts.value.timeline.forDate(state, dateIso)` returns `List<TimelineItem>`; `lifeState.state` for the canonical state; `enforce.startFocus(...)` for the CTA. `TodayViewModel` already groups items — read it first.

## Acceptance criteria

- Now card shows a live countdown that visibly updates.
- Week strip navigates days and marks days that have items.
- Timeline renders as a rail with connector, dots, and cards.
- The "now" line appears at the right position on today, and not on other days.
- Completing an item animates; list items animate on insertion.
