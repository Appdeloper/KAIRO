# Google Play declarations for KAIRO

Play Console → App content → **Foreground service permissions**. For each type, Play asks for a
description, the user impact if the task is deferred or interrupted, a video link, and a use case.

## 1. `specialUse`: ShakeService

**Use case:** enter manually (no preset fits).

**Description (paste):**
> KAIRO is a voice-first daily planner. When the user turns on "Shake twice to open the briefing"
> in Settings, a foreground service listens to the accelerometer and opens the user's daily
> briefing when it detects a deliberate double shake. The service is started only by the user from
> a visible screen (the Settings toggle), shows a persistent "KAIRO shake is on" notification with
> a Stop button, stops listening while the screen is off, and ignores shakes outside the user's
> chosen active hours. No sensor data leaves the device or is stored.

**Why no other type fits:** it isn't media, location, health, connected-device, data-sync or
phone-call work. Android delivers accelerometer events to background apps only through a
foreground service (Android 9+), and no defined type covers motion-gesture detection.

**User impact if deferred or interrupted:**
> Shaking the phone stops opening the briefing until the user opens KAIRO again. The app detects
> this from a heartbeat, shows "Shake was stopped by your phone. Tap to fix", and the Quick
> Settings tile, home-screen widget and app shortcut keep working, so no data or plan is lost.

**Manifest property (already in AndroidManifest.xml):**
`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "Detects a shake gesture to open the user's daily briefing"

## 2. `mediaPlayback`: AlarmRingService (from step 1.5)

> Plays the user's alarm ringtone on the alarm stream when an alarm the user set goes off, until
> they snooze or dismiss it (auto-silenced after 10 minutes). If deferred, the user oversleeps.

## Demo video checklist (one video per type, about 30-60 s, unlisted YouTube link)

- [ ] Start on a fresh install; show Settings → Shake with the toggle off.
- [ ] Turn the toggle on; show the "KAIRO shake is on" notification with its Stop button.
- [ ] Show the sensitivity slider and the live meter reacting to a shake.
- [ ] Go to the home screen, double-shake: the briefing opens (or the "Tap to open your
      briefing" notification appears and is tapped).
- [ ] Turn the screen off and on again; shake still works.
- [ ] Tap Stop in the notification; the notification disappears and shaking does nothing.
- [ ] Alarm video: create an alarm, lock the phone, show it ringing on the lock screen, snooze,
      dismiss.

## Overlay permission (`SYSTEM_ALERT_WINDOW`) justification

KAIRO asks for "Display over other apps" only from Settings → Shake, and only as an optional
upgrade. Its sole use: after a deliberate double shake, it shows a small orb overlay for about a
second while it opens the user's own briefing screen. Android lets apps with a visible overlay
open their own activity from the background; without the permission, KAIRO falls back to a
tappable notification. The overlay never covers other apps' content for longer than that
second, isn't touchable, and is never shown without a shake the user made.

## Not requested, on purpose

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Play allows the direct exemption dialog only for
  specific app categories (messaging without FCM, safety, task automation, device companions).
  KAIRO links to the system battery-optimisation list instead.
- Full-screen intents for shake: reserved for alarms and calls; used only by the alarm.
