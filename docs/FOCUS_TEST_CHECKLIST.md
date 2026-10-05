# KAIRO focus test checklist (real device)

**Phones to use:**
- **Live Update path:** a phone on **Android 16 or 17** (Pixel 8 or newer is the reference).
  Live Updates (promoted notifications, the status-bar chip and ProgressStyle) only exist from
  Android 16. Some OEMs add their own eligibility rules, so note the model and skin.
- **Fallback path:** any phone on **Android 15 or older** (Android 13 or 14 is ideal). It shows a
  normal ongoing notification with a countdown and a progress bar.

Write down the model, Android version and skin for every run.

Keep a log open: `adb logcat -s KairoFocus`

Useful commands:
- `adb shell dumpsys alarm | grep -A3 com.kairo.app` shows the pending end alarm (`focus.TIME_UP`)
  and the progress tick (`focus.TICK`).
- `adb shell dumpsys notification --noredact | grep -A20 com.kairo.app` shows the posted
  notification. On 16+ look for `FLAG_PROMOTED_ONGOING` in the flags.
- `adb shell cmd notification set_dnd off` puts DND back by hand if a test leaves it on.

## 1. Start from the timeline
1. Today → tap a task row. Pass: the "Start focus" sheet opens with the task's length selected.
   The check icon on the right still ticks the task off and doesn't open the sheet.
2. Tap a lecture row. Pass: the sheet opens with the lecture's length (capped at 240 min).
3. Pick 15, 25, 45 or 60; then Custom and drag the slider. Pass: the Start button shows the minutes.
4. Start. Pass: snackbar "Focus started", a "Focus · mm:ss left" chip on Today, a notification.
5. Tap another row while one session runs. Pass: the sheet says "Already focusing on …" with
   "Open it", and Start is disabled.

## 2. Lock screen countdown (acceptance)
1. Start a **custom 2-minute** session (Custom chip, drag the slider to 2). Note: Android's
   status-bar chip only shows a *time* when the end is at least 2 minutes away, but the
   chronometer in the notification itself counts down for the whole session.
2. Lock the phone and turn the screen off for 1 minute. Turn it on (don't unlock).
   - **Android 16+:** the session is at the top of the lock screen, expanded, with the title,
     the role colour, a counting-down timer and a progress bar. The status bar shows a chip with
     the countdown once unlocked.
   - **Android 15 and older:** a normal notification with a counting-down timer and a progress bar.
3. Compare the countdown with a stopwatch. Pass: within a few seconds.
4. With "hide sensitive content" on the lock screen, the card says "Focus session" without the
   task title.

## 3. Time's up arrives on time (acceptance)
1. Leave the phone locked with the screen off until the end.
2. Pass: within about a minute of the end, a "Time's up: …" heads-up appears with
   "Write next step". The ongoing notification is gone. **No** screen opens by itself and there
   is **no** full-screen alarm UI.
3. Log shows `focus finish id=… outcome=DONE reason=TIME_UP`.
4. Note how late it was. In deep Doze (phone still and unplugged for a long time) Android may
   hold this alarm by several minutes; that's documented behaviour, see limitations.

## 4. Notification actions with the app closed
Swipe KAIRO away from Recents first.
1. **+10 min:** the timer jumps 10 minutes; on 16+ a second segment appears on the progress bar.
   `dumpsys alarm` shows the end alarm moved.
2. **Drop:** the notification disappears, no "time's up", log `outcome=DROPPED`.
3. **Done:** "Nice work on …" with "Write next step"; log `outcome=DONE` (or `EXTENDED` after +10).
4. In each case no KAIRO screen opens.

## 5. Next step (acceptance)
1. Tap "Write next step". Pass: the next-step screen opens with any existing next step filled in.
2. Type a line and Save. Pass: Today shows "Next: …" under that task; the briefing's "Up next"
   shows it too.
3. Again, but tap the mic: the permission prompt appears only here; speak; the text fills in.
4. Skip: nothing changes and the notification goes away.
5. For a lecture session, Save keeps the text on the session (it doesn't show on the timeline yet).

## 6. In-app session screen
1. Tap the Today chip. Pass: big countdown, ring in the role colour, Drop / +10 min / Done.
2. Done: switches straight to the next-step screen (no notification).
3. Drop: closes, chip disappears.

## 7. Reboot mid-session (acceptance)
1. Start a 15-minute session and reboot after 3 minutes. Unlock.
   Pass: within a minute of unlocking the notification is back with about 11 to 12 minutes left,
   `dumpsys alarm` shows a new end alarm, and log shows `focus resumed`.
2. Start a 5-minute session, power off, wait 7 minutes, power on, unlock.
   Pass: "Your focus on … ended" with "Write next step", the chip is gone, and log shows
   `focus closed after recovery`.

## 8. Force stop
1. Start a session, then Settings → Apps → KAIRO → Force stop (this cancels alarms and notifications).
2. Open KAIRO. Pass: the session resumes (notification back) or, if the end passed, it closes
   with the "ended" notification.

## 9. Swipe the Live Update away
1. On an **unlocked** phone, swipe the ongoing notification away (Android 14+ allows this).
2. Pass: it does not come back on the next progress tick or app open (log shows no re-post). The
   end alarm still fires and "Time's up" still arrives.
3. Tap +10 min in the app: the notification comes back (an explicit request).

## 10. Do Not Disturb (optional feature)
1. Settings → Focus → "Silence notifications during focus". Pass: an explanation dialog → Open
   settings → allow KAIRO → back: the switch is on.
2. Start a session. Pass: DND is on (Android 15+: shown as a KAIRO mode; older: priority only,
   or alarms only if your priority settings block alarms).
3. During the session, set a test alarm for 2 minutes ahead (Settings → Alarms → Test). Pass:
   it rings at full volume.
4. End the session. Pass: DND is back to what it was before.
5. Turn DND on yourself before starting (Android 14 and older). Pass: KAIRO leaves it alone and
   doesn't turn it off at the end.
6. Start a session with DND enabled, force-stop KAIRO, then open it after the end time. Pass: DND
   is restored.

## 11. Live Update permission hint (Android 16+)
1. System Settings → Notifications → KAIRO → turn off Live Updates (the wording varies by skin).
2. KAIRO Settings → Focus. Pass: a card says Live Updates are off, with a button to the setting.
3. On Android 15 or older the card says Live Updates need Android 16.

## 12. Fallback when notifications are blocked
1. Turn KAIRO notifications off. Open the Start focus sheet. Pass: a red line says the countdown
   only shows inside the app. The session still runs and the Today chip still counts down.
