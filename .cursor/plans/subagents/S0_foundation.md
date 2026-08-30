---
title: "S0 — Foundation & Contracts"
wave: 0
parallel: false
blocks: "S1 S2 S3 S4 S5 S6 S7 S8 S9"
ai_credentials: none
timebox: "40 minutes"
---

# S0 — Foundation & Contracts

> **Read this first.** You are the blocking gate for the entire build. Eight other sessions start the moment you finish and they all compile against the API you are about to write. Correctness and *stability* of signatures matter more than completeness of behaviour. Write stubs freely; write the `:core` API exactly as specified.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Create an 8-module Gradle project that **assembles, installs, and launches**, containing the complete frozen `:core` API, working DataStore persistence, a complete `AndroidManifest.xml`, and a compiling no-op stub for every port and every screen. Nobody after you should have to create a build file, a manifest entry, or a shared component signature.

## AI credentials

**None.** You do not call any model. You only create the `LlmConfig` data class and the `BuildConfig` fields that will carry the key later.

---

## Files you own

Everything in this list, exclusively:

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/**`, `.gitignore`
- `core/**`, `data/**`, `app/**`
- `domain/build.gradle.kts`, `agent/build.gradle.kts`, `email/build.gradle.kts`, `enforce/build.gradle.kts`, `ui/build.gradle.kts`
- Stub sources in `domain/`, `agent/`, `email/`, `enforce/`
- `enforce/src/main/AndroidManifest.xml`
- `ui/src/main/kotlin/com/lifeos/ui/theme/**`, `.../nav/**`, `.../components/**` (stubs only), the seven placeholder screen files, `.../UiPorts.kt`
- `ui/src/main/AndroidManifest.xml`, `ui/src/main/res/**`

## Files you must NOT create

Do not write real implementations in `domain/`, `agent/`, `email/`, or `enforce/` beyond the stubs specified below. Do not write real screen bodies. Those belong to S1–S8 and duplicating them causes merge pain.

---

## Step 1 — Start the dependency download immediately (minute 0)

Nothing is cached for Compose, DataStore, OkHttp, coroutines, or serialization. The first resolution is a multi-minute download, so kick it off before writing Kotlin.

Create, in this order: `gradle.properties`, `settings.gradle.kts`, `gradle/libs.versions.toml`, the root `build.gradle.kts`, and `app/build.gradle.kts` with a minimal Compose dependency block. Generate the wrapper for **Gradle 8.14.3** (that distribution is already in `~/.gradle/wrapper/dists`, so the wrapper itself will not download). Then run a background resolve while you continue writing `:core`:

```bash
./gradlew --version
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

### `gradle.properties` — the JDK pin is mandatory

System Java is 25 and AGP 8.13.2 rejects it. Without this file every session fails identically on its first build.

```properties
org.gradle.java.home=/home/sumit/app/android-studio/jbr
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

Leave configuration cache **off**. It interacts badly with eight concurrent builds and debugging it is not a good use of sprint time.

### `local.properties`

Must contain the SDK location, and should contain empty placeholders so `app/build.gradle.kts` never reads a missing key:

```properties
sdk.dir=/home/sumit/Android/Sdk
AZURE_LLM_ENDPOINT=
AZURE_LLM_DEPLOYMENT=
AZURE_LLM_API_KEY=
AZURE_LLM_API_VERSION=2024-10-21
```

### `.gitignore`

Must include at least: `local.properties`, `.gradle/`, `build/`, `*/build/`, `.idea/`, `*.apk`, `.kotlin/`, `captures/`.

### `gradle/libs.versions.toml`

Pin exactly these. **No subagent may add a dependency later** — an unexpected download stalls seven other sessions.

- `agp = "8.13.2"` (already in the Gradle cache)
- `kotlin = "2.2.20"` (already in the Gradle cache)
- `composeBom = "2025.09.00"`
- `coroutines = "1.9.0"`
- `serialization = "1.7.3"`
- `datastore = "1.1.1"`
- `coreKtx = "1.13.1"`
- `activityCompose = "1.9.3"`
- `lifecycle = "2.8.7"`
- `navigationCompose = "2.8.4"`
- `okhttp = "4.12.0"`
- `junit = "4.13.2"`

Plugins: `android-application`, `android-library`, `kotlin-android`, `kotlin-jvm`, `kotlin-serialization`, `compose-compiler` (`org.jetbrains.kotlin.plugin.compose`, version `kotlin`).

Libraries needed: `kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `kotlinx-coroutines-test`, `kotlinx-serialization-json`, `androidx-datastore-preferences`, `androidx-core-ktx`, `androidx-activity-compose`, `androidx-lifecycle-runtime-ktx`, `androidx-lifecycle-viewmodel-compose`, `androidx-navigation-compose`, `compose-bom`, `compose-ui`, `compose-ui-graphics`, `compose-ui-tooling-preview`, `compose-material3`, `compose-material-icons-extended`, `okhttp`, `junit`.

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LifeOS"
include(":core", ":domain", ":agent", ":email", ":data", ":enforce", ":ui", ":app")
```

### Module build files

Shared Android settings: `compileSdk = 36`, `minSdk = 33`, `targetSdk = 36`, `jvmTarget = "21"`, `sourceCompatibility/targetCompatibility = VERSION_21`.

`minSdk = 33` is chosen deliberately: it removes runtime version guards around `POST_NOTIFICATIONS` and exact alarms. Only two API-34 guards remain, both in `:enforce`.

- `:core` — `kotlin-jvm` + `kotlin-serialization`. Deps: `kotlinx-coroutines-core`, `kotlinx-serialization-json`. Test dep: `junit`, `kotlinx-coroutines-test`.
- `:domain` — `kotlin-jvm` + `kotlin-serialization`. Deps: `api(project(":core"))`. Tests: `junit`, `kotlinx-coroutines-test`.
- `:agent` — `kotlin-jvm` + `kotlin-serialization`. Deps: `api(project(":core"))`, `okhttp`. Tests: `junit`, `kotlinx-coroutines-test`.
- `:email` — `kotlin-jvm` + `kotlin-serialization`. Deps: `api(project(":core"))`. Tests: `junit`, `kotlinx-coroutines-test`.
- `:data` — `android-library` + `kotlin-android` + `kotlin-serialization`. Namespace `com.lifeos.data`. Deps: `api(project(":core"))`, `androidx-datastore-preferences`, `androidx-core-ktx`, `kotlinx-coroutines-android`.
- `:enforce` — `android-library` + `kotlin-android`. Namespace `com.lifeos.enforce`. Deps: `api(project(":core"))`, `androidx-core-ktx`, `kotlinx-coroutines-android`. `buildFeatures { viewBinding = false }` — the overlay is inflated by hand.
- `:ui` — `android-library` + `kotlin-android` + `compose-compiler`. Namespace `com.lifeos.ui`. `buildFeatures { compose = true }`. Deps: `api(project(":core"))`, the Compose BOM plus `compose-ui`, `compose-ui-graphics`, `compose-ui-tooling-preview`, `compose-material3`, `compose-material-icons-extended`, `androidx-activity-compose`, `androidx-lifecycle-viewmodel-compose`, `androidx-navigation-compose`. **Must not depend on `:domain`, `:agent`, `:email`, `:data`, or `:enforce`.**
- `:app` — `android-application` + `kotlin-android` + `compose-compiler`. `applicationId = "com.lifeos.app"`, namespace `com.lifeos.app`. Depends on **all seven**. `buildFeatures { compose = true; buildConfig = true }`.

`app/build.gradle.kts` reads `local.properties` and emits four BuildConfig fields, tolerating absent keys:

```kotlin
val localProps = java.util.Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(k: String, default: String = "") = (localProps.getProperty(k) ?: default).trim()
// inside defaultConfig:
buildConfigField("String", "AZURE_LLM_ENDPOINT",   "\"${prop("AZURE_LLM_ENDPOINT")}\"")
buildConfigField("String", "AZURE_LLM_DEPLOYMENT", "\"${prop("AZURE_LLM_DEPLOYMENT")}\"")
buildConfigField("String", "AZURE_LLM_API_KEY",    "\"${prop("AZURE_LLM_API_KEY")}\"")
buildConfigField("String", "AZURE_LLM_API_VERSION","\"${prop("AZURE_LLM_API_VERSION", "2024-10-21")}\"")
```

---

## Step 2 — `:core`, the frozen API

Package root `com.lifeos.core`, source dir `core/src/main/kotlin/com/lifeos/core/`. **Zero `android.*` imports** — the module type enforces this, which is exactly why `:core` is a JVM library.

Write these signatures verbatim. After you finish, they are immutable for the rest of the sprint.

### `model/Entities.kt`

```kotlin
@Serializable enum class Hardness { SOFT, HARD }
@Serializable enum class EntitySource { USER, AGENT, EMAIL, SEED }
@Serializable enum class BlockKind { STUDY, GYM, DEEP_WORK, OTHER }
@Serializable enum class FocusMode { WHITELIST, BLACKLIST }
@Serializable enum class NetworkMode { OFF, BLACKLIST, WHITELIST }
@Serializable enum class ChatRole { USER, ASSISTANT, SYSTEM }
@Serializable enum class MailKind { SEED, IMAP, GMAIL }
@Serializable enum class CandidateKind { EXAM, DEADLINE, EVENT, NOISE }
@Serializable enum class CandidateStatus { PENDING, PROMOTED, DISMISSED }

