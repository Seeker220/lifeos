---
title: "S9 — Integration, wiring, and demo"
wave: 2
parallel: false
blocked_by: "S1 S2 S3 S4 S5 S6 S7 S8"
ai_credentials: "wires AZURE_LLM_* into BuildConfig; never required"
timebox: "35 minutes plus 15 for recording"
---

# S9 — Integration & Demo

> Eight agents just built eight modules against stubs. Nothing has ever run together. Your job is not to write features — it is to make the seams disappear, then prove the whole thing works on a real device, then hand over something recordable. **Resist every temptation to add functionality.** At this point in the sprint, a new feature is a new way to fail on stage.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Replace every stub with its real implementation, wire the secrets, seed a demo state, run the full narrative on `emulator-5554`, produce an installable APK, and write the demo script.

## AI credentials

You wire `AZURE_LLM_ENDPOINT`, `AZURE_LLM_DEPLOYMENT`, `AZURE_LLM_API_KEY`, and `AZURE_LLM_API_VERSION` from `local.properties` into `BuildConfig` and on into `LlmConfig`. **The demo must pass with all four blank.** Test that case first, then test with a key if one is available.

---

## Files you own

- `app/src/main/kotlin/com/lifeos/app/AppContainer.kt`
- `app/src/main/kotlin/com/lifeos/app/LifeOsApplication.kt`
- `app/src/main/kotlin/com/lifeos/app/DemoSeed.kt` (new)
- `app/build.gradle.kts` — BuildConfig fields only
- `local.properties`
- `.cursor/plans/demo_script.md` (new)
- **Plus targeted fixes anywhere a Wave 1 agent left the build red.** You are the only agent with a licence to edit another agent's files, and you should use it sparingly and only to fix compilation or crashes.

---

## Step 1 — Collect the handoffs (3 minutes)

Every Wave 1 plan ends with a handoff section listing its exact constructor call. Gather all eight before you touch code; guessing a constructor and then discovering the real one costs more than reading.

You are looking for:

- S1 — `ActionExecutor`, `ProjectionBuilder`, `TimelineMerger`, `RiskCalculator`, `Compactor`, plus any changed `ExecuteReport` chip label prefixes
- S2 — `AzureFoundryClient`, `AgentController`, the default API version, and whether the deployment goes in the URL path
- S3 — `FocusController`, `SystemAccessImpl`, `AppCatalogImpl`, plus any temporary debug trigger left behind
- S4 — `AlarmScheduler`, `NetworkGuardController`, `NotificationChannels.ensureAll`, **whether the VPN shipped**, and which alarm launch path worked on API 37
- S5 — any component signature that gained a defaulted parameter, and which permission rows shipped
- S6 — which chip label prefixes it relied on for kind inference
- S7 — the `Action` constructors used for the demo alarm and focus start
- S8 — **whether the Inbox shipped**

The two "did it ship" answers determine whether you remove a nav destination and whether the demo script keeps its network beat.

---

## Step 2 — Wire `AppContainer` (10 minutes)

Delete `app/src/main/kotlin/com/lifeos/app/Stubs.kt`. That file was S0's scaffolding and its removal produces exactly the list of compile errors you need to fix.

There is one ordering problem to solve. `ActionExecutor` needs an `EnforceGateway`, and `EnforceGatewayImpl` needs the three controllers, and `FocusController` needs a `LifeStateStore`. Construct bottom-up:

```kotlin
class AppContainer(private val app: Application) : Ports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 1. persistence has no dependencies
    override val lifeState: LifeStateStore = DataStoreLifeStateStore(app, scope)
    override val chat: ChatStore = DataStoreChatStore(app, scope)

    // 2. system reads
    override val system: SystemAccess = SystemAccessImpl(app)
    override val apps: AppCatalog = AppCatalogImpl(app)

    // 3. enforcement controllers
    private val focusController = FocusController(app, lifeState)
    private val alarmScheduler = AlarmScheduler(app)
    private val networkGuard = NetworkGuardController(app)
    override val enforce: EnforceGateway =
        EnforceGatewayImpl(focusController, alarmScheduler, networkGuard)

    // 4. domain
    override val executor: ActionExecutorPort = ActionExecutor(lifeState, enforce, apps)
    override val projection: ProjectionPort = ProjectionBuilder()
    override val timeline: TimelinePort = TimelineMerger()
    override val risk: RiskPort = RiskCalculator()
    override val compactor: CompactorPort = Compactor(chat, maxMessages = 12)

    // 5. agent
    private val llmConfig = LlmConfig(
        BuildConfig.AZURE_LLM_ENDPOINT, BuildConfig.AZURE_LLM_DEPLOYMENT,
        BuildConfig.AZURE_LLM_API_KEY, BuildConfig.AZURE_LLM_API_VERSION,
    )
    override val agent: AgentPort = AgentController(
        chat, lifeState, executor, projection, compactor,
        llm = if (llmConfig.usable) AzureFoundryClient(llmConfig) else null,
    )

    // 6. email
    override val mailbox: MailboxSync = SeedMailboxSync()
    override val classifier: EmailClassifierPort = EmailClassifier()

    fun publish() {
        EnforceHolder.lifeState = lifeState
        EnforceHolder.alarms = alarmScheduler
        EnforceHolder.focus = focusController
        EnforceHolder.network = networkGuard
        UiPorts.value = this
    }
}
```

