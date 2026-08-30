---
title: "LifeOS — Calm Dark OS UI redesign + calendar integration"
version: 1
supersedes: "lifeos_ui_technical_implementation.md (§1 design system only)"
waves: 3
subagents: 9
---

# LifeOS — "Calm Dark OS" UI redesign

> The functional app is built and running. This plan replaces its visual layer wholesale, fixes one crash that makes a whole tab unreachable, and adds two-way device-calendar integration so LifeOS events show up in Google Calendar.

## Decisions locked by the user

| Decision | Choice |
| --- | --- |
| Aesthetic | **Calm dark OS** — deep near-black, single mint accent, soft elevated cards, subtle gradients, translucent bars |
| Navigation | **Keep all 6 tabs**, redesign visuals only |
| Calendar | **New** — mirror LifeOS events into the device calendar provider so Google Calendar shows them; also read device events into Today |
| Scope | **Full** — design system, all 7 screens, motion, onboarding, enforcement overlay |
| Typography | **Bundle a font** as an app resource (Inter), fully offline |

---

## 1. Audit — why the current UI needs replacing, not tweaking

Verified by reading the source and screenshotting all six tabs on `emulator-5554`.

**The theme is effectively absent.**

- `LifeOsTypography = Typography()` — the literal Material 3 default. No custom font, no type scale, no tracking, no tabular figures for the timeline gutter.
- `Color.kt` is 8 flat colors. There is no tonal surface ramp, so every card sits at the same lightness as the page and the UI reads as one undifferentiated plane.
- `LifeOsTheme` overrides only 9 of ~30 `ColorScheme` slots. Everything unset — `secondaryContainer`, `surfaceVariant`, `outline`, `primaryContainer` — falls back to **stock Material purple**. This is why the selected bottom-nav pill renders mauve next to a mint accent.
- Design tokens are 4 spacing values and a single `LifeOsRadius = 8.dp`. No elevation model, no motion spec, no shape scale.

**Concrete defects visible on device.**

| Screen | Defect |
| --- | --- |
| All | Persona label `"Strict"` is a raw `Text` in `TopAppBar.actions` with no padding — it is **clipped by the screen edge** |
| All | Six `NavigationBarItem`s with always-on labels crowd the bar; M3 guidance is 3–5 |
| Chat | ~600dp of dead space below the first exchange; no hero, no empty state, no suggestions |
| Chat | Applied changes render as a hairline divider plus grey caption — the single most important agentic moment in the app is its least designed |
| Today | Unstyled flat list; no cards, no timeline rail, no "now" indicator; date nav is two bare chevrons |
| Goals | Risk shown as flat text; nothing visualises the goal → enforcement lineage that is the product's whole thesis |
| Inbox | Primary action is a bare `TextButton` reading "Load sample" |
| Focus | **Crashes the process on open** (see below) |
| Everywhere | Zero animation, no press feedback, no haptics, no light theme |

**The crash.** Tapping Focus kills the app:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: Key "com.android.camera2" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

Root cause is `AppCatalogImpl.launchableApps()`, which maps `PackageManager.queryIntentActivities` results straight to `InstalledApp`. A package may export **more than one launcher activity** (`com.android.camera2` exports both a normal and a secure-lockscreen camera entry), so the list contains duplicate `packageName`s. `WellbeingScreen.kt:219` then keys a `LazyColumn` on `"app:${it.packageName}"` and Compose throws. Fixed in Wave U0 at both layers: `distinctBy` at the source, defensive dedupe at the call site.

---

## 2. The design language

The organising idea: **depth through lightness, not shadow.** In a near-black UI, drop shadows are invisible mud. Hierarchy comes from a surface ramp plus 1px hairline borders, with the mint accent used sparingly enough that it always means "this is live" or "this is yours to press".

### 2.1 Color — FROZEN

Wave U0 writes this verbatim into `ui/src/main/kotlin/com/lifeos/ui/theme/Color.kt`. No later agent may change a value; new tokens may be added.

