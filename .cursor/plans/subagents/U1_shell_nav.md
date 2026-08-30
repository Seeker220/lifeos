---
title: "U1 — App shell, navigation bar, headers, transitions"
wave: 1
parallel: true
blocked_by: "U0"
ai_credentials: none
timebox: "60 minutes"
---

# U1 — App shell and navigation

> You own the frame every other agent's screen renders inside. Two visible defects are yours to fix: the persona label clipped off the right edge of the screen, and six labelled nav items crowding a bar that Material sizes for five.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.1.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/nav/**`
- `ui/src/main/kotlin/com/lifeos/ui/shell/**` (new)

## Files you must NOT touch

Any `ui/screens/**`, `ui/theme/**`, `ui/components/**`, `core/**`, `app/**`.

---

## What is wrong now

`LifeOsNav.kt` wraps everything in a stock `Scaffold` + `TopAppBar` + `NavigationBar`. Three problems:

1. `actions = { Text(persona.name, color = MdPrimary) }` — a raw `Text` with no padding, **clipped by the screen edge**. On the emulator "Strict" is cut mid-glyph.
2. Six `NavigationBarItem`s each with an always-visible label. Labels compress and the bar looks cramped.
3. `Modifier.padding(padding)` hard-pads the `NavHost`, so content can never scroll *under* the bars — which is what makes the translucent-bar effect work.

## Build this

**`LifeOsHeader(title, subtitle, actions)`** — a custom header, not `TopAppBar`. `headlineMedium` title, optional `bodyMedium` `TextSecondary` subtitle, trailing slot. Content scrolls under it behind a `ScrimEdge(top = true)`.

**`PersonaPill(persona, onClick)`** — `R.full` `MintWash` chip, mint status dot, persona name at `labelMedium`, `S.x3` horizontal padding, and at least `S.x4` inset from the screen edge. Tapping it opens a persona bottom sheet. Read the persona from `UiPorts.value.lifeState.state`; persist a change via `executor.execute(listOf(Action.SetPersona(id)), ActionOrigin.USER)` — do not mutate the store directly, so the change flows through the same path the agent uses.

**`LifeOsNavBar`** — `Surface2 @ 92%` with a `BorderSubtle` top hairline. The crowding fix: **only the selected item renders its label**, inside a `MintWash` pill whose width animates via `Motion.emphasized`. Unselected items are icon-only at `TextTertiary`, selected icon is `Mint400`. Fire a haptic on tab change.

**Nav graph** — keep all six routes exactly as they are (`chat`, `today`, `goals`, `inbox`, `wellbeing`, `more`); the user chose to keep all six tabs and other agents' `onNavigate` callbacks depend on `LifeOsDestination`. Do not rename the enum or its routes.

Replace the hard `Modifier.padding(padding)` with per-screen `contentPadding` passed down through a `LocalScreenPadding` composition local, so lists scroll under the bars while their first and last items stay reachable.

**Transitions** — `NavHost` with `enterTransition = fadeIn(Motion.navFade) + slideInVertically { 8 }` and the matching exit. Never a horizontal push. Also add `popUpTo(startDestination) { saveState = true }` and `restoreState = true` to the navigate calls, otherwise tab switching stacks destinations forever and back-button behaviour is wrong.

## Acceptance criteria

- Persona pill fully visible on 1284×2778, verified by screenshot.
- Six tabs fit with no label compression; selected label animates in.
- Content scrolls under both bars with a visible fade, and no item is unreachable.
- Tapping the same tab twice does not stack destinations.
- All six routes still resolve.