`LifeOsApplication.onCreate`, in this order:

```kotlin
LifeOsLog.sink = { tag, msg -> Log.d(tag, msg) }
NotificationChannels.ensureAll(this)
container = AppContainer(this).also { it.publish() }
```

Channels before the container, because `FocusController` may start the service immediately if timeouts are already persisted from a previous run.

Log one line confirming whether the LLM is configured — `"LLM configured: <bool>"` — and **never log the key or any prefix of it**.

---

## Step 3 — First integration build and triage (7 minutes)

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
```

You now have exclusive access to the Gradle lock, so build freely.

Triage discipline, in priority order. Anything below the line stays broken:

1. **Compilation errors** — fix in place, in whoever's file.
2. **Crash on launch** — usually an uninitialised `UiPorts` or a `lateinit` read before `publish()`.
3. **A screen that crashes when tapped** — wrap the offending read defensively; do not redesign the screen.
4. **A feature that silently does nothing** — check permissions first, then `EnforceHolder` population, before suspecting logic.
5. Everything else — write it down in the handoff and move on.

Two known integration hazards worth checking directly:

- **Chip label mismatch.** S6 infers `ChangeKind` from S1's label prefixes. If S1 shipped different prefixes, chips get the wrong icons and navigate to the wrong tab. Compare the two handoffs and fix the inference, not the labels — `:domain` has tests pinned to its labels.
- **Frozen-API drift.** Grep for any `:core` signature change. If an agent edited a frozen interface despite instructions, the other seven built against the old one and you will see a cascade of errors that look unrelated.

If a whole feature is unrecoverable, **cut it** rather than fixing it. Cut order from the orchestration doc: VPN, then IMAP, then Inbox and `:email`, then scheduled focus windows, then TTS, then personas down to one. Cutting means deleting one `AppContainer` binding and, for the Inbox, one nav destination — that is the entire payoff of the modular design, so use it without guilt.

---

## Step 4 — `DemoSeed` (5 minutes)

A debug-only object that puts the app into a known good state in one tap, invoked from a hidden long-press on the More screen's About row, or from a `MainActivity` intent extra so `adb` can trigger it.

It must be **idempotent** and it must go **through the executor**, so seeding exercises the same path as the live demo:

```kotlin
suspend fun seed(ports: Ports) {
    ports.lifeState.mutate { CanonicalLifeState() }           // clean slate
    ports.chat.mutate { ChatTranscript() }
    ports.executor.execute(OfflineFallbacks.interviewExpansion(), ActionOrigin.AGENT)
    ports.lifeState.mutate { it.copy(settings = it.settings.copy(
        demoStrictTimeouts = true, onboardingComplete = true)) }
}
```

Reusing S2's interview expansion is deliberate: the seeded state is byte-identical to what the live demo produces, so what you rehearse is what the audience sees.

Then add a `seedFilledChat(ports)` variant that appends 40 synthetic chat messages **without** touching life state. That is the setup for the compaction beat — the presenter needs a transcript long enough that compacting it visibly does something.

Trigger from adb:

```bash
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity --es demo seed
adb -s emulator-5554 shell am start -n com.lifeos.app/.MainActivity --es demo fill_chat
```

Guard both behind `BuildConfig.DEBUG`.

---

## Step 5 — Device preparation

Run this before every rehearsal and before the recording. Usage access silently returns empty when ungranted, which makes focus enforcement look broken while logging nothing useful.

```bash
ADB="adb -s emulator-5554"
APK=app/build/outputs/apk/debug/app-debug.apk

$ADB install -r "$APK"
$ADB shell appops set com.lifeos.app GET_USAGE_STATS allow
$ADB shell appops set com.lifeos.app SYSTEM_ALERT_WINDOW allow
$ADB shell appops set com.lifeos.app SCHEDULE_EXACT_ALARM allow
$ADB shell pm grant com.lifeos.app android.permission.POST_NOTIFICATIONS

