---
title: "P0 — Production mail + Google Calendar + strip demo fixtures"
wave: production
parallel: false
blocked_by: "U7 calendar port (already landed)"
ai_credentials: "optional GOOGLE_OAUTH_CLIENT_ID in local.properties"
timebox: "3 hours"
---

# P0 — Real Gmail, IMAP, Google Calendar, and a production-clean app

> Inbox today always returns the same 7 hardcoded campus emails and ignores `MailAccount`. Calendar writes a local `ACCOUNT_TYPE_LOCAL` calendar that may never leave the phone. More still has "Load sample", "Reset demo data", and "Strict demo timeouts". This agent replaces those with production paths.

## Mission

1. **IMAP** that actually fetches INBOX for any provider (Gmail, iCloud, Fastmail, university).
2. **Gmail** as a first-class account kind: one-tap host/port preset, plus optional Google Sign-In if a client ID is configured.
3. **Google Calendar sync** that reaches `calendar.google.com` — by writing into the device's Google-account calendar so the system sync adapter pushes events. Keep the local LifeOS calendar as a fallback when no Google account exists.
4. **Strip dummy/demo surfaces from release builds.** Debug-only fixtures may remain behind `BuildConfig.DEBUG`.

## AI credentials

**None required for IMAP or CalendarContract Google-account write.**

Optional: `GOOGLE_OAUTH_CLIENT_ID` in `local.properties` enables Google Sign-In for Gmail API. If absent, Gmail still works via IMAP + app password.

---

## What is already true (do not redo)

- `MailboxSync.fetch(account: MailAccount?): Result<List<RawMessage>>`
- `EmailClassifier` is a real rule engine — keep it. It is not dummy.
- `MailKind { SEED, IMAP, GMAIL }` already exists.
- `CalendarPort` + `CalendarPortImpl` already create a **local** LifeOS calendar and upsert/delete via `SYNC_DATA1`.
- `Settings.calendarSyncEnabled`, `calendarId`, `dynamicColor` already exist with defaults.
- `Ports.calendar: CalendarPort?` already exists.
- `ImapMailboxSync` exists as a stub that returns `NotImplementedError` — replace the body.
- `SeedMailbox` / `SeedMailboxSync` exist — keep for **debug only**.
- `DemoSeed` + `MainActivity.handleDemo` are already `BuildConfig.DEBUG`-gated. Leave them.
- `OfflineFallbacks` is the LLM-down path, not dummy data. **Keep it.** Removing it makes a missing API key a hard crash.
- `DemoPackages` is used by enforcement to resolve "instagram" → a real package. **Keep aliases.** Remove only `SUBSTITUTES` silent swap in **release** (Instagram → YouTube was a hackathon emulator hack).

---

## Honest architecture (read this before coding)

### Mail

