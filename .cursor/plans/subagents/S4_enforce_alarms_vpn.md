---
title: "S4 — :enforce, alarms, TTS, notifications, VPN network guard"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "85 minutes"
---

# S4 — `:enforce` alarms and network half

> Two independent deliverables. **Alarms are P0 and must land.** The VPN network guard is the first item on the sprint's cut list, so build it second and do not let it eat alarm time.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

Real exact alarms that fire and speak in the persona's voice, restored across reboot, plus an on-device VPN that starves selected apps of network access.

## AI credentials

**None.** TTS is the on-device `android.speech.tts` engine and needs no permission and no key.

---

## Files you own

- `enforce/src/main/kotlin/com/lifeos/enforce/alarm/**` — `AlarmScheduler`, `AlarmReceiver`, `BootReceiver`, `AlarmActivity`, `TtsSpeaker`
- `enforce/src/main/kotlin/com/lifeos/enforce/notify/**` — `NotificationChannels`
- `enforce/src/main/kotlin/com/lifeos/enforce/vpn/**` — `NetworkGuardController`, `LifeOsVpnService`
- `enforce/src/main/res/layout/activity_alarm.xml`
- `enforce/src/main/res/values/strings_alarm.xml`
- `enforce/src/main/res/drawable/alarm_*.xml`

## Files you must NOT touch

- `enforce/src/main/AndroidManifest.xml` — **S0 already declared your service, both receivers, and `AlarmActivity`** with `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`, and the VPN intent filter. Editing it collides with S3.
- `enforce/.../EnforceGatewayImpl.kt` and `EnforceHolder.kt` — S0 wrote both. Read, never edit.
- Anything under `enforce/.../focus/`, `.../usage/`, `.../system/` — that is S3.
- `core/**` (frozen), `domain/**`, `agent/**`, `email/**`, `ui/**`, `app/**`, any `build.gradle.kts`.

`NotificationChannels` is yours but **S3 depends on it** for the focus service notification. Implement it in your first ten minutes so S3 is never blocked, and do not change the channel id `"lifeos_focus"` that S0 stubbed.

---

## Contracts you implement

```kotlin
class AlarmScheduler(private val context: Context) {
    fun schedule(spec: AlarmSpec)
    fun cancel(alarmId: String)
    fun rescheduleAll(alarms: List<AlarmSpec>)
}

class NetworkGuardController(private val context: Context) {
    fun start(rules: NetworkRules)
    fun stop()
}
```