# verify
$ADB shell appops get com.lifeos.app GET_USAGE_STATS
$ADB shell appops get com.lifeos.app SYSTEM_ALERT_WINDOW
```

**VPN consent cannot be granted over adb** — it is a system dialog by design. If S4 shipped the VPN, accept it manually once on the emulator before recording, then verify with `$ADB shell dumpsys connectivity | grep -i vpn`. If it was cut, drop the network beat from the script.

Note the emulator is API 37 with no Instagram installed. `AppCatalogImpl.resolveOrSubstitute` swaps in `com.google.android.youtube`, so the presenter says "Instagram" and YouTube gets blocked. Confirm that substitution is logged:

```bash
$ADB logcat -s LifeOS/Focus LifeOS/Exec LifeOS/Agent LifeOS/Alarm LifeOS/Vpn
```

---

## Step 6 — Full end-to-end run

Do this **twice**: once with `local.properties` LLM keys blank, once with a real key if available. The blank run is the one that must be perfect, because it is the one you will use on stage.

- [ ] Fresh install after `pm clear` opens Onboarding
- [ ] All five permission rows reflect the adb-granted state correctly
- [ ] Continue is enabled and reaches the Chat tab
- [ ] Suggestion chip "Crack a Google interview in 1 month" produces a reply plus 10 or more chips
- [ ] Goals shows the goal with a risk badge and the caps line
- [ ] Today shows study blocks, LeetCode daily, and the bedtime alarm at the right times
- [ ] Wellbeing shows Instagram and YouTube caps, each attributed to the goal
- [ ] Tapping the timeout chip in Chat navigates to Wellbeing
- [ ] Start Focus in blacklist mode, then open YouTube: **the overlay appears within about a second**
- [ ] The overlay subtitle names the real goal and its remaining days
- [ ] "Back to work" returns to the launcher; "Override 10 min" suppresses it
- [ ] With strict demo timeouts on, using a capped app for a minute triggers the timeout overlay with the right copy
- [ ] "Test alarm in 60s" from More fires, shows the full-screen alarm, and speaks the persona line
- [ ] Inbox sync surfaces the midterm and the assignment above the noise; promoting one puts an event on Today
- [ ] Completing a task increases XP and the streak
- [ ] Fill the chat to 40 messages, press Compact chat: message count drops, **every life-state count is unchanged**
- [ ] "Undo expansion" on the goal clears its tasks, habits, blocks, alarms, and caps, and the overlay stops firing
- [ ] `force-stop` then relaunch preserves everything
- [ ] `adb reboot` then relaunch restores the alarms
- [ ] Zero crashes across the whole run

The compaction check is the one item worth being pedantic about. It is the only place the architecture's central claim becomes observable, and if it fails it is a `:domain` bug you must fix here.

---

## Step 7 — APK

```bash
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ./LifeOS-demo.apk
```

Stay on the debug variant. A release build means signing config, minification, and a fresh set of ProGuard-shaped surprises in `kotlinx.serialization` reflection — none of which is worth doing with 20 minutes left. Sideloading a debug APK is a normal hackathon deliverable.

Record the APK size and confirm it installs on a second target if the user has a physical device attached.

---

## Step 8 — `demo_script.md`

Write `.cursor/plans/demo_script.md` as a 90-second shot list. It should be executable by someone who did not build the app.

Structure it as: a **pre-flight checklist** (the adb commands from Step 5, plus `--es demo seed` and `--es demo fill_chat`, plus "set the demo alarm 60 seconds before you start talking"), then six beats with the exact tap sequence and the line to say, then a **failure playbook**.

Suggested beats, adjusted for whatever actually shipped:

1. **The claim** (10s) — Chat empty state. *"Every hackathon has five AI todo apps. None of them can stop you opening Instagram."*
2. **One sentence, twelve changes** (20s) — tap the interview suggestion chip; let the chips land; tap through Goals and Today.
3. **It is real device state** (20s) — Wellbeing showing the caps attributed to the goal; tap Start Focus; leave the app; open YouTube; the overlay slams in.
4. **It wakes you up** (15s) — the pre-armed alarm fires; the persona line speaks.
5. **It does not forget** (15s) — More, Compact chat, counts unchanged. *"The conversation compacts. Your life doesn't."*
6. **It triages your inbox** (10s) — Inbox sync, promote the midterm, it appears on Today.

Failure playbook, one line each: if the LLM stalls use the suggestion chips (offline fallbacks are identical); if the overlay does not fire re-check `appops GET_USAGE_STATS`; if the alarm does not appear the notification fallback is in the shade; if the VPN was cut skip beat 3's network mention; if a tab crashes navigate around it and keep talking.

---

## Timebox

35 minutes of integration, then 15 for the recording. Allocation: handoffs 3, wiring 10, first build and triage 7, seed 5, device prep 3, end-to-end 5, APK and script 2.

If you are at 25 minutes with a red build, start cutting rather than debugging. A four-tab app that demonstrably blocks apps and fires alarms beats a six-tab app that does not compile.

## Final report

State plainly: which of the ten P0 features shipped, which were cut and why, the APK path, whether the LLM path was exercised or only the offline path, and any known crash the presenter should route around. Do not oversell — the presenter needs an accurate map more than an optimistic one.