@Serializable data class Goal(
    val id: String,
    val title: String,
    val deadlineIso: String? = null,
    val hardness: Hardness = Hardness.SOFT,
    val createdAtIso: String = "",
    val archived: Boolean = false,
    val notes: String = "",
)

@Serializable data class Todo(
    val id: String,
    val title: String,
    val goalId: String? = null,
    val dueIso: String? = null,
    val estMinutes: Int = 30,
    val done: Boolean = false,
    val completedAtIso: String? = null,
    val sourceGoalId: String? = null,
)

@Serializable data class Event(
    val id: String,
    val title: String,
    val startIso: String,
    val endIso: String? = null,
    val hardness: Hardness = Hardness.HARD,
    val source: EntitySource = EntitySource.USER,
    val emailId: String? = null,
    val sourceGoalId: String? = null,
)

/** daysOfWeek uses ISO numbering: 1 = Monday .. 7 = Sunday. */
@Serializable data class Habit(
    val id: String,
    val title: String,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val timeHhmm: String = "19:00",
    val remindMinutesBefore: Int? = null,
    val completedDates: List<String> = emptyList(),
    val sourceGoalId: String? = null,
)

/** Recurring when daysOfWeek is non-empty; one-off when dateIso is set. */
@Serializable data class ScheduleBlock(
    val id: String,
    val title: String,
    val startHhmm: String,
    val endHhmm: String,
    val kind: BlockKind = BlockKind.OTHER,
    val daysOfWeek: List<Int> = emptyList(),
    val dateIso: String? = null,
    val sourceGoalId: String? = null,
)