```kotlin
// ---- Surface ramp: elevation == lightness ----
val Backdrop   = Color(0xFF07090C) // behind everything, scrims
val Surface0   = Color(0xFF0B0E13) // page background
val Surface1   = Color(0xFF11151C) // cards, list rows
val Surface2   = Color(0xFF171C25) // raised cards, bottom sheets, nav bar
val Surface3   = Color(0xFF1E242F) // text fields, pressed states

// ---- Hairlines (never shadows) ----
val BorderSubtle = Color(0x0FFFFFFF) //  6% white
val BorderStrong = Color(0x1FFFFFFF) // 12% white

// ---- Accent: mint, carried over for continuity ----
val Mint300 = Color(0xFF5AF0BE) // hover / highlight
val Mint400 = Color(0xFF2EE6A6) // PRIMARY
val Mint600 = Color(0xFF12B482) // pressed
val MintInk  = Color(0xFF04140F) // text/icon on mint fill
val MintWash = Color(0x242EE6A6) // 14% — halos, tinted fills

// ---- Semantic ----
val Warn    = Color(0xFFF5A524)
val Danger  = Color(0xFFFF5C5C)
val Info    = Color(0xFF5B9DFF) // email + calendar
val Violet  = Color(0xFFA78BFA) // agent / AI moments

val WarnWash   = Color(0x1FF5A524)
val DangerWash = Color(0x1FFF5C5C)
val InfoWash   = Color(0x1F5B9DFF)
val VioletWash = Color(0x1FA78BFA)

// ---- Text ----
val TextPrimary   = Color(0xFFE8EEF5)
val TextSecondary = Color(0xFF97A3B2)
val TextTertiary  = Color(0xFF616C7A)
```

**Every `ColorScheme` slot must be assigned** so no Material purple can leak through. Notably `secondaryContainer = MintWash`, `onSecondaryContainer = Mint300`, `surfaceVariant = Surface3`, `outline = BorderStrong`, `outlineVariant = BorderSubtle`.

Two gradients, both deliberately low-contrast:

- `AgentGradient` — `Mint400 @ 18%` → `Violet @ 14%` → transparent, radial. Behind the chat hero and as a 1px top edge on assistant bubbles.
- `ScrimTop` / `ScrimBottom` — `Surface0` → transparent, 24dp tall. Lets scrolling content fade under the header and nav bar.

### 2.2 Typography — FROZEN

Bundle **Inter** static weights (Regular 400 / Medium 500 / SemiBold 600 / Bold 700) as `ui/src/main/res/font/inter_*.ttf`. Static weights rather than the variable font: `FontVariation` adds API-surface risk for zero visual gain at four fixed weights, and the offline guarantee matters more than 300KB.

| Style | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `displayMedium` | 30 / 36, −0.5 | Bold | Screen hero numbers, countdown |
| `headlineMedium` | 24 / 30, −0.3 | SemiBold | Screen titles in the header |
| `titleLarge` | 20 / 26 | SemiBold | Card titles, goal names |
| `titleMedium` | 17 / 22 | SemiBold | Row titles |
| `bodyLarge` | 16 / 24 | Regular | Chat text, body copy |
| `bodyMedium` | 14 / 20 | Regular | Snippets, subtitles |
| `bodySmall` | 13 / 18 | Regular | Captions |
| `labelLarge` | 14 / 18 | Medium | Buttons, chips |
| `labelMedium` | 12 / 16 | Medium | Pills, meta |
| `labelSmall` | 11 / 14, +1.0 | Medium | `SectionHeader` all-caps |

One addition beyond the M3 scale — the timeline gutter and every countdown must not jitter as digits change:

```kotlin
val TimeNumeric = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium,
    fontSize = 13.sp, lineHeight = 16.sp,
    fontFeatureSettings = "tnum", // tabular figures
)
```

### 2.3 Shape, spacing, elevation, motion — FROZEN

```kotlin
object R { // radius
    val xs = 8.dp; val sm = 12.dp; val md = 16.dp
    val lg = 20.dp; val xl = 28.dp; val full = 999.dp
}
// cards = lg, sheets = xl (top only), chips/pills = full, inputs = xl, bars = full

object S { // spacing
    val x1 = 4.dp;  val x2 = 8.dp;  val x3 = 12.dp; val x4 = 16.dp
    val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp; val x10 = 40.dp
}

object Motion {
    val emphasized = spring<Float>(dampingRatio = 0.80f, stiffness = 380f)
    val standard   = tween<Float>(220, easing = FastOutSlowInEasing)
    val enter      = tween<Float>(320, easing = LinearOutSlowInEasing)
    val navFade    = tween<Float>(180)
}
```

