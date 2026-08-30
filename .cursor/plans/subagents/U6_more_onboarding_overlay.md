---
title: "U6 — More, Onboarding, and the enforcement overlay"
wave: 1
parallel: true
blocked_by: "U0"
soft_depends_on: "U7 (calendar settings) — guard behind a null check"
ai_credentials: none
timebox: "75 minutes"
---

# U6 — More, Onboarding, overlay

> Three surfaces, and one of them is not Compose. The blocked-app overlay in `:enforce` is XML, hand-styled, and it is the screen the audience sees at the emotional peak of the demo — when the phone refuses to open Instagram. It has to match the new design language even though you cannot use any of U0's composables in it.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.7.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/more/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/onboarding/**`
- `enforce/src/main/res/layout/**` and `enforce/src/main/res/drawable/**`

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, other `ui/screens/**`, any `enforce/**` Kotlin (U0 has `AppCatalogImpl`; the overlay controller logic is not yours — restyle the layout only), `core/**`, `app/**`.

---

## More

Grouped `LifeOsCard(level = 1)` sections with icon rows and `BorderSubtle` dividers, replacing today's flat list.

**Persona carousel.** A horizontally scrollable row of persona cards, each showing the name and a one-line tone preview so the choice is meaningful rather than a label. Selected card gets the `active` surface treatment. Persist via `executor.execute(listOf(Action.SetPersona(id)), ...)`.

**"Compact chat" proof card — demo critical.** Today this is a plain button. Make it a card that, on tap, calls `compactor.ensureWindow()` and then animates the message count from before to after (`animateIntAsState`), with the resulting summary shown beneath. The point it must land: the transcript shrank, and the goals, timeouts, and alarms did not. Show a small `SuccessWash` row asserting "0 goals lost · 0 caps lost" computed from the canonical state before and after. This is the visual proof of the compaction-proof architecture.

**Calendar sync (soft dependency on U7).** A toggle bound to `settings.calendarSyncEnabled`, the resolved calendar name, a "Sync now" action, and an "Open in Calendar" button firing a `CalendarContract` view intent. Resolve the port through a nullable lookup and hide the whole section when absent. Do not edit `core/`; do not block on U7.

**Material You toggle — you own this.** A "Use wallpaper colours" switch that drives `LifeOsTheme(dynamicColor = ...)`. U0b added the parameter, defaulted to `false`; you supply the value. Persist it as a new defaulted field on `Settings` — except you may not edit `core/`, so instead keep it in the existing `MoreViewModel` backed by whatever store U0b's handoff names, and flag in your handoff if a `Settings` field is genuinely required so U7 (the only agent allowed in `core/`) can add it.

Show a live preview: a row of accent swatches that updates as the switch flips, so the user sees what they are choosing. Note in the subtitle that only the accent changes and the dark surfaces stay put — that is the deliberate design, not a bug.

**Settings + debug.** Surface `chatWindowK`, `autoScheduleHighConfidence`, and `demoStrictTimeouts` as proper switch rows with explanatory subtitles. Collapse debug actions into an expandable section.

## Onboarding

Rebuild as a full-bleed 5-step pager. Each step: a large glyph inside an `AccentWash` halo, title at `headlineMedium`, body at `bodyLarge` `TextSecondary`, one `PrimaryButton`, and progress dots at the bottom. Keep the existing five permission steps and the existing `OnboardingScreen(onDone)` signature that `LifeOsNav` calls.

Two behaviours to preserve, because enforcement depends on them: re-verify grants on `ON_RESUME` (special-access grants happen in Settings, outside the app, so nothing else will tell you they landed), and animate `PermissionRow` from ungranted to granted when they do. The final step is "You're set" with a lightweight accent particle burst on `Canvas` — a few dozen animated points, not a physics engine.

Add a skip affordance. A user who cannot get past onboarding cannot demo the app, so no step may be a hard gate.

## The enforcement overlay (XML)

`enforce/src/main/res/layout/` — restyle by hand with literal color values, since U0's Compose tokens are unreachable here. Match them exactly:

- Root scrim `#E0` alpha over `#07090C` (`Backdrop`)
- Card `#171C25` (`Surface2`), 20dp corners via a `<shape>` drawable, 1px `#1FFFFFFF` stroke
- Title `#E8EEF5`, persona line `#97A3B2`
- Full-width button filled `#A8C7FA` (`Accent`) with `#0A305F` (`AccentInk`) text, 999dp corners
- Include the blocked app's name and the remaining cap, if the existing controller already passes them — check before adding parameters, and do not change the controller's Kotlin.

## Acceptance criteria

- More renders grouped cards with a working persona carousel.
- Compact chat animates the count and shows the "nothing lost" assertion.
- Onboarding runs all five steps, re-verifies grants on resume, and can be skipped.
- The overlay visually matches the app when triggered by opening a capped app.
- No Kotlin file under `enforce/` is modified.
