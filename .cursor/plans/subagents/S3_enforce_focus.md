---
title: "S3 — :enforce, focus sessions, app timeouts, overlay"
wave: 1
parallel: true
blocked_by: "S0"
ai_credentials: none
timebox: "85 minutes"
---

# S3 — `:enforce` focus half

> You own the moment that wins the hackathon: the user leaves LifeOS, opens YouTube, and a wall slams in front of them quoting their own deadline back at them. Nothing else in the app is this hard to fake, and nothing else is this memorable.

Architecture reference: [`../lifeos_modular_build_orchestration.md`](../lifeos_modular_build_orchestration.md).

## Mission

One foreground service, one 800 ms loop, two enforcement reasons (active focus session and daily app timeout), one overlay window. Plus the permission-status and app-listing reads that the onboarding and Wellbeing screens depend on.

## AI credentials

**None.** `:enforce` must never call an LLM or reach the network.

---

## Files you own

- `enforce/src/main/kotlin/com/lifeos/enforce/focus/**` — `FocusController`, `FocusService`, `OverlayController`, `TimeoutMonitor`
- `enforce/src/main/kotlin/com/lifeos/enforce/usage/**` — `UsageStatsHelper`
- `enforce/src/main/kotlin/com/lifeos/enforce/system/**` — `SystemAccessImpl`, `AppCatalogImpl`
- `enforce/src/main/res/layout/overlay_block.xml`
- `enforce/src/main/res/values/strings_focus.xml`
- `enforce/src/main/res/drawable/overlay_bg.xml` and any other drawable you need, prefixed `overlay_`

## Files you must NOT touch

- `enforce/src/main/AndroidManifest.xml` — **S0 already declared your service.** Editing it collides with S4.
- `enforce/src/main/kotlin/com/lifeos/enforce/EnforceGatewayImpl.kt` — S0 wrote it; it already delegates to your `FocusController`.
- `enforce/src/main/kotlin/com/lifeos/enforce/EnforceHolder.kt` — S0 wrote it. **Read it, never edit it.** This is how `FocusService` reaches `LifeStateStore`, since Android constructs services itself and there is no constructor to inject.
- Anything under `enforce/.../alarm/`, `.../notify/`, `.../vpn/` — that is S4.
- `core/**` (frozen), `domain/**`, `agent/**`, `email/**`, `ui/**`, `app/**`, any `build.gradle.kts`.

If you need a notification channel, **do not create one** — S4 owns `notify/NotificationChannels`. Use the constant `NotificationChannels.FOCUS` (S0 stubbed it with the id `"lifeos_focus"`). If S4 has not filled it in yet, your `startForeground` still works because S0's stub creates the channel.

---

## Contracts you implement

```kotlin
class FocusController(
    private val context: Context,
    private val store: LifeStateStore,
) {
    fun start(session: FocusSession)
    fun stop()
    fun applyRules(rules: EnforcementRules)
    fun usageTodayMinutes(packages: List<String>): Map<String, Int>
}

class SystemAccessImpl(private val context: Context) : SystemAccess {
    override fun permissions(): PermissionStatus
}

class AppCatalogImpl(private val context: Context) : AppCatalog {
    override suspend fun launchableApps(): List<InstalledApp>
    override suspend fun resolveOrSubstitute(nameOrPackage: String): String?
}
```