Elevation is a helper, not a shadow: `Modifier.surface(level)` applies the ramp color, the matching corner radius, and a `BorderSubtle` hairline. An `active` variant swaps the hairline for `Mint400 @ 40%` and adds a soft `MintWash` glow — used for a running focus session and the current timeline block.

**Motion rules.** Nav transitions fade-through with an 8dp slide, never a horizontal push. All numbers that change (XP, risk %, minutes remaining) use `animateIntAsState`. Progress bars and rings animate to their target. Every pressable scales to `0.97` on press and fires `HapticFeedbackType.TextHandleMove`. New list items enter with fade + scale-from-0.94.

**On glass/blur, honestly:** Compose has no backdrop blur. `Modifier.blur` blurs *its own* content, not what is behind it, and true backdrop blur needs a third-party library (`dev.chrisbanes.haze`) — which we are not adding, given this project has already fought Google Maven mirror failures. The translucent-bar effect is achieved with `Surface2 @ 92%` plus a `ScrimTop` gradient so content genuinely fades out under the bar. It reads as glass and costs zero dependencies.

---

## 3. Screen-by-screen redesign

### 3.1 App shell

Kill the generic `TopAppBar`. Each screen supplies a `LifeOsHeader(title, subtitle, actions)`: `headlineMedium` title, optional `bodyMedium` subtitle, and a **`PersonaPill`** on the right — a `full`-radius `MintWash` chip with a mint status dot and the persona name, properly padded and inset from the edge. This fixes the clipped "Strict" and makes persona switching discoverable (tap opens the persona sheet).

`LifeOsNavBar` replaces `NavigationBar`. Six items, `Surface2 @ 92%` with a `BorderSubtle` top hairline. The crowding fix: **only the selected item shows its label**, inside an animated `MintWash` pill that expands horizontally via `Motion.emphasized`; unselected items are icon-only at `TextTertiary`. Six icons breathe comfortably where six labelled items do not.

### 3.2 Chat — the flagship screen

The 600dp of dead space becomes the **agent hero**: a centered `AgentGradient` radial glow behind an `AutoAwesome` glyph, the prompt "What should we change today?", and a 2×2 grid of suggestion cards (icon + title + one-line subtitle) wired to real capabilities — *Crack Google interview*, *Start a 50-min focus block*, *Cap Instagram at 30m*, *Read my inbox*. The hero cross-fades out once the transcript has messages.

Bubbles: user = `Mint400` fill with `MintInk` text; assistant = `Surface1` with a hairline and a 1px `AgentGradient` top edge. Both `R.lg` with the tail corner tightened to `R.xs`.

`AppliedChangesCard` replaces the divider-and-caption treatment. A nested `Surface2` block with an `AutoAwesome` + "Applied 3 changes" header, then a `FlowRow` of chips tinted per `ChangeKind` — mint for `FOCUS`/`TIMEOUT`, `Info` for `EMAIL`, `Violet` for `MEMORY`/`PERSONA`, `Warn` for `ALARM`. Each chip navigates to the screen that owns the change. This is the moment that proves the agent acted; it should look like the most expensive thing in the app.

Composer: `Surface3` pill at `R.xl`, multiline-growing to 5 lines, with a circular `Mint400` send button that scales and rotates on dispatch. Typing indicator is three mint dots on staggered `Motion.emphasized` scale.

### 3.3 Today

A **"Now" hero card** leads: current or next block, a live progress ring, a `TimeNumeric` countdown, and an inline Start Focus CTA. Below it, a horizontally scrollable **week strip** — seven day pills with weekday and date, today filled mint, days holding items marked with a dot — replacing the two bare chevrons.

The timeline becomes a proper rail: left gutter with `TimeNumeric` times, a vertical `BorderSubtle` connector, a kind-colored dot on the line, and a `Surface1` card to the right. A mint **"now" line** with a dot is injected at the correct chronological position. Completed items strike through, fade to 40%, and collapse. Device-calendar events (from U7) appear in the same rail tinted `Info` with a "Google Calendar" source label, visually distinct from LifeOS-owned items.

### 3.4 Goals