`EnforceGatewayImpl` (S0's file) already routes `scheduleAlarm`, `cancelAlarm`, `startNetworkGuard`, and `stopNetworkGuard` to you.

---

## Step 1 — `NotificationChannels` (10 minutes, do this first)

Three channels, created idempotently from a single `ensureAll(context)` call that both `LifeOsApplication` and each service invoke defensively:

- `FOCUS = "lifeos_focus"` — `IMPORTANCE_LOW`, no sound. The ongoing focus-service notification. **S3 needs this.**
- `ALARM = "lifeos_alarm"` — `IMPORTANCE_HIGH`, bypass DND, alarm audio attributes (`USAGE_ALARM`), used for the full-screen-intent notification.
- `VPN = "lifeos_vpn"` — `IMPORTANCE_LOW`. Informational only.

Also expose stable notification ids: `NOTIF_FOCUS = 1001`, `NOTIF_ALARM = 1002`, `NOTIF_VPN = 1003`.

---

## Step 2 — `AlarmScheduler` (20 minutes)

### Scheduling

```kotlin
val am = context.getSystemService(AlarmManager::class.java)
if (!am.canScheduleExactAlarms()) { log and return }   // never crash; onboarding drives the grant
val triggerAt = spec.triggerAtEpochMs ?: Time.nextOccurrenceEpochMs(spec.timeHhmm)
val show = PendingIntent.getActivity(...)              // opens MainActivity
val fire = PendingIntent.getBroadcast(
    context, spec.id.hashCode(),
    Intent(context, AlarmReceiver::class.java)
        .putExtra(EXTRA_ALARM_ID, spec.id)
        .putExtra(EXTRA_LABEL, spec.label)
        .putExtra(EXTRA_PERSONA_LINE, spec.personaLine),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)
am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
```

`setAlarmClock` is the correct choice: it is exempt from Doze batching and it shows the system's next-alarm icon, which reads as legitimacy during a demo.

Use `spec.id.hashCode()` as the `PendingIntent` request code consistently in `schedule` and `cancel`, otherwise cancellation silently misses.

Gate on `canScheduleExactAlarms()` every time. Do **not** declare `USE_EXACT_ALARM` — it is reserved for apps whose core purpose is a clock, timer, or calendar, and LifeOS is not one.

Skip specs where `enabled == false`. Skip and log specs whose resolved trigger is already in the past by more than a minute; do not silently fire them at boot.

`rescheduleAll` cancels everything it can identify, then re-schedules from the list. Called by `BootReceiver` and by S9's seed routine.

### `AlarmReceiver`

`onReceive` must be fast — you have roughly 10 seconds. Do not do work here beyond handing off.

Two launch paths, in this order:

1. **Full-screen-intent notification** on channel `ALARM`, with `setFullScreenIntent(alarmActivityPendingIntent, true)`, `setCategory(CATEGORY_ALARM)`, `setPriority(PRIORITY_HIGH)`, `setOngoing(true)`, and a "Dismiss" action. This is the supported way to surface an alarm from the background.
2. **Direct `startActivity`** with `FLAG_ACTIVITY_NEW_TASK`. This works because `SYSTEM_ALERT_WINDOW` exempts us from background-activity-launch restrictions. Attempt it only when `SystemAccessImpl`-style checks pass.

Guard path 1 on API 34+ with `notificationManager.canUseFullScreenIntent()`. On this emulator (API 37) full-screen-intent is **not** auto-granted to a non-calling, non-clock app, so **the fallback is the path that will actually run** — treat it as the primary and test it first. If neither is available, post a plain high-priority notification so the alarm is never silently lost.

Also acquire a 10-second `PARTIAL_WAKE_LOCK` around the hand-off, and release it in a `finally`.

### `AlarmActivity`

`activity_alarm.xml`, plain `android.widget` views on a near-black background — no Material Components dependency:

- Large time display, `HH:mm`
- The alarm label
- The persona line in the accent teal `#2EE6A6`, 20sp
- One large "Dismiss" button
- A smaller "Snooze 5 min" button

Behaviour:

- `onCreate`: keep the screen on (`FLAG_KEEP_SCREEN_ON`), vibrate a pattern via `VibratorManager`, and speak the persona line through `TtsSpeaker`.
- Dismiss: stop TTS, stop vibration, cancel `NOTIF_ALARM`, `finish()`.
- Snooze: schedule a new one-shot `AlarmSpec` 5 minutes out through `AlarmScheduler` — **do not write state**, `:enforce` is write-free. The snooze alarm is ephemeral and does not need to survive a reboot.
- `onDestroy`: always shut down TTS and the vibrator. A leaked TTS engine holds audio focus indefinitely.

### `TtsSpeaker`

Wrap `TextToSpeech` with an init-listener queue, because `speak` before `onInit` is silently dropped:

```kotlin
class TtsSpeaker(context: Context) {
    fun speak(text: String)   // queues until ready
    fun stop()
    fun shutdown()
}
```

Set `Locale.getDefault()`, fall back to `Locale.US` if unsupported, use `QUEUE_FLUSH`, and set `AudioAttributes` with `USAGE_ALARM` so it is audible while the ringer is silenced. Wrap everything in `runCatching` — an emulator without a TTS engine must degrade to a silent alarm, not a crash.

### `BootReceiver`

Handles `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Read `EnforceHolder.lifeState`; if it is `null` (the receiver can fire before `Application.onCreate` finishes), no-op and log rather than crashing. Otherwise call `rescheduleAll(state.alarms)`, and start `FocusService` if `state.appTimeouts` is non-empty or `state.focus.active` is true.

Use `goAsync()` if you need more than a few milliseconds, and always call `finish()`.

---

## Step 3 — `LifeOsVpnService` and `NetworkGuardController` (35 minutes)

### Read this before writing code — the earlier plan had the filter direction backwards

`VpnService.Builder`'s per-app methods control **who enters the tunnel**, not who is blocked:

- `addAllowedApplication(pkg)` — **only** these apps route through the tunnel; everyone else uses the network normally.
- `addDisallowedApplication(pkg)` — these apps **bypass** the tunnel and use the network normally; everyone else routes through it.

Since our tunnel is a blackhole that reads packets and discards them, "routed through the tunnel" means "has no network". Therefore:

- **Blacklist mode** (starve YouTube and Instagram): `addAllowedApplication` for **each blacklisted package**. Their traffic enters the blackhole. Every other app is untouched.
- **Whitelist mode** (only Chrome, Docs, and LifeOS may reach the network): `addDisallowedApplication` for **each whitelisted package plus `DemoPackages.ALWAYS_ALLOW`**. They bypass and work. Everything else enters the blackhole.

Getting this backwards produces a demo where blocking an app gives it *better* connectivity, which is both wrong and confusing on stage. Add a comment above each call stating which mode it serves.

### Establishing the tunnel

```kotlin
val builder = Builder()
    .setSession("LifeOS Focus")
    .addAddress("10.7.0.1", 32)
    .addRoute("0.0.0.0", 0)
    .addDnsServer("1.1.1.1")
    .setBlocking(true)
    .setMtu(1500)
// then the per-app filters per the mode, each wrapped in runCatching
val fd = builder.establish() ?: return   // null means consent was revoked
```

`addDisallowedApplication` and `addAllowedApplication` throw `NameNotFoundException` for a package that is not installed. Wrap **each individual call** in `runCatching` and log the miss — one absent package must not abort the whole tunnel. This matters here because the model will emit `com.instagram.android` and it is not installed on this emulator.

Whitelist mode has one more failure case worth handling: if the resulting disallowed set covers *every* installed app, `establish()` gives you a tunnel that blocks nothing. Harmless, but log it so you are not debugging a phantom.

### The blackhole loop

A single daemon thread reading and discarding:

```kotlin
thread(isDaemon = true, name = "lifeos-vpn-blackhole") {
    val input = FileInputStream(fd.fileDescriptor)
    val buf = ByteArray(32 * 1024)
    try { while (!Thread.interrupted()) { if (input.read(buf) <= 0) break } }
    catch (_: IOException) { /* fd closed on stop */ }
}
```

Do not forward, do not parse IP headers, do not implement DNS. Domain-level filtering was explicitly deferred; app-level routing is the P0 scope and it is what the demo shows.

`setBlocking(true)` makes `read` block instead of spinning, which keeps the thread off the CPU.

### Service lifecycle

- `onStartCommand` reads a mode and package list from intent extras, tears down any existing tunnel, establishes the new one, starts the thread, returns `START_STICKY`.
- Support an explicit `ACTION_STOP` extra so `NetworkGuardController.stop()` can request a clean teardown.
- `onRevoke` — the user turned the VPN off in system Settings, or another VPN took over. Tear down and `stopSelf()`. Do not fight for the tunnel.
- `onDestroy` — interrupt the thread, `close()` the fd, null the reference. A leaked fd survives the service and the user has to reboot.
- **Do not call `startForeground`.** The system already shows a persistent VPN key notification. Adding an FGS type buys nothing and introduces an Android 14 type-mismatch failure mode. Post a plain `NOTIF_VPN` notification on the `VPN` channel if you want the state visible in the shade.

### `NetworkGuardController`

- `start(rules)` — if `rules.mode == OFF`, delegate to `stop()`. Otherwise check `VpnService.prepare(context)`. If it returns non-null, consent has not been granted: **log and return without starting**. Do not try to launch the consent dialog from here — a `Context` that is not an `Activity` cannot, and the Onboarding screen (S5) owns that flow.
- `stop()` — send `ACTION_STOP` to the service.
- Never throw. A failure to start the network guard must leave focus enforcement working.

---

## Verification

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat -s LifeOS/Alarm LifeOS/Vpn AlarmManager Vpn
```

Cap yourself at two or three Gradle builds; seven other sessions share the lock. On `Timeout waiting to lock`, wait 20 seconds and retry.

Grant exact alarms without tapping through Settings:

```bash
adb -s emulator-5554 shell appops set com.lifeos.app SCHEDULE_EXACT_ALARM allow
adb -s emulator-5554 shell dumpsys alarm | grep -i lifeos
```

VPN consent **cannot** be granted over `adb` — it is a system dialog by design. For your own testing, either accept it manually once on the emulator or verify only that `prepare()` returns non-null and your code declines cleanly. Confirm the tunnel with:

```bash
adb -s emulator-5554 shell dumpsys connectivity | grep -i -A5 vpn
```

Acceptance checklist — alarms (P0):

- [ ] An alarm scheduled 60 seconds out fires while the app is backgrounded
- [ ] `AlarmActivity` appears over the lock screen and turns the screen on
- [ ] The persona line is spoken aloud, and a missing TTS engine degrades to a silent alarm rather than a crash
- [ ] With full-screen-intent unavailable on API 37, the direct-`startActivity` fallback still shows the alarm
- [ ] If both paths fail, a high-priority notification appears — the alarm is never silently lost
- [ ] Dismiss stops TTS and vibration and clears the notification
- [ ] Snooze fires again five minutes later
- [ ] `cancel(alarmId)` genuinely prevents the alarm (verify with `dumpsys alarm`)
- [ ] `canScheduleExactAlarms() == false` logs and returns instead of crashing
- [ ] `adb reboot` followed by relaunch restores scheduled alarms via `BootReceiver`
- [ ] `BootReceiver` with a null `EnforceHolder.lifeState` no-ops without crashing

Acceptance checklist — VPN (cuttable):

- [ ] `NetworkGuardController.start` with no consent logs and returns, and focus enforcement keeps working
- [ ] After manual consent, blacklist mode on YouTube leaves Chrome online and YouTube offline
- [ ] Whitelist mode with Chrome allowed leaves Chrome online and YouTube offline
- [ ] A package in the list that is not installed logs a miss and the tunnel still establishes
- [ ] Turning the VPN off in Settings triggers `onRevoke` and a clean teardown
- [ ] `stop()` closes the fd; no `lifeos-vpn-blackhole` thread survives (check with `dumpsys`)
- [ ] Nothing in `:enforce` calls `store.mutate` — grep to confirm

---

## Timebox

85 minutes: channels 10, alarms 20, alarm activity and TTS 20, VPN 35.

**Sequencing is not optional here.** Alarms are never cut and the VPN is first on the cut list, so if you are at 55 minutes without alarms fully working, abandon the VPN entirely: leave `NetworkGuardController.start` logging "network guard unavailable" and return. A silent no-op is a perfectly good outcome for a cut feature, and `:domain` already handles it because every gateway call is fire-and-forget.

Within the VPN, cut in this order: the `NOTIF_VPN` notification, then whitelist mode (keep blacklist, which is what the demo shows), then the whole tunnel.

## Handoff notes for S9

Report the exact constructor calls:

```kotlin
val alarmScheduler = AlarmScheduler(app)
val networkGuard = NetworkGuardController(app)
NotificationChannels.ensureAll(app)   // call from LifeOsApplication.onCreate
```

State clearly whether the VPN shipped or was cut, and which alarm launch path actually worked on API 37 — S9 needs that to write the demo script's alarm beat, and needs to know whether to pre-accept the VPN consent dialog before recording.
