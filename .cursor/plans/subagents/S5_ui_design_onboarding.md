---
title: "S5 — :ui design system and permission onboarding"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "80 minutes"
---

# S5 — `:ui` design system and Onboarding

> Three other UI agents are calling your components **right now**, against the stub signatures S0 created. That means two rules: never change a signature, and get something visually correct into each component early rather than perfecting one of them. You also own the screen that decides whether enforcement works at all — without these grants, focus and timeouts silently do nothing.

Design reference: [`../lifeos_ui_technical_implementation.md`](../lifeos_ui_technical_implementation.md) §1 and §2.0. Architecture: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Turn S0's placeholder components into a coherent design system, and build the onboarding flow that walks the user through one runtime permission and four special-access hand-offs with live verification.

## AI credentials

**None.**

---

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/components/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/onboarding/**`

## Files you must NOT touch

- `ui/src/main/kotlin/com/lifeos/ui/theme/**` — S0 wrote the tokens. If a token is genuinely missing, **add a new one**; do not change an existing value, because S6, S7, and S8 are already rendering against them.
- `ui/src/main/kotlin/com/lifeos/ui/nav/**` — S0's nav graph already routes to you.
- `ui/.../screens/chat/**` (S6), `.../today|goals|more/**` (S7), `.../wellbeing|inbox/**` (S8)
- `core/**` (frozen), `domain/**`, `agent/**`, `enforce/**`, `email/**`, `app/**`, any `build.gradle.kts`

**Signature freeze.** These seven signatures were published by S0 and three agents depend on them. You may change bodies freely; you may not change parameters, names, or ordering:

```kotlin
@Composable fun ActionChipRow(chips: List<AppliedChange>, onChipClick: (AppliedChange) -> Unit)
@Composable fun RiskBadge(percent: Int)
@Composable fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit)
@Composable fun TimeoutBar(label: String, usedMinutes: Int, limitMinutes: Int, sourceLabel: String?)
@Composable fun EmptyState(title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun SectionHeader(text: String)
@Composable fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
```

If one of them needs an extra parameter, add it **with a default value** so existing call sites keep compiling, and say so in your handoff.

---

## Contracts you consume

Only `:core`, reached through `UiPorts.value` (S0's holder):

- `system: SystemAccess` → `permissions(): PermissionStatus` with `notifications`, `exactAlarms`, `usageAccess`, `overlay`, `vpnConsented`, `fullScreenIntent`, and the derived `enforcementReady`
- `lifeState: LifeStateStore` → for reading and writing `settings.onboardingComplete` and `personaId`
- `executor: ActionExecutorPort` → to persist the persona pick via `Action.SetPersona`
- `Personas.ALL` for the picker

`SystemAccess` may still be S0's stub returning all-false while S3 works. Build against that; the screen should look correct with everything ungranted, which is also its most important visual state.

---

## Step 1 — Components (35 minutes)

Order matters: do `SectionHeader`, `EmptyState`, and `RiskBadge` first — they are five-minute wins that immediately improve three other agents' screens.

**`SectionHeader`** — uppercase, `letterSpacing = 1.sp`, `labelSmall`, `onSurfaceVariant`, padded `16.dp` horizontal and `20.dp` top / `8.dp` bottom.

**`EmptyState`** — centered column, title in `titleMedium`, subtitle in `bodyMedium` at 70% alpha, optional `TextButton`. Must fill its parent and stay vertically centered. Every screen shows this on first launch, so it is the first thing a judge sees.

**`RiskBadge`** — a pill with the percent and a word. Colour from the token set: under 40 primary `#2EE6A6` on a 12%-alpha background, under 70 warn `#F5A524`, otherwise danger `#FF5C5C`. Label `"On track"`, `"At risk"`, `"Critical"`. Keep the text inside the pill; do not let long labels wrap.

**`ActionChipRow`** — a `FlowRow` (or a wrapping `Row` if `FlowRow` is unavailable in this Compose version) of small `AssistChip`s, one per applied change, each with a leading icon chosen from `ChangeKind`:

- `GOAL` → `Flag`, `TASK` → `CheckCircle`, `EVENT` → `Event`, `HABIT` → `Repeat`, `BLOCK` → `Schedule`
- `ALARM` → `Alarm`, `TIMEOUT` → `HourglassBottom`, `FOCUS` → `Shield`, `NETWORK` → `Wifi`
- `MEMORY` → `Psychology`, `PERSONA` → `Face`, `XP` → `Star`, `EMAIL` → `Mail`, `REVERT` → `Undo`

Truncate each label to 28 characters. Chip taps drive navigation in S6's chat, which is why the callback receives the whole `AppliedChange` rather than a string.

**`TimeoutBar`** — label row with `usedMinutes / limitMinutes` on the right, a `LinearProgressIndicator` beneath it, and `sourceLabel` in `labelSmall` primary underneath when non-null. Progress colour goes warn above 80% and danger at or above 100%. Clamp progress to `1f` — a 45-of-30 overflow must not paint outside the track. This is the component that makes goal-driven enforcement legible, so the `"From: Crack Google interview"` line matters.

**`AppToggleRow`** — icon placeholder, app label, `Switch`. **Do not load real app icons**; `PackageManager.getApplicationIcon` returns a `Drawable` and bridging it into Compose costs time. Use a circular monogram of the label's first letter on a surface-variant background. Height 56.dp, full-width clickable, toggling on row tap as well as switch tap.

**`PermissionRow`** — leading status icon (`CheckCircle` in primary when granted, `RadioButtonUnchecked` in `onSurfaceVariant` when not), title in `bodyLarge`, subtitle in `bodySmall` at 70% alpha, and a trailing control that is a `Button` labelled "Grant" when ungranted and the text "Granted" in primary when granted. Never show a tappable Grant button for something already granted — the demo audience notices.

---

## Step 2 — `OnboardingScreen` (45 minutes)

Signature is fixed by S0: `@Composable fun OnboardingScreen(onDone: () -> Unit)`.

### Layout, single scrollable column

1. **Hero** — "LifeOS" in `displaySmall`, then the tagline *"Plans that enforce themselves."* This is the only place the wordmark is hero-sized, so give it room.
2. **Persona picker** — three `FilterChip`s from `Personas.ALL`. Selecting one dispatches `Action.SetPersona(id)` through `ActionExecutorPort` immediately; do not defer to Continue.
3. **A one-sentence disclosure** before the permission list: *"LifeOS needs these to actually block apps and wake you up. Everything stays on your device."* This is not decoration — the VPN and usage-access dialogs are alarming without it, and a judge asking "is this spyware" is a bad 15 seconds.
4. **Five `PermissionRow`s**, in the order below.
5. **Continue** — a filled `Button`, enabled when `usageAccess && overlay` (that is `PermissionStatus.enforcementReady`). Notifications and exact alarms produce a warning line rather than a block; VPN is fully optional.
6. **"Skip for now"** `TextButton` — always enabled. A judge will not wait for you to fix a stuck grant.

### The five grants and their exact hand-offs

**1. Notifications** — runtime permission, not a Settings trip. Use `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` with `Manifest.permission.POST_NOTIFICATIONS`. Subtitle: *"So focus sessions and alarms can reach you."*

**2. Exact alarms** — `Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)` with `data = Uri.fromParts("package", packageName, null)`. Subtitle: *"So a 7am wake-up fires at 7am, not whenever."*

**3. Usage access** — `Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)`. This one does **not** accept a package URI reliably; it opens the full list and the user finds LifeOS. Subtitle: *"So LifeOS can tell which app is in front of you."* Add a hint line: *"Find LifeOS in the list and turn it on."* Without that hint people bounce straight back out, and then focus enforcement silently does nothing — the single most common failure in this category of app.

**4. Display over other apps** — `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)` with `data = Uri.parse("package:$packageName")`. Subtitle: *"So the block screen can appear over the app you're avoiding."*

**5. Network control (VPN)** — `VpnService.prepare(context)`. If it returns an `Intent`, launch it with `rememberLauncherForActivityResult(StartActivityForResult())`. If it returns `null`, consent already exists. Subtitle: *"Optional. Filters traffic on your device — no remote server, no traffic leaves."* Mark the row "Optional" in the title.

`:ui` may not import `android.net.VpnService`... except it can, because `:ui` is an Android library. This is the one deliberate exception to "the UI goes through ports": `prepare()` must be called from an `Activity` context to produce a launchable consent `Intent`, and no port can hand back an `Intent`. Keep the exception to exactly this one call and read the resulting *status* through `SystemAccess.permissions()`.

### Re-verification on resume

Every one of grants 2 through 5 leaves the app. `PermissionStatus` is a plain snapshot with no observability, so you must re-read it when the user comes back:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
var status by remember { mutableStateOf(UiPorts.value.system.permissions()) }
DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) status = UiPorts.value.system.permissions()
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
}
```

Without this the rows stay grey after a successful grant and the flow feels broken. Also re-read immediately in each launcher's result callback, since the notification dialog does not always produce an `ON_RESUME`.

### Completion

Continue and Skip both write `settings.onboardingComplete = true` through `lifeState.mutate`, then call `onDone()`. S0's nav graph reads that flag to decide between onboarding and the tab scaffold.

Write the flag through `lifeState.mutate` rather than an `Action` — `onboardingComplete` is UI-local state with no enforcement consequence, and there is no action type for it. This is the one sanctioned direct `mutate` from `:ui`.

### Re-entry from More

S7's More screen navigates back here to review grants. Make the screen correct when everything is already granted: all five rows show "Granted", and Continue reads "Done".

---

## Verification

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity
```

Cap yourself at two or three Gradle builds; seven other sessions share the lock. On `Timeout waiting to lock`, wait 20 seconds and retry.

To reset onboarding between attempts:

```bash
adb -s emulator-5554 shell pm clear com.lifeos.app
```

To confirm your rows react to externally granted permissions (useful while S3 is still building `SystemAccessImpl`):

```bash
adb -s emulator-5554 shell appops set com.lifeos.app GET_USAGE_STATS allow
adb -s emulator-5554 shell appops set com.lifeos.app SYSTEM_ALERT_WINDOW allow
```

Then background and foreground the app; both rows must flip to "Granted" without a restart.

Acceptance checklist:

- [ ] Onboarding is the first screen on a freshly cleared install
- [ ] All five rows render with correct ungranted styling and no crash when `SystemAccess` is still a stub returning all-false
- [ ] The notifications runtime dialog appears and the row updates from its result callback
- [ ] Each of the four Settings hand-offs opens the correct system screen
- [ ] Returning from any hand-off refreshes every row via `ON_RESUME`
- [ ] Continue is disabled until usage access **and** overlay are granted
- [ ] "Skip for now" always works and reaches the tab scaffold
- [ ] Selecting a persona persists — kill the app, reopen, and the choice survives
- [ ] Re-entering from More shows all-granted state with "Done"
- [ ] Every one of the seven components renders in isolation without a crash, including `TimeoutBar(45, 30)` clamping and `RiskBadge(0)` / `RiskBadge(100)`
- [ ] No component signature changed, or any change added a defaulted parameter only
- [ ] `./gradlew :ui:compileDebugKotlin` succeeds with no reference to `:domain`, `:agent`, `:email`, `:data`, or `:enforce`

## Timebox

80 minutes: components 35, onboarding 45.

Front-load the components. If you are at 40 minutes with components incomplete, ship rough versions and move to onboarding anyway — three agents are blocked on your visuals but they are blocked on *quality*, whereas the demo is blocked on *permissions existing at all*.

If behind at 65 minutes, cut in this order: the VPN row (leave it out; the network guard is already first on the sprint cut list), then the persona picker (default to Strict), then the disclosure copy. **Never** cut: usage access, overlay, or the `ON_RESUME` re-verification. Those three are what make enforcement real.

## Handoff notes for S9

State whether any component signature gained a defaulted parameter, and list which permission rows shipped. Confirm the `adb appops` commands S9 should run before recording so the demo never shows a Settings detour.