The goal card is where the product thesis becomes visible. Title and hardness pill on top; a **risk ring** (mint → warn → danger) replacing the flat percentage text; deadline as "29 Sep · 30 days left"; and — critically — a row of **lineage chips** showing what this goal spawned: `Caps: YouTube 45m`, `2 blocks`, `1 alarm`. Tapping the card expands it to reveal the linked todos, blocks, and timeouts. Nothing else in the app communicates "a goal became enforcement" as directly.

Todos get a custom animated checkbox (mint circle fills, checkmark draws) and swipe-to-complete. An XP/streak card with an animated counter and flame streak sits at the top.

### 3.5 Inbox

Candidate cards with `Info` accent, sender monogram avatar, bold subject, two-line snippet, a confidence meter, and a kind pill colored per `CandidateKind` (`EXAM` danger, `DEADLINE` warn, `EVENT` info, `NOISE` tertiary). Actions become a filled mint **"Add to calendar"** and a ghost **"Dismiss"** — and with U7 landed, that button now genuinely writes to the device calendar. Collapsible Noise and Handled sections with counts.

### 3.6 Focus / Wellbeing

Crash fixed first, then: a **focus hero** that is either an idle Start ring with 25/50/90-minute presets, or an active state with a large countdown ring, the mode, blocked-app avatars, and an End button. Timeout caps get app monograms, a rounded mint→warn→danger track, and a lineage chip naming the source goal. The network guard becomes a segmented OFF / BLACKLIST / WHITELIST control with an animating shield. The app picker becomes a real modal bottom sheet with search and a sticky selected count. Any missing permission raises a `Warn` card at the top, because without grants enforcement silently no-ops.

### 3.7 More, Onboarding, and the enforcement overlay

**More** becomes grouped `Surface1` setting cards with icon rows and dividers, a persona carousel showing each persona's tone preview, the calendar-sync controls from U7, and a redesigned **"Compact chat" proof card** that animates the message count before → after (demo-critical). Debug collapses out of the way.

**Onboarding** becomes a full-bleed 5-step pager: a large glyph in a `MintWash` halo, title, body, one primary CTA, and progress dots. Permission rows animate grant → check. The final step lands on "You're set" with a lightweight mint particle burst drawn on `Canvas`.

**The overlay** in `:enforce` is XML, not Compose, and must be restyled by hand to match: `Backdrop @ 88%` scrim, a `Surface2` card, mint accent, the persona line, and a full-width "Back to work" button.

---

## 4. Calendar integration

The user's ask: *"integrate phone's calendar (maybe google cal) i.e. subscribe to lifeos calendar from google calendar as well."*

### What is actually achievable, and what is not

**Achievable, offline, no OAuth.** Create a calendar owned by LifeOS through `CalendarContract.Calendars` with `ACCOUNT_TYPE_LOCAL`, then insert LifeOS events into `CalendarContract.Events` against it. The Google Calendar app renders local provider calendars alongside synced ones, so LifeOS events appear there and in every other calendar app on the device. Reading works the same way in reverse, letting the agent schedule around existing commitments. Needs `READ_CALENDAR` / `WRITE_CALENDAR` runtime permissions.

**Not achievable in this scope.** Pushing a LifeOS calendar to *Google's servers* so it syncs across a user's devices requires either Google Calendar API OAuth or a publicly hosted ICS feed for URL subscription. Both need a backend and a consent screen. Flag as future work; do not start it.

**One caveat to verify on device, not assume:** local-account calendar visibility in the Google Calendar app has varied across versions, and some builds hide calendars whose account is `ACCOUNT_TYPE_LOCAL`. U7's acceptance criteria therefore require opening the actual Google Calendar app on the emulator and confirming the events render — with an ICS-export fallback if they do not.

### Architecture

A **new `:calendar` Gradle module** rather than folding this into `:enforce`. Calendar access is not enforcement, and a separate module lets U7 run fully parallel without contending for `:enforce` files that U5 and U6 are editing.

New port added to `:core` (additive only — the frozen API permits new declarations):

```kotlin
interface CalendarPort {
    fun permissions(): CalendarPermissionStatus
    suspend fun ensureLifeOsCalendar(): Result<Long>      // returns calendarId, idempotent
    suspend fun upsert(items: List<CalendarMirrorItem>): Result<Int>
    suspend fun delete(lifeOsIds: List<String>): Result<Int>
    suspend fun readRange(startMs: Long, endMs: Long): Result<List<ExternalEvent>>
}
```

