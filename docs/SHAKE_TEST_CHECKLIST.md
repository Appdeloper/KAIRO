# KAIRO shake test checklist (real device)

**Phones to use:** one stock Android phone on Android 16 or 17 (Pixel 8 or newer), plus at least
one aggressive-OEM phone on its current OS (Xiaomi/Redmi with HyperOS, Vivo, Oppo/Realme or
Samsung One UI). Write down model, Android version and OEM skin for every run.

Keep a log open: `adb logcat -s KairoShake ActivityTaskManager`
(`ActivityTaskManager` shows "Background activity launch blocked!" if Android refuses a launch.)

## 1. Turn it on
1. Settings → Shake → toggle on. Pass: "KAIRO shake is on" notification appears with Stop.
2. Health card shows "Shake is armed", a fresh heartbeat, restarts today 0.

## 2. Sensitivity meter
1. On Settings, shake gently and firmly. Pass: the meter moves and turns green above the trigger
   line; "detected" counts double shakes. The briefing does **not** open while this screen is open.

## 3. Background launch, each path (acceptance)
Press Home first so KAIRO is in the background, screen on.
1. **No overlay permission:** double-shake. Pass: haptic tick, then a "Tap to open your
   briefing" heads-up. Log: `shake launch path=notification(notification)`.
2. **Overlay granted** (health card → Display over other apps → allow): double-shake. Pass: a
   small orb flashes and the briefing opens. Log: `path=overlay`. If instead you see
   `overlay-blocked` and the notification, Android refused the launch: note the version.
3. **App visible:** with Today open, double-shake. Log: `path=direct`.
4. Single shake, or two shakes far apart: nothing happens.
5. Double-shake twice within 2 s: only one launch (debounce).

## 4. Screen off
1. Screen off for 1 minute, shake the phone, screen on. Pass: nothing opened while off.
2. Screen on, double-shake within a few seconds. Pass: works (sensor re-registered).

## 5. Active hours and charging
1. Set active hours to a window that excludes now; double-shake. Pass: nothing opens; log says
   `shake ignored (activeHours=false…)`.
2. Turn on "Only while charging", unplug, shake: ignored. Plug in, shake: opens.

## 6. Killed from recents (acceptance)
1. Swipe KAIRO away in recents. Wait 5 minutes with the screen on now and then.
2. Open the app (or the tile or widget). Pass, one of:
   - The service survived: health card says "armed", heartbeat under 3 minutes old, no card.
   - It was killed: "Shake was stopped by your phone. Tap to fix" card on Today and the briefing.
     Tapping re-arms it and "Restarts today" goes up by one.
3. On OEM phones repeat after following the OEM steps in the health card, and note the difference.

## 7. Reboot and app update
1. With shake on, reboot and unlock. Pass: the notification returns within a minute
   (`ShakeBootReceiver`), or a "Tap to re-arm shake" notification appears; tapping it re-arms.
2. `./gradlew :app:installDebug` over the existing install: same result (MY_PACKAGE_REPLACED).

## 8. Stop
1. Tap Stop in the notification. Pass: notification gone, toggle off, shaking does nothing,
   no "stopped" card (it's off, not broken).

## 9. Battery: 1-hour idle measurement (acceptance)
Run twice, once with shake off and once with shake on, same phone, same brightness, unplugged.
```
adb shell dumpsys batterystats --reset
adb shell dumpsys battery unplug
# leave the phone idle for 60 minutes: screen on with no touches for the first 10 (sensor active),
# then screen off for 50 (sensor unregistered)
adb shell dumpsys batterystats --charged com.kairo.app > kairo-batterystats.txt
adb shell dumpsys battery reset
```
Record from the output: the `Estimated power use (mAh)` line for the KAIRO uid and its
`Sensor` lines, plus wakelock time. Report: shake on minus shake off, in mAh per hour.
Android Studio's Power Profiler (Pixel 6+) gives the same per-rail numbers if you prefer.
