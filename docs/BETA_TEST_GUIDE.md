# KAIRO beta test guide (0.1.0-beta)

Thanks for testing KAIRO. This takes about 20 minutes. Do the parts in order the first time.

## 1. Install
1. On your Android phone (Android 8 or newer), open
   **https://github.com/Appdeloper/KAIRO/releases/download/kairo-debug-latest/kairo-debug.apk**
2. Download, open it, and allow "Install unknown apps" for your browser if asked.
3. Already have an older KAIRO beta? It updates in place; your data stays.
   If Android says the package conflicts, uninstall the old KAIRO once and install again
   (only needed for builds from before 6 Oct 2026).

## 2. First launch (onboarding)
1. You see the KAIRO logo and "Meet KAIRO". Tap **Let's begin**.
2. Enter your first name → wake and sleep times → lanes (rename one, switch one off) →
   permissions. Tap **Allow** on each, or **Later** to skip.
3. Turn on **Load a sample week** if you don't want to type your timetable now.
4. Tap **Start my day**.

## 3. Daily loop (Today tab)
- Check the greeting, the next-alarm chip and the **Now** card with its countdown.
- The amber line in the timeline is "now". Tap a task's circle to tick it off.
- Type in the bar at the bottom, e.g. `gym skip kar` or `add client call at 6pm`.
  A preview shows what will change: **Apply changes** or **Cancel**. After applying, tap **Undo**.
- Tap the glowing orb in the middle of the bottom bar: the briefing opens and listens.
  Say something like "aaj kya hai".

## 4. Plan tab
- **Timetable:** pick a day, tap **Add a class**, add one, then edit and delete it.
- **Tasks:** tap **Add task**. Swipe a task **right** (done) and **left** (drop, then Undo).

## 5. Alarms tab
1. Look at "Next alarm" and the health line. If it says something could stop an alarm, open it and tap **Fix** on each row.
2. Settings → Alarms → **Test alarm in 1 minute**. Lock the phone and wait.
   Pass: the ring screen shows over the lock screen with the big time; **slide to dismiss** works.
3. Create a wake-up alarm for 2 minutes from now and try **Snooze**.

## 6. Focus
On Today, tap a task row → pick 15 min → **Start**. Check the countdown in your notifications
(and the lock screen on Android 16+). Open it, tap **Add 10 min**, then **Done**, and write a next step.

## 7. Quick access
- Add the **Quick Settings tile** (Settings → Quick access → Add), the **home-screen widget**,
  and try the **long-press "Brief me"** shortcut. Each should open the briefing.
- Settings → Shake: turn it on and shake twice with the screen on.

## 8. Settings, data and privacy
- Turn **cloud AI** off: commands still work (handled on the phone).
- Turn on airplane mode with cloud AI on: Today and the briefing show "You're offline", and commands still work.
- **Export my data** shares a file; **Reset all data** asks first, then takes you back to onboarding.

## 9. Accessibility check (2 minutes)
- Phone Settings → Display → Font size: largest. Look at Today, Plan, Alarms and Settings. Nothing should be cut off.
- Turn on TalkBack and swipe through Today. Every button should be read with a name.
- Developer options → Animator duration scale: off. The orb should stand still.

## 10. Send feedback
Settings → About and feedback → **Send feedback**. Pick email or a chat app and write what happened.
The message already contains the app version, Android version, phone model and the last 50 app events.
It **never** contains your tasks, names, or anything you typed or said.

If KAIRO ever closes by itself, the next time you open it you'll be asked to **Share report** or **Dismiss**.
The report has the version, phone model and where the code failed, and nothing you typed.

## What to report
For each problem: what you did, what you expected, what happened, and a screenshot if you can.
Please mention your phone model and whether it was the first time you tried it.

## Known limitations in this beta
- Shake-to-open can be stopped by battery savers on some phones (Xiaomi, Vivo, Oppo, OnePlus, Samsung).
  Settings → Shake explains how to allow it; the tile, widget and shortcut always work.
- Live Updates (lock-screen countdown chip) need Android 16+. Older phones show a normal notification.
- In deep Doze, the focus "time's up" notice can be a few minutes late. Alarms are not affected.
- Cloud AI needs your own Worker URL and device token; without them KAIRO works fully on the phone.