`CalendarMirrorItem` carries the LifeOS id in the provider's `SYNC_DATA1` column, which is what makes upsert and delete idempotent across reinstalls without a local id map.

`Settings` gains two fields, both defaulted so `kotlinx.serialization` stays backward compatible with persisted state:

```kotlin
val calendarSyncEnabled: Boolean = false,
val calendarId: Long? = null,
```

`:domain`'s `ActionExecutor` mirrors `Event` and `ScheduleBlock` writes to `CalendarPort` **as a side effect after the atomic state mutation**, matching the existing execution model — never inside the mutation, and never allowed to fail the action.

---

## 5. Parallel execution

```
Wave U0 — BLOCKING, solo (~50 min)
└── U0  Design system + component library + crash fix
         ↓ tokens and component signatures freeze here
Wave U1 — PARALLEL, 7 agents (~80 min)
├── U1  App shell, nav bar, headers, transitions
├── U2  Chat
├── U3  Today
├── U4  Goals
├── U5  Inbox + Focus/Wellbeing
├── U6  More + Onboarding + overlay XML
└── U7  :calendar module + port + domain mirroring
Wave U2 — SOLO (~40 min)
└── U8  Integration, build, on-device screenshot QA, polish
```

### File ownership — no two agents share a file

| Agent | Owns |
| --- | --- |
| U0 | `ui/theme/**`, `ui/components/**`, `ui/res/font/**`, `enforce/system/AppCatalogImpl.kt` |
| U1 | `ui/nav/**`, `ui/shell/**` |
| U2 | `ui/screens/chat/**` |
| U3 | `ui/screens/today/**` |
| U4 | `ui/screens/goals/**` |
| U5 | `ui/screens/inbox/**`, `ui/screens/wellbeing/**` |
| U6 | `ui/screens/more/**`, `ui/screens/onboarding/**`, `enforce/res/layout/**` |
| U7 | `calendar/**`, `core/CalendarPort.kt`, `core/model/Calendar.kt`, `domain/ActionExecutor.kt`, `settings.gradle.kts`, `gradle/libs.versions.toml` |
| U8 | `app/**`, integration fixes anywhere |

### Freeze rules

1. **U0's tokens are immutable after Wave U0 lands.** Later agents may *add* a token; they may never change an existing value, because six agents are rendering against it concurrently.
2. **Component signatures are additive-only.** New parameters must carry defaults so existing call sites keep compiling.
3. **`:core` is additive-only.** U7 may add `CalendarPort` and new model types and may add defaulted fields to `Settings`. Nothing existing may change shape.
4. Only U7 touches `settings.gradle.kts` and the version catalog, so Gradle configuration never races.

### Risks

| Risk | Mitigation |
| --- | --- |
| Six agents redesign against tokens that turn out wrong | U0 is blocking and ships a Preview screen exercising every token and component before Wave U1 starts |
| Inter font files unavailable offline | U0 verifies the download first; falls back to defining the full type scale on `FontFamily.SansSerif`, which still fixes 90% of the typographic problem |
| Local calendar invisible in Google Calendar app | U7 must verify on the emulator and ships ICS export as fallback |
| Kotlin incremental-cache corruption, seen twice already in this project | U8 runs `./gradlew --stop && rm -rf */build .kotlin` before the integration build |
| Redesign regresses the 90-second demo path | U8 re-runs the full demo script and screenshots all six tabs as the exit gate |

## 6. Definition of done

- All six tabs open without crashing; Focus is reachable.
- No Material purple anywhere — every `ColorScheme` slot explicitly assigned.
- Inter renders across all screens; timeline and countdown digits do not jitter.
- Persona pill fully visible, not clipped, on a 1284×2778 screen.
- Chat shows the hero + suggestions when empty, and an `AppliedChangesCard` after an agent turn.
- Today shows the Now card, week strip, timeline rail, and a "now" line.
- Goals shows a risk ring and lineage chips.
- A LifeOS event created in chat appears in the device Google Calendar app.
- Every list animates insertion; every pressable gives press feedback.
- The 90-second demo script runs start to finish.
