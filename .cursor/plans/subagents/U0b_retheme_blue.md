---
title: "U0b — Retheme to blue Accent + Material You dynamic colour"
wave: 0b
parallel: false
blocked_by: "U0 (done)"
blocks: "U1, U2, U3, U4, U5, U6"
ai_credentials: none
timebox: "25 minutes"
---

# U0b — Blue accent and Material You

> U0 shipped a mint palette and it works. The accent is now blue, with Material You dynamic colour as an opt-in. You are catching this before Wave U1 starts, which is the only reason it is a 25-minute job instead of a rewrite of six screens — so land it cleanly and do not let it drift.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §2.1 and §2.1b (both revised).

## Mission

Rename the accent tokens to hue-neutral names, swap mint for a Material 3 tonal blue, delete the now-redundant `Info` colour, and add opt-in dynamic colour that changes the accent without destroying the surface ramp.

## AI credentials

**None.**

---

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/theme/**`
- `ui/src/main/kotlin/com/lifeos/ui/components/**`
- `ui/src/main/kotlin/com/lifeos/ui/screens/wellbeing/WellbeingScreen.kt` — **colour references only**, because the concurrent DNS work added ~13 lines there that may reference a renamed token. Change nothing else in that file; U5 owns its redesign.

## Files you must NOT touch

`ui/nav/**`, every other `ui/screens/**`, `core/**`, `domain/**`, `agent/**`, `email/**`, `enforce/**`, `app/**`, any `build.gradle.kts`.

---

## Task 1 — Rename the accent tokens

**Why not `Blue400`.** We have now changed the accent hue once. Naming tokens after the hue guarantees another rename next time, and a rename is precisely what breaks six agents compiling in parallel. Use semantic names that survive a palette change.

Apply this map across `ui/theme/**` and `ui/components/**`:

| Current | New | Notes |
| --- | --- | --- |
| `Mint300` | `AccentHigh` | now tone 90, used for text on accent containers |
| `Mint400` | `Accent` | the primary role |
| `Mint600` | `AccentDeep` | now tone 30, `primaryContainer` |
| `MintInk` | `AccentInk` | ink ON an accent fill |
| `MintWash` | `AccentWash` | 12% halo fill |
| — | `AccentVivid` | **new**, tone 60: rings, progress tracks, the "now" line |
| `Info`, `InfoWash` | **deleted** | see Task 3 |
| — | `Success`, `SuccessWash` | **new**: the old mint, retained |

## Task 2 — Swap in the tonal blue

Copy the values from master plan §2.1 verbatim. The important shift is not just hue: Material 3 dark themes use a **light** primary with **dark** ink on it, not a saturated fill with dark ink. That tonal relationship is what makes it read as Material You rather than as a dark theme that happens to have a blue button.

```kotlin
val AccentHigh  = Color(0xFFD7E3FF) // tone 90
val Accent      = Color(0xFFA8C7FA) // tone 80 — PRIMARY
val AccentVivid = Color(0xFF4C8DFF) // tone 60 — rings, progress, "now" line
val AccentDeep  = Color(0xFF28497A) // tone 30 — primaryContainer
val AccentInk   = Color(0xFF0A305F) // tone 20 — ON accent
val AccentWash  = Color(0x1F4C8DFF) // 12%
```

Also restate the semantic set: `Warn = 0xFFFFD8A8`, `Danger = 0xFFFFB4AB`, `Success = 0xFF5AF0BE`, `Violet = 0xFFC7A9FF`.

Update `AgentGradient` and `AgentGradientEdge` to run `AccentVivid @ 18%` → `Violet @ 14%` → transparent.

Update the `ColorScheme`: `primary = Accent`, `onPrimary = AccentInk`, `primaryContainer = AccentDeep`, `onPrimaryContainer = AccentHigh`, `inversePrimary = AccentDeep`, `secondaryContainer = AccentWash`, `onSecondaryContainer = AccentHigh`, `surfaceTint = Accent`. **Keep every other slot assigned** — U0 assigned all ~36 and none may regress to a Material default.

Fix the deprecated aliases so they still point somewhere real: `MdPrimary = Accent`, `MdOnPrimary = AccentInk`. Note that `Warn` and `Danger` are now *light* tonal colours, so `MdWarn`/`MdDanger` consumers that draw dark text on them will need checking — grep for them.

## Task 3 — Delete `Info`

`Info` was `#5B9DFF`. The accent is now `#A8C7FA`/`#4C8DFF`. They are the same hue family, so email and calendar affordances would look like primary actions — the distinction the colour existed to draw is gone.

Email, calendar, and device-calendar events move to `Success`. That keeps them clearly non-primary, gives the retired mint a second life, and keeps external calendar events visually distinct from LifeOS-owned items on the Today rail.

## Task 4 — Material You, without wrecking the surface ramp

`minSdk = 33`, so `dynamicDarkColorScheme(context)` needs no version guard.

**Do not adopt the dynamic scheme wholesale.** A full dynamic `ColorScheme` carries its own surface roles, which would overwrite `Surface0`–`Surface3` and flatten every card back onto the page — destroying the depth-through-lightness model the whole design rests on. Harvest only the accent roles:

```kotlin
@Composable
fun LifeOsTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (dynamicColor) {
        val dyn = dynamicDarkColorScheme(LocalContext.current)
        DarkColors.copy(
            primary = dyn.primary,
            onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer,
            onPrimaryContainer = dyn.onPrimaryContainer,
            secondary = dyn.secondary,
            tertiary = dyn.tertiary,
            surfaceTint = dyn.primary,
            // surfaces, outlines, text: deliberately NOT taken from dyn
        )
    } else DarkColors
    MaterialTheme(colorScheme = scheme, typography = LifeOsTypography, shapes = LifeOsShapes, content = content)
}
```

Keep `dynamicColor` defaulted to `false` and give the parameter a default so the existing `LifeOsTheme { }` call site in `MainActivity` keeps compiling — you may not edit `app/**`. U6 wires the toggle in More and reads it from settings; your job is only to accept the parameter.

**Off by default, on purpose.** Wallpaper-derived colour is unpredictable — the accent can land on brown or olive, and the demo depends on blue reading as "live". Branded blue is the default; Material You is a switch.

## Task 5 — Make the toggle actually mean something

Sweep `ui/components/**` for places that use the `Accent` *literal* where the meaning is "primary action". Those must read `MaterialTheme.colorScheme.primary` instead, or the dynamic-colour toggle will silently do nothing. Keep the literal `AccentVivid` for decorative rings and progress tracks, which should stay blue regardless of wallpaper. Note this rule in your handoff, because six screen agents are about to write hundreds of colour references.

## Acceptance criteria

- `./gradlew :app:assembleDebug` passes; `app/**` unmodified.
- `rg -n 'Mint|\bInfo\b' ui/src/main/kotlin` returns nothing but the word "mint" in a comment explaining `Success`.
- No `ColorScheme` slot regressed to a Material default.
- `DesignSystemPreview.kt` renders the new palette, including `AccentVivid` and `Success`.
- Install on `emulator-5554` and screenshot Chat, Today, Goals, and Focus: the accent reads blue, the selected nav pill is blue, and no mint remains except on completion and calendar affordances.
- Flipping `dynamicColor = true` in the Preview changes the accent and leaves `Surface0`–`Surface3` untouched.

## Handoff

Report the final token names and values, the `Info` → `Success` migration, the exact `LifeOsTheme` signature, and the "read `colorScheme.primary`, not `Accent`" rule for the six screen agents.