/** triggerAtEpochMs null means "next occurrence of timeHhmm". */
@Serializable data class AlarmSpec(
    val id: String,
    val label: String,
    val timeHhmm: String,
    val triggerAtEpochMs: Long? = null,
    val personaLine: String = "",
    val enabled: Boolean = true,
    val sourceGoalId: String? = null,
)

@Serializable data class AppTimeout(
    val packageName: String,
    val limitMinutes: Int,
    val sourceGoalId: String? = null,
)

@Serializable data class FocusWindow(
    val daysOfWeek: List<Int>,
    val startHhmm: String,
    val endHhmm: String,
    val mode: FocusMode,
    val packages: List<String>,
    val sourceGoalId: String? = null,
)

@Serializable data class FocusRules(
    val active: Boolean = false,
    val mode: FocusMode = FocusMode.BLACKLIST,
    val packages: List<String> = emptyList(),
    val startedAtEpochMs: Long? = null,
    val endsAtEpochMs: Long? = null,
    val windows: List<FocusWindow> = emptyList(),
)

@Serializable data class NetworkRules(
    val mode: NetworkMode = NetworkMode.OFF,
    val packages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

@Serializable data class Settings(
    val chatWindowK: Int = 12,
    val autoScheduleHighConfidence: Boolean = false,
    val demoStrictTimeouts: Boolean = false,
    val onboardingComplete: Boolean = false,
)

@Serializable data class Gamification(
    val xp: Int = 0,
    val streakDays: Int = 0,
    val lastActiveDateIso: String? = null,
)

@Serializable data class MailAccount(
    val id: String,
    val kind: MailKind = MailKind.SEED,
    val address: String = "",
    val host: String = "",
    val port: Int = 993,
)

@Serializable data class RawMessage(
    val id: String,
    val from: String,
    val subject: String,
    val body: String,
    val receivedAtEpochMs: Long,
)

@Serializable data class EmailCandidate(
    val id: String,
    val messageId: String,
    val from: String,
    val subject: String,
    val snippet: String,
    val confidence: Double,
    val kind: CandidateKind,
    val proposedTitle: String,
    val proposedStartIso: String? = null,
    val proposedEndIso: String? = null,
    val status: CandidateStatus = CandidateStatus.PENDING,
)

@Serializable data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val atEpochMs: Long,
    val appliedChips: List<String> = emptyList(),
    val expansionGoalId: String? = null,
)
```

### `model/State.kt`

```kotlin
@Serializable data class CanonicalLifeState(
    val schemaVersion: Int = 1,
    val personaId: String = "strict",
    val memoryFacts: List<String> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val tasks: List<Todo> = emptyList(),
    val events: List<Event> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val scheduleBlocks: List<ScheduleBlock> = emptyList(),
    val alarms: List<AlarmSpec> = emptyList(),
    val appTimeouts: List<AppTimeout> = emptyList(),
    val focus: FocusRules = FocusRules(),
    val network: NetworkRules = NetworkRules(),
    val mailAccounts: List<MailAccount> = emptyList(),
    val emailCandidates: List<EmailCandidate> = emptyList(),
    val settings: Settings = Settings(),
    val gamification: Gamification = Gamification(),
)