| Path | How | Needs |
| --- | --- | --- |
| IMAP (any host) | Jakarta Mail / Eclipse Angus over SSL, last-N messages | Host, port, username, **app password** stored in EncryptedSharedPreferences — never in `CanonicalLifeState` |
| Gmail via IMAP | Same, host `imap.gmail.com`, port 993 | Gmail address + [App Password](https://myaccount.google.com/apppasswords). Normal Google password will fail. |
| Gmail API | `users.messages.list` + `get` with OAuth | `GOOGLE_OAUTH_CLIENT_ID` + play-services-auth. **Optional.** If client id is blank, do not add a broken Sign-In button. |

**Do not put the IMAP password on `MailAccount`.** That object is serialized into DataStore as plaintext JSON. Add:

```kotlin
// Entities.kt — additive defaults only
data class MailAccount(
    val id: String,
    val kind: MailKind = MailKind.SEED,
    val address: String = "",
    val host: String = "",
    val port: Int = 993,
    val username: String = "",          // NEW, default ""
    val useSsl: Boolean = true,         // NEW
)

interface SecretsStore {
    fun llmConfig(): LlmConfig?
    suspend fun putMailSecret(accountId: String, secret: String)
    suspend fun getMailSecret(accountId: String): String?
    suspend fun deleteMailSecret(accountId: String)
}
```

Implement `EncryptedSecretsStore` in `:data` using `androidx.security.crypto.EncryptedSharedPreferences` (add `androidx.security:security-crypto` to the version catalog). `BuildConfigSecretsStore` can stay as the LLM half, or fold LLM + mail into one store.

`:email` is currently a **pure JVM** module. IMAP via Angus Mail can stay JVM. Gmail API + EncryptedSharedPreferences need Android — put those in `:email` after converting it to an Android library (`com.android.library`, minSdk 33), **or** put Gmail API in a new `:email-android` and keep IMAP in JVM. Prefer converting `:email` to an Android library so `AppContainer` wiring stays one module. `EmailClassifier` and tests must keep compiling.

**Composite mailbox** (what `AppContainer` binds):

```kotlin
class CompositeMailboxSync(
    private val imap: ImapMailboxSync,
    private val gmail: GmailMailboxSync?,   // null if no OAuth client
    private val seed: SeedMailboxSync,      // used only when BuildConfig.DEBUG && no accounts
) : MailboxSync {
    override suspend fun fetch(account: MailAccount?): Result<List<RawMessage>> {
        val target = account ?: /* first non-SEED account in state, passed by caller */
        return when (target?.kind) {
            MailKind.IMAP -> imap.fetch(target)
            MailKind.GMAIL -> gmail?.fetch(target) ?: imap.fetch(gmailImapPreset(target))
            MailKind.SEED, null ->
                if (debug && noRealAccounts) seed.fetch(null)
                else Result.success(emptyList())
        }
    }
}
```

`InboxViewModel.sync()` must pass the selected `MailAccount`, not `null`.

IMAP fetch rules:

- `Dispatchers.IO`, 20s connect timeout, SSL.
- Open `INBOX` read-only.
- Fetch the newest 50 messages (UID descending). Envelope + first 8KB of text body is enough for the classifier.
- Never log the password. On auth failure return `Result.failure` with a user-readable message ("App password rejected. Create one at myaccount.google.com/apppasswords").
- Unit-test `ImapMailboxSync` against a fake store if you can; otherwise extract host-preset + message mapping and test those.

Gmail IMAP preset: if `kind == GMAIL` and host is blank, use `imap.gmail.com:993`.

### Google Calendar

Two write targets, in this order, when `calendarSyncEnabled`:

1. **Google-account calendar on the device** (`ACCOUNT_TYPE = "com.google"`). Query `CalendarContract.Calendars` for the primary calendar (`IS_PRIMARY=1`) of a `com.google` account. Upsert LifeOS events there. The Google Calendar sync adapter pushes them to `calendar.google.com` and every other device. **This is the production path. It does not need OAuth.**
2. If no Google account calendar exists, fall back to the existing local `ACCOUNT_TYPE_LOCAL` "LifeOS" calendar.

Extend `CalendarPort` additively (defaulted methods so existing call sites compile):

```kotlin
interface CalendarPort {
    // existing methods stay
    suspend fun ensureGoogleCalendar(): Result<Long>   // primary com.google calendar, or failure
    fun googleAccountPresent(): Boolean
}
```

`upsert` should write to the Google primary calendar when present and `calendarSyncEnabled`, else local. Store which id you used in `Settings.calendarId`.

`readRange` already reads all visible calendars — keep that so Today shows the user's existing Google events.

**Do not start the Google Calendar REST API** unless `GOOGLE_OAUTH_CLIENT_ID` is set *and* IMAP-via-Gmail is already working. One OAuth surface is enough; CalendarContract is the reliable sync.

### Production strip (release builds)

Gate with `BuildConfig.DEBUG` from the **app** module, or pass an `isDebug: Boolean` into UI/viewmodels from `AppContainer` — `:ui` cannot see `com.lifeos.app.BuildConfig` unless you add a `debug` flag on `Ports` or `UiPorts`. Cleanest: add to `Ports`:

```kotlin
val isDebug: Boolean get() = false
```

`AppContainer` overrides `isDebug = BuildConfig.DEBUG`.

Then hide in **release**:

| Surface | Release behaviour |
| --- | --- |
| Inbox "Load sample" | Gone. Empty state is "Connect Gmail or IMAP" + Connect button. |
| Inbox account label "Seed mailbox" | Show connected address, or "No account". |
| More → Debug card (Test alarm, Reset demo) | Hidden. |
| More → "Strict demo timeouts" | Hidden. |
| `DemoPackages.SUBSTITUTES` silent Instagram→YouTube | Disabled in release (`AppCatalogImpl` / `ActionExecutor.resolvePackage`). Aliases stay. |
| Onboarding skip that marks everything granted | Keep skip (users must be able to enter the app) but do not pretend permissions are granted. |
| `usesCleartextTraffic` | `false` on the release manifest / `false` unless debug. |

Keep in **debug**:

- ADB `--es demo seed` / `fill_chat` / `--es say`
- Debug card on More
- Seed mailbox fetch when no real account is configured
- Offline interview fallback

---

## Files you own

You may edit these. Other UI agents may have just rewritten Inbox/More — **restyle nothing**. Add account-connect UI using existing `LifeOsCard` / `PrimaryButton` / `GhostButton`.

- `core/src/main/kotlin/com/lifeos/core/model/Entities.kt` — additive `MailAccount` fields only
- `core/src/main/kotlin/com/lifeos/core/Ports.kt` — `SecretsStore` mail methods, `Ports.isDebug`, `CalendarPort.ensureGoogleCalendar` / `googleAccountPresent`
- `core/src/main/kotlin/com/lifeos/core/CalendarPort.kt`
- `data/src/main/kotlin/com/lifeos/data/**` — encrypted mail secrets
- `email/**` — convert to Android library if needed; implement IMAP; optional Gmail API
- `calendar/src/main/kotlin/com/lifeos/calendar/CalendarPortImpl.kt` — Google-account target
- `app/src/main/kotlin/com/lifeos/app/AppContainer.kt` — wire composite mailbox + secrets
- `app/src/main/AndroidManifest.xml` — `GET_ACCOUNTS` not required on API 33 if using CalendarContract; do **not** add unused permissions
- `app/build.gradle.kts` + `gradle/libs.versions.toml` — Angus Mail, security-crypto, optional play-services-auth
- `ui/.../inbox/**` — connect account sheet, hide Load sample in release
- `ui/.../more/**` — hide debug in release; calendar section shows "Google Calendar (device account)" vs "Local only"
- `enforce/.../AppCatalogImpl.kt` — no SUBSTITUTES in release (pass `isDebug` or check applicationInfo.flags)
- `domain/.../ActionExecutor.kt` — no SUBSTITUTES in release if you can read a flag; otherwise leave domain pure and filter in `AppCatalogImpl.resolveOrSubstitute` only (preferred)

## Files you must NOT touch

`ui/theme/**`, `ui/components/**`, `ui/nav/**`, `ui/screens/chat/**`, `ui/screens/today/**`, `ui/screens/goals/**`, `ui/screens/onboarding/**` (except adding calendar/mail permission rows if already present — prefer Inbox/More), `agent/**` except if `SystemPromptBuilder` must mention real inbox, `enforce/vpn/**`.

Do not rewrite the Focus/DNS work.

---

## Inbox / More UI (minimal, production-grade)

**Connect account sheet** (Inbox, when no IMAP/GMAIL account):

- SegmentedControl: Gmail / IMAP
- Gmail: email + app-password fields, helper text linking to app-password rules (plain text, not a webview). Save → `MailKind.GMAIL`, host/port filled, secret in `SecretsStore`, account in `lifeState.mailAccounts`.
- IMAP: email, host, port, username (default email), password. Same save path with `MailKind.IMAP`.
- PrimaryButton "Connect and sync" then `sync()`.
- Disconnect: deletes secret + account row.

**Never** show the password again after save.

More calendar copy:

- If `googleAccountPresent()`: "Events sync to the Google Calendar account on this phone."
- Else: "No Google account on this device — events stay in a local LifeOS calendar."

---

## Gradle notes

- Pin new libs in `libs.versions.toml`. Suggested: `org.eclipse.angus:angus-mail` (Jakarta Mail impl) + `jakarta.mail:jakarta.mail-api`. Do **not** use the abandoned `com.sun.mail:android-mail` if Angus resolves.
- `androidx.security:security-crypto:1.1.0-alpha06` or the latest stable that resolves from the Aliyun/Google mirrors already in `settings.gradle.kts`.
- Optional: `com.google.android.gms:play-services-auth` only if `GOOGLE_OAUTH_CLIENT_ID` wiring is actually used.
- `gradle.properties` pins JDK 21. Do not change repository config.
- Convert `:email` carefully: `email/build.gradle.kts` is currently `kotlin.jvm`. Mirror `:data`'s Android library block. Tests become `androidTest` or stay JVM if classifier stays JVM-testable — prefer keeping `EmailClassifier` unit tests on JVM via a `java` source set, or move assertions into `src/test` with Robolectric **only if forced**. Simplest: keep classifier tests as `test` on the Android library (they run on JVM).

---

## Acceptance criteria

- `./gradlew :app:assembleDebug :app:assembleRelease` both succeed.
- Release APK empty Inbox has **no** "Load sample" and **no** seed emails after Sync with zero accounts.
- Connecting a fake IMAP account fails with a readable error, not a crash.
- `MailAccount` JSON in DataStore contains **no** password field.
- With calendar permission + a Google account on the emulator, `ensureGoogleCalendar()` returns that calendar's id; a LifeOS event upserted there has `ACCOUNT_TYPE=com.google`.
- With no Google account, local LifeOS calendar still works (existing U7 path).
- More Debug card compiled out or hidden when `isDebug == false`.
- `DemoPackages.SUBSTITUTES` not used when `isDebug == false`.
- Existing persisted DataStore still deserializes (all new fields defaulted).
- `EmailClassifier` tests still pass.

## Handoff

Report: how to connect Gmail (app password vs OAuth), which calendar is written, what is debug-only, and the exact `Ports` additions.
