# KAIRO UI audit (before the redesign)

Screenshots: `docs/screenshots/before/` (21 images, rendered with Roborazzi on Robolectric,
393 × 851 dp phone at xhdpi). Re-render with `./gradlew :app:recordRoborazziDebug`; files land
in `app/build/outputs/roborazzi/`.

> Harness note: the "before" harness draws each screen inside a plain `Box`, not a themed
> `Surface`. Screens that sit inside a `Scaffold` or a bottom sheet in the real app (Today, Plan
> diff, Start focus, Settings) therefore show black text in these images that the app itself
> doesn't show. Screens marked **real bug** below have the same problem in the running app,
> because their activity also has no themed surface.

## 1. Inventory

### Activities and screens
| Where | Composable | Notes |
|---|---|---|
| MainActivity → onboarding gate | `OnboardingScreen` / `OnboardingContent`, `ProfileForm` | one page: name, wake, sleep, wake-alarm checkbox |
| MainActivity → tab Today | `TodayScreen` / `TodayContent`, `CommandBar`, `NextAlarmChip`, `FocusChip`, `ShakeStoppedCard`, `AlarmPermissionBanner` | |
| MainActivity → tab Timetable | `TimetableScreen` / `TimetableContent` (week grid) | |
| MainActivity → tab Tasks | `TasksScreen` / `TasksContent` | |
| MainActivity → tab Alarms | `AlarmsScreen` / `AlarmsContent`, `DayChips` | |
| MainActivity → tab Settings | `SettingsScreen` / `SettingsContent` + `AlarmSettingsSection`, `ShakeSettingsSection` (+ `ShakeHealthCard`, `OemFixCard`, `ShakeTestMeter`), `FocusSettingsSection`, `AiSettingsSection`, `QuickAccessSection` | |
| BriefingActivity | `BriefingContent`, `Orb` (AGSL + fallback), `ParticleBurst` | translucent window |
| AlarmRingActivity | `AlarmRingScreen` (slide to dismiss) | shows over lock screen |
| FocusActivity | `FocusSessionContent`, `NextStepContent` | |

### Sheets and dialogs
- `PlanDiffSheet` (change preview and agenda), `AddTaskSheet` (+ date picker), `BlockEditorSheet`,
  `AlarmEditorSheet`, `FocusStartSheet`.
- `TimePickerDialog` (shared), the "put on today at…" time picker in Tasks, the DND-access dialog in Focus settings.

### Notifications (11)
- **Alarms:** ringing (full-screen intent), ring fallback, missed, snoozed.
- **Shake:** "shake is on" status, "tap to open your briefing", "tap to re-arm".
- **Focus:** ongoing countdown / Live Update, "time's up / next step".
- All use `ic_tile_orb` as the small icon. Only focus sets an accent colour; the others use the system default.

### Widget, tile, shortcut
- Glance `KairoWidget` (orb image, greeting/next line, "Brief me").
- Quick Settings `BriefTileService`.
- Static shortcut "Brief me".

## 2. Problems found

### Color and contrast
1. **Real bug: black text on near-black** in BriefingActivity, FocusActivity and Onboarding. None of
   them sits on a themed `Surface`, so plain `Text` falls back to black. Seen in `briefing.png` (Up
   next titles nearly invisible), `focus_session.png` (task name and the 17:32 countdown),
   `focus_next_step.png` (title and hint), `onboarding.png` (wake/sleep labels, checkbox label).
2. **Palette doesn't match the brand.** Magenta `#FF2BD6` and lime `#B6FF3B` are used for warnings,
   selected chips and "OK" ticks. The brand is cyan → blue with amber only for "the right moment".
   Magenta warning cards (`alarms.png`) read like errors and clash with the Content lane pink.
3. **Background is `#07070B`, not the brand `#05070F`**; surfaces are grey (`#0F0F16`, `#181822`),
   not navy glass.
4. **Role colours too close to each other and to UI colours:** College `#00E5FF` equals the
   primary cyan, so College blocks look like selected/primary UI (`timetable.png`).
5. **The orb is magenta-dominant** (`briefing.png`, `alarm_ring.png`), while the logo orb is cyan-to-blue.
6. **Pure white `#FFFFFF`-ish 7:00 AM on the ring screen**: harsh in a dark room.
7. **"Offline brief" label is tiny and very low contrast** (`briefing_listening.png`).

### Typography
8. Only the system font (Roboto); no display face, so headings have no brand character.
9. Times and countdowns use proportional digits, so "17:32" → "17:31" jumps width.
10. **Inconsistent heading sizes:** Today greeting is headlineMedium bold; Onboarding uses displaySmall in
    cyan; Settings sections use titleMedium in cyan; Tasks sections use titleSmall.

### Layout and spacing
11. **Spacing values are ad hoc:** 6, 10, 12, 14, 20 and 24 dp are mixed with no grid. Card radius is
    20 dp in some places and 28 dp in others.
12. **Alarms day chips overflow:** the 7th chip (Sunday) is clipped at the right edge in
    `alarms.png` and `alarms_font200.png`.