@Serializable data class ChatTranscript(
    val messages: List<ChatMessage> = emptyList(),
    val summary: String = "",
)
```

### `model/Action.kt` — 25 members, exactly

`@Serializable sealed interface Action` with `@SerialName` values matching the wire strings. The `@SerialName` strings are the contract the model emits; do not rename them.

**Naming rule:** the Kotlin class name is the PascalCase form of the wire name, so `create_goal` becomes `Action.CreateGoal` and `set_app_timeout` becomes `Action.SetAppTimeout`. Five other agents write `Action.CompleteTask(...)`, `Action.FocusStart(...)`, `Action.RevertExpansion(...)` and so on directly, so this mapping must hold with no exceptions. Field names below are the Kotlin parameter names *and* the JSON keys.

```
create_goal(id: String? = null, title, deadlineIso: String? = null, hardness = SOFT, notes = "")
update_goal(id, title: String? = null, deadlineIso: String? = null, hardness: Hardness? = null, notes: String? = null)
archive_goal(id)
create_task(id: String? = null, title, goalId: String? = null, dueIso: String? = null, estMinutes = 30, sourceGoalId: String? = null)
complete_task(id: String? = null, title: String? = null)
create_event(id: String? = null, title, startIso, endIso: String? = null, hardness = HARD, emailId: String? = null, sourceGoalId: String? = null)
create_habit(id: String? = null, title, daysOfWeek = 1..7, timeHhmm = "19:00", remindMinutesBefore: Int? = null, sourceGoalId: String? = null)
complete_habit_today(id: String? = null, title: String? = null)
add_schedule_block(id: String? = null, title, startHhmm, endHhmm, kind = OTHER, daysOfWeek = emptyList(), dateIso: String? = null, sourceGoalId: String? = null)
remember(fact)
set_persona(personaId)
set_alarm(id: String? = null, label = "", timeHhmm, personaLine = "", triggerAtEpochMs: Long? = null, sourceGoalId: String? = null)
cancel_alarm(id: String? = null, label: String? = null)
set_app_timeout(packageName, limitMinutes, sourceGoalId: String? = null)
clear_app_timeout(packageName: String? = null, sourceGoalId: String? = null)
focus_start(mode: FocusMode? = null, packages: List<String>? = null, minutes: Int? = null)
focus_stop
focus_set_apps(mode, packages)
set_focus_windows(windows: List<FocusWindow>, sourceGoalId: String? = null)
network_set_mode(mode: NetworkMode)
network_set_apps(packages: List<String>)
promote_email(candidateId, titleOverride: String? = null, startIsoOverride: String? = null)
dismiss_email(candidateId)
revert_expansion(goalId)
award_xp(amount, reason = "")
```

`focus_stop` has no fields; declare it as `@Serializable @SerialName("focus_stop") data object FocusStop : Action`.

Every action that can originate from a goal expansion carries `sourceGoalId`. That single field powers "From: Crack Google interview" on the Wellbeing screen and one-shot `revert_expansion`.

### `model/Dto.kt` — non-persisted transfer objects

```kotlin
enum class ActionOrigin { AGENT, USER, EMAIL, SYSTEM }
enum class ChangeKind { GOAL, TASK, EVENT, HABIT, BLOCK, ALARM, TIMEOUT, FOCUS, NETWORK, MEMORY, PERSONA, XP, EMAIL, REVERT }
enum class TimelineKind { ALARM, EVENT, BLOCK, HABIT, TASK }
enum class TurnSource { LLM, OFFLINE_FALLBACK, ERROR }
enum class PermissionKind { NOTIFICATIONS, EXACT_ALARMS, USAGE_ACCESS, OVERLAY, VPN }

data class AppliedChange(val label: String, val kind: ChangeKind, val refId: String? = null)
data class SkippedAction(val type: String, val reason: String)
data class ExecuteReport(
    val applied: List<AppliedChange> = emptyList(),
    val skipped: List<SkippedAction> = emptyList(),
) { val isEmpty: Boolean get() = applied.isEmpty() && skipped.isEmpty() }

data class LifeStateProjection(val json: String) { val charCount: Int get() = json.length }

data class TimelineItem(
    val timeHhmm: String,
    val kind: TimelineKind,
    val title: String,
    val subtitle: String = "",
    val done: Boolean = false,
    val refId: String = "",
    val hard: Boolean = false,
)