`EnforceGatewayImpl` (S0's file) already routes `startFocus`, `stopFocus`, `applyRules`, and `usageTodayMinutes` to you. You do not wire yourself in.

---

## Step 1 — `UsageStatsHelper` (15 minutes)

Two distinct reads, two distinct APIs. Getting these mixed up is the most common way this feature silently returns nothing.

**Foreground package right now:**

```kotlin
fun foregroundPackage(lookbackMs: Long = 5_000): String? {
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val now = System.currentTimeMillis()
    val events = usm.queryEvents(now - lookbackMs, now)
    var last: String? = null
    val e = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(e)
        if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
    }
    return last
}
```

Use `ACTIVITY_RESUMED`, not the deprecated `MOVE_TO_FOREGROUND`. Keep a `lastKnownForeground` field and return it when the window yields no event — the query is genuinely empty between app switches and returning `null` there makes the overlay flicker.

**Minutes used today per package:**

```kotlin
fun usageTodayMinutes(packages: Collection<String>): Map<String, Int> {
    val usm = context.getSystemService(UsageStatsManager::class.java)
    val stats = usm.queryAndAggregateUsageStats(Time.startOfTodayEpochMs(), System.currentTimeMillis())
    return packages.associateWith { ((stats[it]?.totalTimeInForeground ?: 0L) / 60_000L).toInt() }
}
```

`queryAndAggregateUsageStats` is the correct call here. `queryUsageStats(INTERVAL_DAILY, ...)` returns misaligned buckets and will report yesterday's minutes.

Wrap both in `runCatching` returning empty. Without `PACKAGE_USAGE_STATS` these return nothing *silently* rather than throwing, which is exactly why S9 grants the permission over `adb` before the demo run.

---

## Step 2 — `OverlayController` (25 minutes)

**Use an inflated XML `View`, not a `ComposeView`.** A `ComposeView` inside a `WindowManager` overlay needs `setViewTreeLifecycleOwner` and `SavedStateRegistryOwner` plumbing that will eat 20 minutes and can crash on first show. This is a deliberate exception to the app being Compose everywhere.

### `overlay_block.xml`

Full-screen `FrameLayout`, background `#F20E1116` (near-opaque brand charcoal), centered vertical `LinearLayout` containing:

- `@+id/overlay_title` — 28sp bold, `#FFFFFF`
- `@+id/overlay_subtitle` — 16sp, `#B0BAC5`
- `@+id/overlay_source` — 13sp, `#2EE6A6`, for `"From: Crack Google interview"`, `visibility=gone` by default
- `@+id/overlay_back` — a `Button`, label "Back to work", teal `#2EE6A6` on `#04140F`
- `@+id/overlay_override` — a flat `Button`, label "Override 10 min", muted `#8A94A0`

Use only `android.widget` classes. Do not add a Material Components dependency.

### Window parameters

```kotlin
WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT,
).apply { gravity = Gravity.TOP or Gravity.START }
```

Do **not** set `FLAG_NOT_TOUCHABLE` or `FLAG_NOT_FOCUSABLE` — you need the two buttons to be tappable. `SYSTEM_ALERT_WINDOW` also exempts us from Android 10+ background-activity-launch restrictions, which is why the HOME intent below works from a service.

### API

```kotlin
fun show(reason: BlockReason, title: String, subtitle: String, sourceLabel: String?)
fun hide()
val isShowing: Boolean
```

`enum class BlockReason { FOCUS, TIMEOUT }` lives in your package.

Rules:

- Idempotent. If already showing with the same reason and title, update the text in place rather than removing and re-adding the view — re-adding causes a visible flash every 800 ms.
- All `WindowManager` calls must be on the main thread. Hold a `Handler(Looper.getMainLooper())` and post.
- Wrap `addView` / `removeView` in `runCatching`. `removeView` on an already-detached view throws, and that exception would kill the service loop.
- **"Back to work"** → hide, then `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME).setFlags(FLAG_ACTIVITY_NEW_TASK)` and `startActivity`.
- **"Override 10 min"** → hide and call back into the service to register a grace window. Expose it as a constructor lambda `onOverride: (String) -> Unit` receiving the package name, so `OverlayController` stays free of service state.

### Copy — this is the demo's most-quoted line, so make it good

- Focus, blacklist: title `"Not now."`, subtitle `"<AppLabel> is blocked. <goal deadline phrase>"`
- Focus, whitelist: title `"Focus mode."`, subtitle `"Only <n> apps are allowed right now."`
- Timeout: title `"That's your <n> minutes."`, subtitle `"<AppLabel> is done for today. <goal deadline phrase>"`

The goal deadline phrase comes from `EnforcementRules.activeGoalLabel` and `activeGoalDeadlineIso`: render `"Two days left on Crack Google interview."` when both are present, and omit the sentence entirely when they are not. Compute the day count with `Time`; say `"Due today."` at zero and `"Overdue."` when negative.

---

## Step 3 — `TimeoutMonitor` (10 minutes)

Pure decision logic, no Android state, so it is trivially testable.

```kotlin
class TimeoutMonitor {
    fun effectiveLimit(t: AppTimeout, demoStrict: Boolean): Int =
        if (demoStrict) minOf(t.limitMinutes, 1) else t.limitMinutes

    fun exceeded(pkg: String, usedMinutes: Int, rules: EnforcementRules): AppTimeout?
}
```

The `demoStrict` clamp is how the overlay becomes visible on stage without waiting 30 real minutes: `:domain` stores the honest 30, and you clamp to 1 only for enforcement. The Wellbeing screen therefore keeps showing "30m" while the block fires after 60 seconds.

---

## Step 4 — `FocusService` (25 minutes)

One service, one loop, both enforcement reasons. Do not create a second service — two polling services double the battery cost and the ways it can die.

### Lifecycle

- `onStartCommand` → `startForeground` immediately (Android kills you in 5 seconds otherwise), then launch the loop in a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Return `START_STICKY`.
- On API 34+, `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`. Below that, the two-argument overload. `minSdk` is 33 so you need exactly one `Build.VERSION.SDK_INT >= 34` guard.
- Notification: channel `NotificationChannels.FOCUS`, ongoing, non-dismissible, `setContentTitle("LifeOS is watching")`, content text summarising the mode and the number of caps. Tapping it opens `MainActivity` — resolve the intent by `packageName` plus `.MainActivity` rather than importing `:app`, which would create a dependency cycle.
- `onDestroy` → cancel the scope and `overlay.hide()`. A leaked overlay after the service dies is unrecoverable without a reboot.

### The loop

The service obtains its dependencies from `EnforceHolder` (S0's file), because Android instantiates services and there is no constructor to inject into. Read `EnforceHolder.lifeState` and `EnforceHolder.rules` on each tick, and `stopSelf()` if `lifeState` is still `null` — that only happens if the service somehow outlived the process.

```
every 800ms:
  state = EnforceHolder.lifeState?.state?.value ?: return stopSelf()
  if (!shouldRun(state)) { overlay.hide(); stopSelf(); return }
  rules = EnforcementRules from state (or the last applyRules snapshot)
  fg = usage.foregroundPackage() ?: continue
  if (fg == myPackageName)                  { overlay.hide(); continue }
  if (fg in DemoPackages.ALWAYS_ALLOW)      { overlay.hide(); continue }
  if (overrideActiveFor(fg))                { overlay.hide(); continue }

  focusViolation = rules.focus.active && violates(fg, rules.focus)
  timeoutHit     = timeoutMonitor.exceeded(fg, usedMinutes(fg), rules)

  when {
    focusViolation -> overlay.show(FOCUS, ...)
    timeoutHit != null -> overlay.show(TIMEOUT, ...)
    else -> overlay.hide()
  }
```

`violates(fg, focus)`:

- `BLACKLIST` → `fg in focus.packages`
- `WHITELIST` → `fg !in focus.packages` and `fg !in DemoPackages.ALWAYS_ALLOW`

`shouldRun(state)` → true when `focus.active`, or `appTimeouts` is non-empty, or `focus.windows` is non-empty. Timeouts are all-day, so the service normally runs continuously once a goal expansion has set a cap.

Performance rules:

- Cache `usageTodayMinutes` for **10 seconds**. Calling `queryAndAggregateUsageStats` every 800 ms is expensive and the numbers do not move that fast.
- Cache `PackageManager` label lookups in a `MutableMap<String, String>`. Uncached, they allocate on every tick.
- Wrap the whole loop body in `runCatching` and log at `LifeOS/Focus`. One thrown exception must not end enforcement for the session.

### Scheduled focus windows

If `rules.focus.windows` contains a window matching the current ISO day-of-week and `HH:mm`, treat it as an active focus session with that window's mode and packages, even when `focus.active` is false. This is what makes "block YouTube during my 7–9pm study block" work without the user pressing anything.

**You never write state to reflect this.** The window is evaluated live on each tick. `:enforce` is write-free — that is the design law that removes every race between this service and the UI.

### Override grace

Keep `MutableMap<String, Long>` of package to expiry, 10 minutes. In memory only. When the user overrides, log it at `LifeOS/Focus` so the behaviour is observable during the demo.

### `FocusController`

Thin. `start`/`stop` send `startForegroundService` / `stopService` intents. `applyRules` writes the snapshot to `EnforceHolder.rules` and starts the service if it is not already running. `usageTodayMinutes` delegates straight to `UsageStatsHelper` so the Wellbeing screen can render progress bars without the service running at all.

---

## Step 5 — `SystemAccessImpl` and `AppCatalogImpl` (10 minutes)

`permissions()` returns a fully populated `PermissionStatus`:

- `notifications` — `NotificationManagerCompat.from(context).areNotificationsEnabled()`
- `exactAlarms` — `context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()`
- `usageAccess` — `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == MODE_ALLOWED`
- `overlay` — `Settings.canDrawOverlays(context)`
- `vpnConsented` — `VpnService.prepare(context) == null`
- `fullScreenIntent` — API 34+: `notificationManager.canUseFullScreenIntent()`; below that, `true`

Every field in `runCatching { }.getOrDefault(false)`. A `SecurityException` reading one of these must not blank out the whole onboarding screen.

`launchableApps()` uses `queryIntentActivities` with `ACTION_MAIN` + `CATEGORY_LAUNCHER` (S0 declared the `<queries>` element, so no `QUERY_ALL_PACKAGES` needed), maps to `InstalledApp(packageName, label)`, drops our own package, de-duplicates, and sorts by label. Run it on `Dispatchers.IO`.

`resolveOrSubstitute(nameOrPackage)`:

1. Lowercase-trim; if it matches `DemoPackages.ALIASES`, replace it with the mapped package.
2. If the package is installed (`getPackageInfo` succeeds), return it.
3. Else look up `DemoPackages.SUBSTITUTES`; if the substitute is installed, log the swap at `LifeOS/Focus` and return it.
4. Else return `null`.

Step 3 is why "block Instagram" is demoable on an emulator that has never had Instagram installed.

---

## Verification

Requires the emulator and granted permissions.

```bash
cd /home/sumit/lifeos
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# Grant the two special permissions without tapping through Settings
adb -s emulator-5554 shell appops set com.lifeos.app GET_USAGE_STATS allow
adb -s emulator-5554 shell appops set com.lifeos.app SYSTEM_ALERT_WINDOW allow

adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat -s LifeOS/Focus LifeOS/Exec
```

Cap yourself at two or three Gradle builds — seven other sessions are sharing the lock. On `Timeout waiting to lock`, wait 20 seconds and retry rather than deleting locks.

To exercise enforcement before `:ui` exists, add a temporary `debug`-only entry point in your own package (an `IntentService`-style extra on `FocusService`, for example `--es action seed_demo`), trigger it with `adb shell am start-foreground-service`, then open YouTube:

```bash
adb -s emulator-5554 shell am start -n com.google.android.youtube/com.google.android.apps.youtube.app.WatchWhileActivity
```

Delete that temporary entry point before you finish, or note it clearly in your handoff.

Acceptance checklist:

- [ ] `FocusService` starts as a foreground service with a visible ongoing notification, no `ForegroundServiceDidNotStartInTimeException`
- [ ] With usage access granted, `foregroundPackage()` returns `com.google.android.youtube` within about a second of opening YouTube
- [ ] Blacklist focus on YouTube shows the overlay, and it survives 30 seconds without flicker
- [ ] The overlay does not appear over LifeOS itself, the launcher, or Settings
- [ ] Whitelist focus with Chrome allowed shows the overlay on YouTube but not on Chrome
- [ ] "Back to work" sends the user to the launcher and the overlay disappears
- [ ] "Override 10 min" hides the overlay and it stays hidden for that app
- [ ] With `demoStrictTimeouts = true` and a 30-minute Instagram cap, the overlay fires on the substituted package after roughly one minute of use
- [ ] The overlay subtitle contains the real goal deadline phrase when a hard goal with a deadline exists
- [ ] `permissions()` reports all six fields correctly before and after the two `appops` grants
- [ ] `launchableApps()` returns Chrome, YouTube, Docs, and Maps with human-readable labels
- [ ] `resolveOrSubstitute("instagram")` returns `com.google.android.youtube` on this emulator
- [ ] Stopping focus removes the overlay and the service stops when no timeouts remain
- [ ] Nothing in `:enforce` calls `store.mutate` — grep to confirm

That last item is the design law. If you wrote a mutation, remove it and route the need through an `Action` instead.

## Timebox

85 minutes: usage helper 15, overlay 25, timeout logic 10, service 25, system access 10.

If behind at 65 minutes, cut in this order: scheduled focus windows (keep manual sessions and daily caps), then the label cache, then the `overlay_source` line. **Never** cut: the overlay itself, foreground detection, or the `ALWAYS_ALLOW` guard. Blocking SystemUI on stage ends the demo.

## Handoff notes for S9

Report the exact constructor calls:

```kotlin
val focusController = FocusController(app, lifeState)
val systemAccess = SystemAccessImpl(app)
val appCatalog = AppCatalogImpl(app)
```

State whether you left any temporary debug trigger in place, and confirm which `appops` commands S9 must run before the demo recording.
