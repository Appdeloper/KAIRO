# KAIRO alarm test checklist (real device)

Run on at least one Pixel / stock Android phone and one OEM phone with aggressive battery
management (Xiaomi, Oppo, Vivo, Samsung). Note the Android version for each run.

Before you start
- Install a debug build: `./gradlew :app:installDebug`.
- Open **Settings → Alarms**. Every line on the health card should be green. Fix anything red with
  its **Fix** button before testing. (On Android 14+ check "Full-screen alarm screen allowed".)
- Keep a log open in a second terminal: `adb logcat -s KairoAlarm AlarmManager`.
- Check the clock is set automatically (Settings → System → Date & time).

For each test write down: expected time, actual ring time (stopwatch), pass or fail.

## 1. Ring with the screen off
1. Settings → **Test alarm in 1 minute**. Note the time shown on the health card ("Next alarm").
2. Press the power button so the screen goes off. Don't touch the phone.
3. Pass: within 2 s of the minute, the screen turns on, the ring screen shows, sound ramps up, the
   phone vibrates.

## 2. Ring on a locked phone
1. Make sure a PIN, pattern or fingerprint lock is set.
2. Schedule a test alarm, lock the phone, screen off.
3. Pass: the ring screen shows **over** the lock screen without unlocking. Snooze works without unlocking.
4. Slide to dismiss → the phone asks to unlock → after unlocking, the morning briefing opens and
   only then starts speaking.

## 3. Reboot, then ring
1. Create a one-time alarm 5 minutes ahead.
2. Reboot the phone. **Do not unlock it.**
3. Pass: the alarm rings before you unlock (direct boot). The ring screen shows the time and
   label but not the timeline (encrypted storage is still locked). That's expected.
4. Repeat, unlocking right after the reboot: it must still ring.

## 4. Swipe from recents, then ring
1. Schedule a test alarm.
2. Open recents and swipe KAIRO away. On OEM phones also try the "clear all" button.
3. Pass: it still rings on time.
4. Extra (OEM phones): Settings → Apps → KAIRO → **Force stop**. A force-stopped app gets no alarms
   until it's opened again; this is Android behaviour. Note it, but don't count it as a KAIRO failure.

## 5. Snooze until the limit
1. Create an alarm with snooze 5 min, max snoozes 3. Let it ring.
2. Snooze three times (button label counts down: 3 left, 2 left, 1 left).
3. Pass: on the 4th ring the button reads "No snoozes left" and is disabled. The status bar shows
   "Snoozed until …" between rings. Reboot during a snooze: the snooze still rings.

## 6. Phone call during ringing
1. Let an alarm ring. Call the phone from another phone and answer.
2. Pass: alarm sound pauses during the call (vibration may continue). After hanging up, the sound
   resumes. The alarm screen is still there.
3. Also: be on a call **when** the alarm time arrives. It should not blast into the call; it
   should start sounding after the call ends.

## 7. Do Not Disturb on
1. Turn on DND with default settings ("Alarms" allowed).
2. Pass: the alarm still rings at full ramped volume.
3. Turn on DND with alarms **blocked** (custom DND rule). Expected: Android silences it; the ring
   screen still shows. Note the result.

## 8. Time and time-zone changes
1. With a 7:00 weekday alarm set, change the time zone manually (turn off automatic zone).
2. Pass: Alarms tab and health card still show 7:00 local time; it rings at 7:00 in the new zone.

## 9. Auto-silence and missed alarm
1. Let an alarm ring without touching it for 10 minutes.
2. Pass: it goes quiet, the ring screen closes, a "Missed alarm" notification appears, and
   **Open KAIRO** opens the app.

## 10. Permission banners
1. Settings → Apps → Special app access → Alarms & reminders → KAIRO off (if your device lets you
   change it), or revoke notifications or full-screen intents.
2. Pass: the Alarms tab, Today (if any alarm is on) and the Settings health card all show the
   problem with a **Fix** button that opens the right system screen.
