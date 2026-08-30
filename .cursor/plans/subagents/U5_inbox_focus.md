---
title: "U5 — Inbox and Focus/Wellbeing redesign"
wave: 1
parallel: true
blocked_by: "U0"
soft_depends_on: "U7 (calendar) — guard behind a null check"
ai_credentials: none
timebox: "85 minutes"
---

# U5 — Inbox and Focus

> You own two screens and the larger one, `WellbeingScreen.kt` at 570 lines, is the biggest file in the UI module. It is also the screen that crashed the app until U0 fixed the app catalog. Budget accordingly: Focus first, Inbox second.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §3.5–3.6.

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/screens/wellbeing/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/inbox/**`

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, other `ui/screens/**`, `core/**`, `enforce/**`, `email/**`, `app/**`.

---

## First: defensive dedupe

U0 fixed the root cause of the Focus crash in `AppCatalogImpl` (`queryIntentActivities` returned duplicate packages, and `WellbeingScreen.kt:219` keyed a `LazyColumn` on package name). Add a belt-and-braces `distinctBy { it.packageName }` where you consume `launchableApps()` anyway — a keyed list that can crash the process on a data anomaly is not a risk worth carrying into a demo. The same applies to the picker sheet at line 498.

## Focus / Wellbeing

**Focus hero.** A `LifeOsCard(level = 2, active = focus.active)` with two states.

*Idle:* a `ProgressRing` at rest around a large Start button, plus 25 / 50 / 90-minute duration presets as a `SegmentedControl`, and the mode shown as a pill.

*Active:* a large countdown `ProgressRing` driven by `focus.startedAtEpochMs` and `endsAtEpochMs`, remaining time at `displayMedium` in `TimeNumeric`, `MonogramAvatar`s for the blocked or allowed apps, and a full-width End button. Tick every second with a `LaunchedEffect` + `delay`, not per-frame recomposition.

**Timeout caps.** U0's redesigned `TimeoutBar` already handles the monogram, the `Radius.full` `Success`→`Warn`→`Danger` track, and the source lineage chip. Your job is the section around it: a header with the cap count, live usage from `enforce.usageTodayMinutes(packages)`, and an add-cap affordance opening the app picker. Refresh usage on `ON_RESUME` rather than polling.

**Network guard.** A `SegmentedControl` for OFF / BLACKLIST / WHITELIST with a shield icon that animates between states, the affected package list below, and an honest one-line explanation of what the VPN actually does. Route changes through `executor.execute`, never by calling `enforce` directly — the executor is what persists state.

**App picker.** Promote the current inline list to a real `ModalBottomSheet` at `Radius.xl`: search field, sticky selected count in the header, `AppToggleRow` items, and a confirm button. Dedupe the list.

**Permission health.** If `system.permissions().enforcementReady` is false, raise a `WarnWash` card at the top naming the missing grants with a jump to onboarding. Without these grants focus and timeouts silently do nothing, so this must be impossible to miss.

## Inbox

**Candidate cards.** `LifeOsCard(level = 1)` with a `Success` left accent stripe: `MonogramAvatar` from the sender, subject at `titleMedium` bold, a two-line snippet at `bodyMedium` `TextSecondary`, U0's `ConfidenceMeter`, and a kind `Pill` colored per `CandidateKind` — `EXAM` danger, `DEADLINE` warn, `EVENT` info, `NOISE` tertiary.

**Actions.** A filled `PrimaryButton("Add to calendar")` and a `GhostButton("Dismiss")`, replacing the current bare `TextButton`s. Once U7 lands, that first button genuinely writes to the device calendar — until then it promotes to a LifeOS event exactly as it does today. Guard the calendar call behind a null check; do not block on U7.

**Sections.** Keep the existing pending / noise / handled split with counts in the headers, collapsible with animation. Existing `key = { it.id }` on those lists is already correct — leave it.

**Empty state.** U0's `EmptyState` with a filled accent "Load sample" action, replacing today's bare `TextButton`.

## Contracts you consume

`UiPorts.value`: `lifeState.state`, `apps.launchableApps()`, `enforce.usageTodayMinutes(...)`, `system.permissions()`, `executor.execute(...)`, `mailbox.fetch(...)`, `classifier.classify(...)`. `WellbeingViewModel` is only 16 lines, so most Focus state lives in the composable — you may grow the ViewModel, but keep the `WellbeingScreen(onNavigate)` signature that `LifeOsNav` calls.

## Acceptance criteria

- Focus tab opens, and repeated open/close never crashes.
- Starting a focus session shows a live countdown that decrements every second.
- Caps show real usage minutes and their source goal.
- Network guard switches modes and persists across app restart.
- App picker opens as a sheet, searches, and contains no duplicate packages.
- Inbox candidates render with confidence and kind, and both actions work.
