---
title: "U7 — :calendar module, device calendar mirroring"
wave: 1
parallel: true
blocked_by: "U0 (nominally — you barely touch UI, so you may start immediately)"
ai_credentials: none
timebox: "85 minutes"
---

# U7 — Device calendar integration

> You are the only agent adding a feature rather than restyling one. LifeOS events must show up in the user's Google Calendar app. You are also the only agent permitted to touch `settings.gradle.kts`, the version catalog, `:core`, and `:domain` — so nothing races you, and you must not break the seven agents compiling against `:core` while you work.

Design reference: [`../lifeos_ui_redesign.md`](../lifeos_ui_redesign.md) §4.

## Files you own

- `calendar/**` (new module)
- `core/src/main/kotlin/com/lifeos/core/CalendarPort.kt` (new)
- `core/src/main/kotlin/com/lifeos/core/model/Calendar.kt` (new)
- `core/src/main/kotlin/com/lifeos/core/model/Entities.kt` — **only** to add two defaulted fields to `Settings`
- `domain/src/main/kotlin/com/lifeos/domain/ActionExecutor.kt`
- `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts` (dependency wiring only)

## Files you must NOT touch

Any `ui/**` (U0–U6 own all of it), `agent/**`, `email/**`, `enforce/**`, anything else in `core/` or `domain/`.

---

## Scope: what to build, and what to refuse to build

**Build.** A calendar owned by LifeOS in the device calendar provider — `CalendarContract.Calendars` with `ACCOUNT_TYPE_LOCAL` — plus LifeOS events inserted into `CalendarContract.Events` against it. The Google Calendar app renders local provider calendars alongside synced ones, so events appear there and in every other calendar app on the device. Read the other direction too, so the agent can schedule around existing commitments.

**Refuse.** Pushing a LifeOS calendar up to *Google's servers* for cross-device sync. That needs Google Calendar API OAuth or a publicly hosted ICS feed for URL subscription, both of which need a backend and a consent screen. Out of scope. If asked, the answer is "local provider calendar, visible in Google Calendar on this device".

**Verify, do not assume.** Local-account calendar visibility inside the Google Calendar app has varied across versions, and some builds hide `ACCOUNT_TYPE_LOCAL` calendars by default or until the calendar is enabled in its settings. Your acceptance criteria require **actually opening the Google Calendar app on `emulator-5554` and seeing the event.** If it does not appear, ship the ICS-export fallback described below and report it plainly.

## The port

New file in `:core` — additive only, so nothing existing changes shape:

```kotlin
interface CalendarPort {
    fun permissions(): CalendarPermissionStatus
    suspend fun ensureLifeOsCalendar(): Result<Long>   // idempotent, returns calendarId
    suspend fun upsert(items: List<CalendarMirrorItem>): Result<Int>
    suspend fun delete(lifeOsIds: List<String>): Result<Int>
    suspend fun readRange(startMs: Long, endMs: Long): Result<List<ExternalEvent>>
}
```

Every method returns `Result` — calendar provider access fails in ordinary ways (revoked permission, no provider, OEM weirdness) and none of them may crash the app or fail a user's action.

`CalendarMirrorItem` must carry the originating LifeOS id, written into the provider's `SYNC_DATA1` column. That is what makes `upsert` and `delete` idempotent without maintaining a local id map, and what stops a reinstall from duplicating every event. Query by `SYNC_DATA1` to decide insert versus update.

`Settings` gains exactly two fields, both defaulted so `kotlinx.serialization` still parses state already persisted on the device:

```kotlin
val calendarSyncEnabled: Boolean = false,
val calendarId: Long? = null,
```

Adding non-defaulted fields here would fail to deserialize existing `DataStore` content and wipe the demo state. Defaults are mandatory.

## The module

A new `:calendar` Gradle module rather than folding this into `:enforce`: calendar access is not enforcement, and a separate module means you never contend for files U5 and U6 are editing.

Mirror the existing module conventions exactly — `compileSdk = 36`, `minSdk = 33`, Java 21, `api(project(":core"))`. Add `READ_CALENDAR` and `WRITE_CALENDAR` to the module manifest; both are runtime permissions in the `CALENDAR` group and must be requested, not merely declared.

`CalendarPortImpl(context)` does all provider work on `Dispatchers.IO` behind `runCatching`. Two details that bite:

- Inserting an event **requires** `CALENDAR_ID`, `DTSTART`, and `EVENT_TIMEZONE`. Omitting the timezone throws.
- Creating a calendar row requires the `ACCOUNT_NAME`, `ACCOUNT_TYPE`, and `CALLER_IS_SYNCADAPTER=true` query parameters on the insert URI, or the provider silently rejects it.

Reuse the project's existing ISO-8601 helpers in `core/Time.kt` for conversion rather than writing new date parsing.

## Domain mirroring

`ActionExecutor` mirrors `Event` and `ScheduleBlock` writes to `CalendarPort`. Respect the existing execution model that the rest of `:domain` follows: **one atomic state mutation, then side effects.** The calendar write is a side effect. It happens after the mutation, never inside it, and a failure is logged via `LifeOsLog` and swallowed — a dead calendar provider must never fail the user's action or roll back their state.

Gate every mirror call on `settings.calendarSyncEnabled`. Mirror deletions too, or reverting an agent expansion leaves orphaned events in the user's real calendar, which is the kind of bug that makes people uninstall.

## ICS fallback

If Google Calendar will not display the local calendar, implement `exportIcs(): String` generating a VCALENDAR from LifeOS events and share it via a `FileProvider` intent. Less elegant, still demonstrates the integration, and is a 20-minute job. Do not start it unless the primary path fails verification.

## Acceptance criteria

- `./gradlew :app:assembleDebug` passes with the new module wired in.
- Existing persisted `DataStore` state still deserializes — install over the running build, do not uninstall first, and confirm the seeded goal survives.
- `ensureLifeOsCalendar()` is idempotent: called twice, one calendar exists.
- An event created through chat appears in the **Google Calendar app** on the emulator.
- Deleting the LifeOS event removes it from the device calendar.
- `readRange` returns device events, ready for U3 to render.
- With `calendarSyncEnabled = false`, zero provider writes occur.
- Revoking calendar permission mid-session degrades gracefully with no crash.

## Handoff

Report the exact nullable-lookup mechanism U3, U5, and U6 should use to reach `CalendarPort`, since all three guard against your absence.