13. **Alarm row reads "Wake up · off"** while its switch is ON; the label state is confusing.
14. The timetable is a tiny 7-column grid on a phone: block titles wrap or clip ("DBMS lecture"),
    and the grid starts at 7:00 with no indication of the current time.
15. The **Today progress card** is a large card for just "1 / 3 tasks done"; it takes a quarter of
    the screen above the timeline.
16. **Plan-diff rows** use the same icon colour family for "added" and "warning", and give no
    before → after structure; the Apply button floats right with no emphasis.
17. **The Focus start sheet is cramped:** chips wrap to a second row, and the Start button sits right
    under the chips.
18. The FAB "+" sits on top of list content on Tasks/Timetable/Alarms with no bottom padding in some cases.

### States
19. **Empty states** are plain grey sentences: Today, Tasks, Briefing "Nothing else on today".
    There is no illustration and no action button (except the Alarms wake-alarm card).
20. **The Briefing loading state** is just the orb with nothing explaining what's happening.
21. **No loading or error state** on Today (shows "0 / 0 tasks done" before data arrives).
22. **Permission problems** are shown three different ways: a magenta card, `HealthRow` in a grey
    card, and plain `TextButton`s.

### Font scale 200%
23. `today_font200.png`: the greeting breaks into three lines and pushes "Brief me" down; the date
    wraps.
24. `settings_font200.png`: the "Usually wake up at" label wraps under the time button, and the AI
    explanation is a wall of magenta text.
25. `alarms_font200.png`: the Sunday day chip is fully hidden.

### Navigation and structure
26. **Five bottom tabs** (Today, Timetable, Tasks, Alarms, Settings); Timetable and Tasks are two
    halves of planning.
27. The **briefing is only reachable from a small "Brief me" button** on Today (plus tile, widget and
    shortcut). There is no persistent voice entry point.

### Notifications and widget
28. Notifications have no consistent accent colour; titles mix styles ("Alarms may not ring
    reliably", "Tap to open your briefing", "KAIRO shake is on").
29. The widget uses hardcoded `#0F0F16` / `#ECECF4` and a 13 sp label; not on brand.

### Accessibility
30. Several icon buttons have `contentDescription = null` where the icon is the only label (FAB
    "+" has one, but the Today trailing state icons and some chips don't), and the role is
    signalled by colour only (dot + strip, no text in the Timetable grid).
31. Touch targets: the day chips on Alarms are about 40 dp wide.

## 3. What the redesign must fix
All of the above. The Part 1 tokens fix 1 to 11; components fix 12 to 22; the Part 2 navigation fixes 26 and 27; the
Part 3 screens fix the rest; and the Part 7 "after" screenshots check each item again.

## 4. After the redesign

"After" screenshots: `docs/screenshots/after/` (56 images, same harness, now inside `KairoTheme`),
including 200% font versions of Today, Plan tasks, Alarms, ring screen, Briefing, Focus, Onboarding and
Settings, plus long content (`today_long.png`, 50 items) and the beta states (`whats_new.png`,
`crash_prompt.png`, `today_offline.png`, `briefing_offline.png`). Launcher icon under three masks:
`docs/screenshots/icon/icon_masks.png`.

| # | Problem | Fixed by |
|---|---|---|
| 1 | Black text without a themed surface | `KairoTheme` provides `LocalContentColor`; every screen checked in the after set |
| 2–5 | Off-brand palette and orb | Part 1 colour roles; lane colours distinct from primary; orb recoloured cyan → blue |
| 6 | Harsh white ring time | Soft white `#F2F8FF` on navy, auto-sized to fit |
| 7 | Tiny "Offline brief" label | `StatusPill`, plus an offline banner when cloud AI is on |
| 8–10 | Fonts and headings | Sora/Inter, one type scale, tabular figures for every time |
| 11 | Ad hoc spacing | 4/8 grid tokens, radius 8/16/24 |
| 12, 25, 31 | Day chips clipped, under 48 dp | `DayPicker` with full-width 48 dp chips (AccessibilityTest) |
| 13 | "off" label vs ON switch | Subtitle follows the switch; disabled rows dimmed |
| 14 | Tiny week grid on phones | Day picker + list on phones; grid only on wide screens |
| 15 | Oversized progress card | Progress ring inside the Now card |
| 16 | Plan diff structure | Icon, label and before → after per row; full-width Apply |
| 17 | Cramped focus sheet | Redesigned sheet with clear spacing |
| 18 | FAB over content | No FABs; actions in the top bar, bottom-bar clearance on lists |
| 19–21 | Empty and loading states | `EmptyState` with actions; `LoadingOrb` until data arrives |
| 22 | Three permission styles | `Banner`, `PermissionCard` and one health section |
| 23–24 | 200% font breakage | Stacked timeline layout, wrapping fields, tested at 200% |
| 26–27 | Navigation | Four tabs and the centre orb button |
| 28–29 | Notifications and widget | One brand accent and sentence-case titles; widget on brand colours |
| 30 | Unlabelled controls, colour-only signals | AccessibilityTest; lanes always shown with a name |