data class FocusSession(val mode: FocusMode, val packages: List<String>, val endsAtEpochMs: Long? = null)

/** Everything :enforce needs in one immutable snapshot. */
data class EnforcementRules(
    val focus: FocusRules,
    val timeouts: List<AppTimeout>,
    val demoStrictTimeouts: Boolean = false,
    val activeGoalLabel: String? = null,
    val activeGoalDeadlineIso: String? = null,
)

data class PermissionStatus(
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val vpnConsented: Boolean = false,
    val fullScreenIntent: Boolean = false,
) {
    val enforcementReady: Boolean get() = usageAccess && overlay
}

data class InstalledApp(val packageName: String, val label: String)

data class LlmConfig(
    val endpoint: String = "",
    val deployment: String = "",
    val apiKey: String = "",
    val apiVersion: String = "2024-10-21",
) { val usable: Boolean get() = endpoint.isNotBlank() && deployment.isNotBlank() && apiKey.isNotBlank() }

data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = 1400,
    val temperature: Double = 0.4,
)

data class AgentTurnResult(
    val reply: String,
    val actions: List<Action> = emptyList(),
    val report: ExecuteReport = ExecuteReport(),
    val source: TurnSource = TurnSource.OFFLINE_FALLBACK,
    val expansionGoalId: String? = null,
)
```

### `Ports.kt`

Copy §5 of the orchestration doc verbatim, and add the `Ports` bundle that lets `:ui` reach everything without depending on implementation modules:

```kotlin
interface Ports {
    val lifeState: LifeStateStore
    val chat: ChatStore
    val executor: ActionExecutorPort
    val agent: AgentPort
    val projection: ProjectionPort
    val timeline: TimelinePort
    val risk: RiskPort
    val compactor: CompactorPort
    val enforce: EnforceGateway
    val system: SystemAccess
    val apps: AppCatalog
    val mailbox: MailboxSync
    val classifier: EmailClassifierPort
}
```

### `Personas.kt`

```kotlin
data class Persona(val id: String, val name: String, val voice: String)
object Personas {
    val STRICT = Persona("strict", "Strict", "Blunt, terse, holds them to the deadline. No pep talk.")
    val SUPPORTIVE = Persona("supportive", "Supportive", "Warm, encouraging, forgiving of one slip.")
    val COACH = Persona("coach", "Coach", "Energetic, competitive, frames work as training.")
    val ALL = listOf(STRICT, SUPPORTIVE, COACH)
    fun byId(id: String): Persona = ALL.firstOrNull { it.id == id } ?: STRICT
}
```

### `DemoPackages.kt`

The emulator has no Instagram, so the model's natural output must be remapped rather than dropped.

```kotlin
object DemoPackages {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val CHROME = "com.android.chrome"
    const val DOCS = "com.google.android.apps.docs"
    const val MAPS = "com.google.android.apps.maps"
    const val SELF = "com.lifeos.app"

    /** Used by AppCatalog.resolveOrSubstitute when the requested package is not installed. */
    val SUBSTITUTES: Map<String, String> = mapOf(
        INSTAGRAM to YOUTUBE,
        "com.twitter.android" to YOUTUBE,
        "com.zhiliaoapp.musically" to YOUTUBE,
        "com.facebook.katana" to YOUTUBE,
        "com.reddit.frontpage" to CHROME,
    )

    /** Never blocked, in either focus mode. Prevents bricking the device mid-demo. */
    val ALWAYS_ALLOW: Set<String> = setOf(
        SELF, "com.android.systemui", "com.android.settings", "com.google.android.dialer",
        "com.android.dialer", "com.android.launcher3", "com.google.android.apps.nexuslauncher",
        "com.android.phone", "com.google.android.permissioncontroller",
    )

