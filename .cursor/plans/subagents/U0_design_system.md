---
title: "U0 — Design system, component library, crash fix"
wave: 0
parallel: false
blocked_by: none
blocks: "U1, U2, U3, U4, U5, U6"
ai_credentials: none
timebox: "50 minutes"
---

# U0 — Design system foundation

> Seven agents start the moment you finish, and every one of them renders against the tokens and component signatures you publish here. Whatever you commit becomes immutable. Get the tokens exactly right, get every component visually correct-enough, and do not leave a single `ColorScheme` slot unassigned.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §2.

## Mission

Replace the app's non-existent theme with a complete "Calm Dark OS" design system, rewrite the seven shared components against it, and fix the duplicate-key crash that makes the Focus tab unreachable.

## AI credentials

**None.**

---

## Files you own

- `ui/src/main/kotlin/com/lifeos/ui/theme/**`
- `ui/src/main/kotlin/com/lifeos/ui/components/**`
- `ui/src/main/res/font/**` (new)
- `enforce/src/main/kotlin/com/lifeos/enforce/system/AppCatalogImpl.kt` (crash fix only)

## Files you must NOT touch

`ui/nav/**`, any `ui/screens/**`, `core/**`, `domain/**`, `agent/**`, `email/**`, `app/**`, any `build.gradle.kts` except `ui/build.gradle.kts` if a font dependency genuinely requires it.

---

## Task 1 — Fix the Focus crash (do this first, it is 5 minutes)

Tapping Focus currently kills the process:

```
java.lang.IllegalArgumentException: Key "com.android.camera2" was already used.
```

`AppCatalogImpl.launchableApps()` maps `PackageManager.queryIntentActivities` straight to `InstalledApp`. A package can export multiple launcher activities — `com.android.camera2` exports a normal and a secure-lockscreen entry — so the returned list holds duplicate `packageName`s, and `WellbeingScreen` keys a `LazyColumn` on package name.

Fix at the source, in `AppCatalogImpl`:

- `distinctBy { it.packageName }` before returning.
- Sort by `label.lowercase()` so the list is stable and alphabetical.
- Filter out our own package (`context.packageName`) — offering to block LifeOS with LifeOS is silly.

Do **not** edit `WellbeingScreen` (U5 owns it). Note in your handoff that U5 should also dedupe defensively.

Verify: rebuild, launch, tap Focus, confirm the tab renders.

## Task 2 — Bundle Inter

Fetch Inter static weights 400/500/600/700 into `ui/src/main/res/font/` as `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf` (Android resource names must be lowercase with underscores). Inter is SIL Open Font License, so bundling is fine; drop the license text at `ui/src/main/res/font/OFL.txt`.

**If the download fails**, do not burn time on it: define the full type scale on `FontFamily.SansSerif` instead and flag it in your handoff. The custom scale, tracking, and tabular figures deliver most of the improvement; the typeface is the smaller half.

## Task 3 — Write the tokens

`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt`, `Elevation.kt`, `Theme.kt`. Copy the values from §2.1–2.3 of the master plan **verbatim** — they are frozen and other agents are about to depend on them.

Three things that are easy to get wrong and matter:

**Assign every `ColorScheme` slot.** The current theme sets 9 of ~30, which is why the selected nav pill renders Material purple. Set all of them, explicitly, including `secondaryContainer`, `onSecondaryContainer`, `tertiaryContainer`, `surfaceVariant`, `surfaceContainer*`, `outline`, `outlineVariant`, `inverseSurface`, `scrim`, and every `on*` pair. Grep your own output for any slot you skipped.

**Keep the old token names as deprecated aliases.** `MdBg`, `MdSurface`, `MdPrimary`, `MdDanger`, `MdWarn`, `MdOnSurface`, `MdOnSurfaceVariant`, `LifeOsRadius`, and `LifeOsSpacing` are referenced across seven existing screen files that you are not allowed to edit. If you delete them, nothing compiles and all seven agents start from a broken tree. Alias them to the new tokens:

```kotlin
@Deprecated("Use Surface0", ReplaceWith("Surface0")) val MdBg = Surface0
```

**Ship the elevation helper**, since it is how every card in the app will be drawn:

```kotlin
fun Modifier.surface(
    level: Int = 1,
    radius: Dp = R.lg,
    active: Boolean = false,
): Modifier
```

Applies the ramp color for `level` (1→`Surface1`, 2→`Surface2`, 3→`Surface3`), clips to `radius`, and draws a 1px hairline — `BorderSubtle` normally, `Mint400 @ 40%` plus a `MintWash` glow when `active`.

## Task 4 — Rewrite the shared components

The seven existing signatures are **frozen**; change bodies freely, and add new parameters only with defaults:

```kotlin
@Composable fun ActionChipRow(chips: List<AppliedChange>, onChipClick: (AppliedChange) -> Unit)
@Composable fun RiskBadge(percent: Int)
@Composable fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit)
@Composable fun TimeoutBar(label: String, usedMinutes: Int, limitMinutes: Int, sourceLabel: String?)
@Composable fun EmptyState(title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun SectionHeader(text: String)
@Composable fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
```

Redesign notes: `ActionChipRow` chips tint per `ChangeKind` (mint for `FOCUS`/`TIMEOUT`, `Info` for `EMAIL`, `Violet` for `MEMORY`/`PERSONA`, `Warn` for `ALARM`) — and drop the current hard `truncated(28)` character chop in favour of `maxLines = 1` with ellipsis, which respects actual available width. `RiskBadge` keeps the pill but gains a matching `RiskRing(percent, size)` sibling for U4. `TimeoutBar` gains an app monogram, a `R.full` track, and the source rendered as a lineage chip. `EmptyState` gains a layered-rounded-rectangle glyph so empty screens stop looking broken. `AppToggleRow` gets a real app icon via `PackageManager` with monogram fallback, and a mint-tinted `Switch`.

Also publish these **new** components, because multiple Wave U1 agents need them and duplicated implementations will drift:

```kotlin
@Composable fun LifeOsCard(modifier: Modifier = Modifier, level: Int = 1, active: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)
@Composable fun Pill(text: String, color: Color, icon: ImageVector? = null)
@Composable fun LineageChip(sourceLabel: String)
@Composable fun RiskRing(percent: Int, size: Dp = 56.dp, strokeWidth: Dp = 5.dp)
@Composable fun ProgressRing(progress: Float, size: Dp = 56.dp, strokeWidth: Dp = 5.dp, color: Color = Mint400, content: @Composable (() -> Unit)? = null)
@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null)
@Composable fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)
@Composable fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit)
@Composable fun AnimatedCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit)
@Composable fun MonogramAvatar(text: String, color: Color = Mint400, size: Dp = 36.dp)
@Composable fun ConfidenceMeter(confidence: Double)
@Composable fun Modifier.pressable(onClick: () -> Unit): Modifier  // scale 0.97 + haptic
@Composable fun ScrimEdge(top: Boolean)
```

## Acceptance criteria

- Focus tab opens without crashing.
- `rg -n 'Color\(0xFF' ui/src/main/kotlin/com/lifeos/ui/screens` returns nothing new that you introduced, and no `ColorScheme` slot is left at its Material default.
- The whole project still compiles: `./gradlew :app:assembleDebug`. All seven untouched screen files must build against your deprecated aliases.
- A `@Preview` composable (`ui/theme/DesignSystemPreview.kt`) renders every token and every component in one scrollable column.
- Inter renders, or the fallback is flagged.

## Handoff

Report: the exact final token names, the full list of component signatures including the new ones, whether Inter landed or fell back, and the note for U5 about defensive deduping.
