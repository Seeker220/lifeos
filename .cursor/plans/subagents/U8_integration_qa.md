---
title: "U8 — Integration, build, on-device QA, polish"
wave: 2
parallel: false
blocked_by: "U0, U1, U2, U3, U4, U5, U6, U7"
ai_credentials: "Azure AI Foundry key optional — offline fallbacks must carry the demo"
timebox: "40 minutes"
---

# U8 — Integration and QA

> Seven agents just rewrote the UI in parallel against a frozen contract. Your job is to make it one coherent app, prove it on the emulator with screenshots, and confirm the 90-second demo still runs. You are the only agent allowed to fix anything, anywhere.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §6.

## Files you own

`app/**`, plus integration fixes anywhere they are needed.

---

## Build clean, first time

This project has corrupted its Kotlin incremental caches twice, throwing `IllegalStateException: Storage for [...] is already registered`. After seven agents touched the tree in parallel, assume it will happen again. Start from a clean state rather than debugging a phantom:

```bash
./gradlew --stop
rm -rf */build .kotlin .gradle/kotlin
./gradlew :app:assembleDebug
```

Note that `gradle.properties` pins `org.gradle.java.home=/home/sumit/app/android-studio/jbr` for JDK 21 — do not remove it. Google Maven is reached through the Aliyun mirror configured in `settings.gradle.kts` because `dl.google.com` was unreachable from this network; if U7's new module fails to resolve dependencies, that mirror is the first thing to check.

## Wire and reconcile

Wire U7's `CalendarPortImpl` into `AppContainer` alongside the existing ports, and add `:calendar` to `app/build.gradle.kts`. Then hunt the seams that parallel work always leaves:

- **Deprecated token aliases.** U0 kept `MdBg`, `MdPrimary`, `LifeOsRadius` and friends as aliases so untouched files kept compiling. Every screen has now been rewritten, so sweep for remaining references and delete the aliases once nothing uses them.
- **Nullable `CalendarPort` guards.** U3, U5, and U6 each guarded against U7 being absent. U7 has landed; confirm all three now resolve it and actually render their calendar affordances.
- **Duplicated components.** Where two agents needed the same widget and U0 had not published it, they may each have written one. Consolidate into `ui/components/` and delete the copies.
- **Padding and insets.** U1 replaced hard `Scaffold` padding with a composition local. Verify every screen consumes it: nothing hidden under the header, nothing unreachable under the nav bar, no double padding.
- **Leftover hardcoded colors.** `rg -n 'Color\(0x' ui/src/main/kotlin/com/lifeos/ui/screens` should come back empty; every color belongs to a token.

## On-device QA — the exit gate

Install and screenshot all six tabs at 1284×2778 on `emulator-5554`:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity --es demo seed
```

`MainActivity` handles the `demo` extra in both `onCreate` and `onNewIntent`, so seeding works whether or not the app is already running.

Check each item, and fix what fails:

| Check | Screen |
| --- | --- |
| Opens without crashing | all six |
| No Material purple anywhere | all — especially the selected nav pill |
| Persona pill fully visible, not clipped | all |
| Inter renders; timeline and countdown digits do not jitter | Today, Focus |
| Hero + four suggestions on empty transcript | Chat |
| `AppliedChangesCard` with navigating chips | Chat |
| Now card counts down; week strip; timeline rail; "now" line | Today |
| Risk ring animates; lineage chips present and navigating | Goals |
| Candidates show confidence and kind; both actions work | Inbox |
| Opens repeatedly without crashing; countdown ticks | Focus |
| Compact-chat proof animates and asserts nothing lost | More |
| Event created in chat appears in Google Calendar app | Calendar |

Then re-run the full 90-second demo script end to end. The redesign is not done if it broke the demo.

## Polish pass, with whatever time remains

In priority order: verify every list animates insertion and every pressable gives press feedback; confirm no dropped frames while scrolling Today and Chat; check that a mid-demo rotation or backgrounding does not lose state; and confirm the app still functions with `LlmConfig.usable == false`, since the offline fallbacks are what actually carry the demo.

## Acceptance criteria

- Clean build from scratch.
- All twelve QA checks pass, evidenced by screenshots.
- The 90-second demo script runs start to finish without a crash.
- No hardcoded colors and no deprecated token aliases remain.
- `LifeOS-demo.apk` refreshed at the repo root.