    /** Common-name aliases so the model can say "instagram" instead of a package. */
    val ALIASES: Map<String, String> = mapOf(
        "instagram" to INSTAGRAM, "youtube" to YOUTUBE, "chrome" to CHROME,
        "docs" to DOCS, "google docs" to DOCS, "maps" to MAPS, "lifeos" to SELF,
    )
}
```

### `Ids.kt` and `Time.kt`

`Ids.new(prefix: String): String` returning `"${prefix}_${randomAlphanumeric(8)}"`.

`Time` must be pure JVM (`java.time`), because `:domain` unit tests depend on it:

- `fun nowEpochMs(): Long`
- `fun todayIso(zone: ZoneId = ZoneId.systemDefault()): String` → `yyyy-MM-dd`
- `fun nowIso(): String` → `yyyy-MM-dd'T'HH:mm`
- `fun parseIsoOrNull(s: String?): LocalDateTime?` — must accept both `yyyy-MM-dd` and `yyyy-MM-ddTHH:mm`
- `fun nextOccurrenceEpochMs(timeHhmm: String, fromEpochMs: Long = nowEpochMs()): Long`
- `fun isoDayOfWeek(dateIso: String): Int`
- `fun plusDaysIso(dateIso: String, days: Long): String`
- `fun startOfTodayEpochMs(): Long`

Be permissive in parsing. The model will emit sloppy dates and a throw here kills a whole turn.

### `LifeOsLog.kt`

A tiny pluggable logger so JVM modules can log without an Android dependency:

```kotlin
object LifeOsLog {
    var sink: ((tag: String, msg: String) -> Unit)? = null
    fun d(tag: String, msg: String) { sink?.invoke(tag, msg) ?: println("[$tag] $msg") }
}
```

`:app` sets `sink` to `android.util.Log.d`. Agreed tags: `LifeOS/Agent`, `LifeOS/Exec`, `LifeOS/Focus`, `LifeOS/Vpn`, `LifeOS/Mail`, `LifeOS/Data`.

---

## Step 3 — `:data`, real persistence

Package `com.lifeos.data`. This is real code, not a stub — every other module needs working state.

- `LifeOsDataStore.kt` — one `preferencesDataStore(name = "lifeos")` extension on `Context`.
- `DataStoreLifeStateStore.kt` — implements `LifeStateStore`. Key `stringPreferencesKey("life_state_v1")`. Holds a `MutableStateFlow<CanonicalLifeState>` seeded on construction from disk inside an injected `CoroutineScope`. `mutate` takes a `Mutex`, applies the lambda to the current value, writes the serialized result, then updates the flow.
- `DataStoreChatStore.kt` — same shape, key `chat_v1`, type `ChatTranscript`.
- `BuildConfigSecretsStore.kt` — implements `SecretsStore`, constructed with an `LlmConfig`, returns it or `null` when `!usable`.
- `LifeOsJson.kt` — one shared `Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }`.

`ignoreUnknownKeys = true` is important: it lets the state schema grow during the sprint without wiping a device that already has data.

Decoding must be defensive. A corrupt or older blob returns `CanonicalLifeState()` and logs, never throws — otherwise a mid-sprint model change bricks the app until someone finds `adb shell pm clear`.

---

## Step 4 — `:app`, manifest and composition root

### `app/src/main/AndroidManifest.xml`

Declare the full permission set now so no later agent has to touch it:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<queries>
  <intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
  </intent>
</queries>
```

Use `<queries>` rather than `QUERY_ALL_PACKAGES` — same result for the app picker, no Play declaration form.

`<application>` needs `android:name=".LifeOsApplication"`, and `MainActivity` as `exported="true"` singleTop launcher with `android:theme="@style/Theme.LifeOS"`.

### `enforce/src/main/AndroidManifest.xml` — you write this, S3 and S4 do not

This is the one place S3 and S4 would otherwise collide, so it ships complete from Wave 0, pointing at the stub classes you also create:

```xml
<service android:name="com.lifeos.enforce.focus.FocusService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="Enforces user-configured focus sessions and daily app time limits" />
</service>

<service android:name="com.lifeos.enforce.vpn.LifeOsVpnService"
    android:exported="false"
    android:permission="android.permission.BIND_VPN_SERVICE">
  <intent-filter><action android:name="android.net.VpnService" /></intent-filter>
</service>

<receiver android:name="com.lifeos.enforce.alarm.AlarmReceiver" android:exported="false" />

<receiver android:name="com.lifeos.enforce.alarm.BootReceiver" android:exported="false">
  <intent-filter>
    <action android:name="android.intent.action.BOOT_COMPLETED" />
    <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
  </intent-filter>
</receiver>

<activity android:name="com.lifeos.enforce.alarm.AlarmActivity"
    android:exported="false"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:theme="@style/Theme.LifeOS.Alarm" />
```

`LifeOsVpnService` deliberately does **not** call `startForeground`. The system already shows a persistent VPN key notification, so adding an FGS type buys nothing and adds a failure mode.

Define `Theme.LifeOS` and `Theme.LifeOS.Alarm` in `:app` resources (dark, no action bar; the alarm variant fullscreen and translucent-status).

### `app/src/main/kotlin/com/lifeos/app/AppContainer.kt`

Implements `com.lifeos.core.Ports`. In Wave 0 it wires **real** `:data` stores and **stubs** for everything else, then publishes itself:

