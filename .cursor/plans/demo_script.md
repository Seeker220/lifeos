---
title: LifeOS 90-second demo script
---

# LifeOS demo — 90 seconds

## Pre-flight

```bash
cd /home/sumit/lifeos
ADB="adb -s emulator-5554"
./gradlew :app:assembleDebug
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell appops set com.lifeos.app GET_USAGE_STATS allow
$ADB shell appops set com.lifeos.app SYSTEM_ALERT_WINDOW allow
$ADB shell appops set com.lifeos.app SCHEDULE_EXACT_ALARM allow
$ADB shell pm grant com.lifeos.app android.permission.POST_NOTIFICATIONS
$ADB shell am force-stop com.lifeos.app
$ADB shell am start -n com.lifeos.app/.MainActivity --es demo seed
```

Optional compaction setup (after seed):

```bash
$ADB shell am start -n com.lifeos.app/.MainActivity --es demo fill_chat
```

Arm the 60s alarm from **More → Test alarm in 60s** just before you start talking.

VPN consent cannot be granted over adb. Accept the system dialog once if you want the network-guard beat. Instagram is not on this emulator — LifeOS substitutes YouTube.

APK: `LifeOS-demo.apk` (debug). The live LLM is wired via `local.properties` (LiteLLM, OpenAI-compatible) — confirm with `adb logcat -s LifeOS/Agent` showing `LLM configured: true` and `turn source=LLM`. If the endpoint is unreachable the app silently falls back to offline matches (`turn source=OFFLINE_FALLBACK`), so the demo still runs.

Type any sentence during the demo — expansion is generated live, not scripted. To drive a turn from the terminal (note the inner quotes, adb strips outer ones):

```bash
$ADB shell "am start -n com.lifeos.app/.MainActivity --es say 'cap reddit at 15 minutes'"
```

## Beats

1. **The claim (10s)** — Chat empty or seeded. *"Every hackathon has five AI todo apps. None of them can stop you opening Instagram."*

2. **One sentence, twelve changes (20s)** — Tap suggestion **"Crack a Google interview in 1 month"**. Wait for chips. Tap **Goals**, then **Today**.

3. **It is real device state (20s)** — **Focus** tab: Instagram/YouTube caps attributed to the goal. Tap **Start Focus**. Leave the app. Open YouTube. Overlay slams in. Tap **Back to work**.

4. **It wakes you up (15s)** — Pre-armed alarm fires. Full-screen (or high-priority notification if full-screen intent is denied). Persona line speaks if TTS is present.

5. **It does not forget (15s)** — **More → Compact chat**. Message count drops. Life-state counts stay the same. *"The conversation compacts. Your life doesn't."*

6. **It triages your inbox (10s)** — **Inbox → Load sample**. Promote the midterm. It appears on **Today**.

## Failure playbook

- LLM stalls → a turn takes 3-11s; the chat shows a typing row. If the endpoint is down it falls back automatically, and the suggestion chips always match an offline expansion.
- Overlay missing → re-run the two `appops` grants; usage access returns empty when denied.
- Alarm silent → check the shade for the high-priority `lifeos_alarm` notification (API 37 often denies full-screen intent).
- VPN cut / no consent → skip any network-guard mention; overlay still works.
- A tab crashes → stay on Chat / Today / Focus and keep talking.