```kotlin
class AppContainer(app: Application) : Ports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val lifeState: LifeStateStore = DataStoreLifeStateStore(app, scope)
    override val chat: ChatStore = DataStoreChatStore(app, scope)
    // Wave 0: stubs. S9 replaces each with the real implementation.
    override val executor: ActionExecutorPort = StubActionExecutor()
    override val agent: AgentPort = StubAgent()
    // ... one line per port
}
```

`LifeOsApplication.onCreate` builds the container, sets `LifeOsLog.sink = { t, m -> Log.d(t, m) }`, and assigns `UiPorts.value = container`.

Put every stub in one file, `app/src/main/kotlin/com/lifeos/app/Stubs.kt`, so S9's job is literally "delete this file and fix the eight compile errors it causes". Each stub returns an empty result and logs `"STUB <name> called"`.

`MainActivity` sets `Theme.LifeOS` and calls `LifeOsApp()` from `:ui`.

---

## Step 5 — module stubs so nobody is blocked

Create these with **final signatures and empty bodies**. Wave 1 agents fill in bodies; nobody creates a file that already exists.

`:domain` (`com.lifeos.domain`), S1 fills in:

- `ActionExecutor(private val store: LifeStateStore, private val enforce: EnforceGateway, private val apps: AppCatalog) : ActionExecutorPort`
- `ProjectionBuilder : ProjectionPort`
- `TimelineMerger : TimelinePort`
- `RiskCalculator : RiskPort`
- `Compactor(private val chat: ChatStore, private val maxMessages: Int = 12) : CompactorPort`

`:agent` (`com.lifeos.agent`), S2 fills in:

- `AzureFoundryClient(private val config: LlmConfig) : LlmClient`
- `SystemPromptBuilder`
- `ActionParser`
- `OfflineFallbacks`
- `AgentController(chat, executor, projection, compactor, lifeState, llm: LlmClient?) : AgentPort`

`:email` (`com.lifeos.email`), S8 fills in:

- `SeedMailboxSync : MailboxSync`
- `EmailClassifier : EmailClassifierPort`
- `SeedMailbox` (object holding the seed JSON constant)

`:enforce` (`com.lifeos.enforce`):

- **You write `EnforceGatewayImpl` completely**, delegating to the three controllers below. S3 and S4 then never touch a shared file.
  ```kotlin
  class EnforceGatewayImpl(
      private val focus: FocusController,
      private val alarms: AlarmScheduler,
      private val network: NetworkGuardController,
  ) : EnforceGateway
  ```
- **You also write `EnforceHolder` completely.** Android constructs `Service`, `BroadcastReceiver`, and `Activity` instances itself, so those classes cannot receive constructor injection. They need one process-wide handle:
  ```kotlin
  object EnforceHolder {
      @Volatile var lifeState: LifeStateStore? = null
      @Volatile var alarms: AlarmScheduler? = null
      @Volatile var focus: FocusController? = null
      @Volatile var network: NetworkGuardController? = null
      @Volatile var rules: EnforcementRules? = null
  }
  ```
  `AppContainer` populates all five during `LifeOsApplication.onCreate`. `FocusService` (S3), `AlarmReceiver` and `BootReceiver` (S4) read from it and must tolerate `null` by no-opping — on a cold process start after boot, the receiver can fire before `Application.onCreate` completes.
- Stub for S3: `focus/FocusController(context, store: LifeStateStore)` with `start(FocusSession)`, `stop()`, `applyRules(EnforcementRules)`, `usageTodayMinutes(List<String>): Map<String, Int>`; `focus/FocusService : Service`; `focus/OverlayController(context)`; `usage/UsageStatsHelper(context)`; `system/SystemAccessImpl(context) : SystemAccess`; `system/AppCatalogImpl(context) : AppCatalog`.
- Stub for S4: `alarm/AlarmScheduler(context)` with `schedule(AlarmSpec)`, `cancel(String)`, `rescheduleAll(List<AlarmSpec>)`; `alarm/AlarmReceiver : BroadcastReceiver`; `alarm/BootReceiver : BroadcastReceiver`; `alarm/AlarmActivity : Activity`; `notify/NotificationChannels`; `vpn/NetworkGuardController(context)` with `start(NetworkRules)`, `stop()`; `vpn/LifeOsVpnService : VpnService`.

Stubbed `Service`/`Activity`/`BroadcastReceiver` classes must be *real* subclasses with empty overrides, because the manifest references them and manifest merger will fail otherwise.

---

## Step 6 — `:ui` shell

Package `com.lifeos.ui`.

### `UiPorts.kt`

```kotlin
object UiPorts {
    lateinit var value: Ports
    val isReady: Boolean get() = ::value.isInitialized
}
```

Not elegant, but it keeps `:ui` dependent on `:core` alone, which is what makes four UI agents run in parallel. No Hilt: KSP costs build time the sprint does not have.

### `theme/`

`Color.kt`, `Type.kt`, `Theme.kt` implementing the dark-first tokens from [`../lifeos_ui_technical_implementation.md`](../lifeos_ui_technical_implementation.md) §1.2: background `#0E1116`, surface `#161B22`, primary `#2EE6A6`, onPrimary `#04140F`, danger `#FF5C5C`, warn `#F5A524`. Use `darkColorScheme` and force dark regardless of system setting. Use `FontFamily.SansSerif` — do not add a downloadable-font dependency.

Also add `LifeOsSpacing` (4/8/16/24) and `LifeOsRadius` (8dp) so five UI agents produce visually consistent output without coordinating.

### `nav/LifeOsNav.kt`

`LifeOsApp()` composable: `MainScaffold` with a `TopAppBar` (title plus persona chip), a `NavHost`, and a `NavigationBar` of six destinations. Start destination `chat`.

Routes and icons: `chat`/`AutoAwesome`, `today`/`CalendarToday`, `goals`/`Flag`, `inbox`/`Mail`, `wellbeing`/`Shield`, `more`/`MoreHoriz`.

Onboarding: if `UiPorts.value.lifeState.state.value.settings.onboardingComplete` is false, show `OnboardingScreen` instead of the scaffold. Route name `onboarding`, reachable from More.

Expose a `LifeOsDestination` enum so S6/S7/S8 can navigate by chip tap without editing the nav file.

### Placeholder screens — final signatures, trivial bodies

Create all seven now so the nav graph compiles and each Wave 1 agent replaces exactly one file:

```kotlin
// ui/screens/chat/ChatScreen.kt          -> S6
@Composable fun ChatScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/today/TodayScreen.kt        -> S7
@Composable fun TodayScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/goals/GoalsScreen.kt        -> S7
@Composable fun GoalsScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/more/MoreScreen.kt          -> S7
@Composable fun MoreScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/inbox/InboxScreen.kt        -> S8
@Composable fun InboxScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/wellbeing/WellbeingScreen.kt-> S8
@Composable fun WellbeingScreen(onNavigate: (LifeOsDestination) -> Unit)
// ui/screens/onboarding/OnboardingScreen.kt -> S5
@Composable fun OnboardingScreen(onDone: () -> Unit)
```

Body: a centered `Text` with the screen name. Keep the parameter lists exactly as above — S5 through S8 depend on them and cannot renegotiate.

### `components/` — stubs with final signatures, owned by S5

S6, S7, and S8 call these from minute one, so the signatures must exist before Wave 1 opens:

```kotlin
@Composable fun ActionChipRow(chips: List<AppliedChange>, onChipClick: (AppliedChange) -> Unit)
@Composable fun RiskBadge(percent: Int)
@Composable fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit)
@Composable fun TimeoutBar(label: String, usedMinutes: Int, limitMinutes: Int, sourceLabel: String?)
@Composable fun EmptyState(title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun SectionHeader(text: String)
@Composable fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
```

Bodies: minimal but *not* empty — a plain `Text` or `Row` is enough that S6–S8 can see their layouts while S5 works on styling in parallel.

---

## Verification — this is a hard gate

Run in order. Do not report success until all three pass.

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity
adb -s emulator-5554 logcat -d -s LifeOS/Data AndroidRuntime | tail -40
```

Acceptance checklist:

- [ ] `./gradlew projects` lists all eight modules
- [ ] `:app:assembleDebug` succeeds with no errors
- [ ] APK installs on `emulator-5554`
- [ ] App launches to the Chat tab without crashing
- [ ] All six bottom-nav tabs are tappable and show their placeholder text
- [ ] `./gradlew :core:compileKotlin :domain:compileKotlin :agent:compileKotlin :email:compileKotlin` succeeds (proves the JVM modules are genuinely Android-free)
- [ ] Grep confirms zero `android.` imports under `core/src/`
- [ ] `local.properties` is gitignored and no key is committed
- [ ] Killing and relaunching the app preserves a value written through `LifeStateStore.mutate` (persistence actually works)

That last item matters more than it looks: if DataStore is silently failing, seven agents will build on sand.

## Timebox

40 minutes. If you are at 40 minutes and the gate has not passed, **the gate still comes first** — cut theme polish, cut the persona chip, cut the top bar, but do not open Wave 1 on a red build. Everything after you assumes green.

## Handoff notes for S9

Leave a short comment block at the top of `Stubs.kt` listing which port each stub covers and which subagent supplies the real one. S9's first action is deleting that file.
