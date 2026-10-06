# KAIRO design system

Code: `app/src/main/java/com/kairo/app/ui/design/`. Screens read tokens through `KairoTheme`
(`KairoTheme.colors`, `KairoTheme.type`, `KairoTheme.numbers`) and use the components in
`ui/design/components`. No raw hex, dp-literal colours or font sizes in screens.

Feel: a calm, intelligent holographic assistant. Dark, minimal, glowing like an instrument. The orb
is the hero. Dark theme only.

## Colour roles
| Role | Value | Use |
|---|---|---|
| background | `#05070F` | every screen |
| surface1 / 2 / 3 | `#0C1222` / `#121A30` / `#1A2440` | navy glass: cards / raised cards and sheets / inputs and nested |
| outline / outlineStrong | `#2A3556` / `#3B4874` | hairlines (decorative; never the only signal) |
| primary → primaryEnd | `#00E5FF` → `#4D7CFF` | primary gradient: main buttons, progress, orb |
| onPrimary | `#04121C` | label on the gradient |
| textPrimary / Secondary / Tertiary | `#F2F8FF` / `#AAB6D3` / `#8590B0` | soft white, never pure white |
| success / warning / error | `#4ADE80` / `#FF9466` / `#FF6B81` | status, always with an icon or text |
| moment (amber) | `#FFB020` | **only** the now marker, the one key highlight per screen, the logo spark |

## Lanes
| Lane | Colour | Tint (backgrounds) |
|---|---|---|
| College | `#38D9F5` cyan | 18% over surface1 |
| Intern | `#9B8CFF` blue-violet | 18% over surface1 |
| Client | `#3EE8A8` mint | 18% over surface1 |
| Content | `#FF6FB7` pink-magenta | 18% over surface1 |

Roles are matched to lanes by their stored colour, including the old pre-redesign defaults, so
existing installs pick up the new palette without a database change. A lane always shows its name
next to its colour.

## Contrast (WCAG 2.2)
Normal text needs 4.5:1; large text and icons need 3:1. Enforced by `ContrastTest`.

| Foreground | background #05070F | surface1 #0C1222 | surface2 #121A30 | surface3 #1A2440 |
|---|---|---|---|---|
| textPrimary `#F2F8FF` | 18.82 | 17.46 | 16.15 | 14.34 |
| textSecondary `#AAB6D3` | 9.91 | 9.19 | 8.50 | 7.55 |
| textTertiary `#8590B0` | 6.34 | 5.88 | 5.44 | 4.83 |
| primary `#00E5FF` | 13.08 | 12.13 | 11.23 | 9.96 |
| primaryEnd * `#4D7CFF` | 5.41 | 5.01 | 4.64 | 4.12 |
| success `#4ADE80` | 11.55 | 10.71 | 9.91 | 8.79 |
| warning `#FF9466` | 9.27 | 8.60 | 7.96 | 7.06 |
| error `#FF6B81` | 7.35 | 6.82 | 6.31 | 5.60 |
| moment (amber) `#FFB020` | 11.00 | 10.20 | 9.44 | 8.38 |
| lane College `#38D9F5` | 11.89 | 11.03 | 10.21 | 9.06 |
| lane Intern `#9B8CFF` | 7.27 | 6.74 | 6.24 | 5.54 |
| lane Client `#3EE8A8` | 12.75 | 11.83 | 10.95 | 9.72 |
| lane Content `#FF6FB7` | 7.87 | 7.30 | 6.75 | 5.99 |

\* primaryEnd is 4.12 on surface3, so it is used only in gradients, large text and icons (≥ 3:1), never
for small text.

- **onPrimary `#04121C` on the gradient:** 12.32 at the cyan end and 5.09 at the blue end.
- **Text on lane tints:** textPrimary is 13.6 or more; textSecondary is 7.2 or more.
- **Banner tints:** the text and the accent icon on each banner tint are checked in `ContrastTest.bannerTintsKeepTextReadable`.

## Type
**Fonts:** Sora (display, headings) and Inter (body, labels), bundled in `res/font`, both SIL OFL 1.1
(`docs/licenses`).

| Style | Font | Size/line | Use |
|---|---|---|---|
| displayLarge/Medium/Small | Sora SemiBold | 52/60, 40/48, 32/40 | onboarding hero, ring time |
| headlineLarge/Medium/Small | Sora SemiBold | 28/36, 24/32, 20/28 | screen titles |
| titleLarge | Sora SemiBold | 18/24 | card titles |
| titleMedium/Small | Inter SemiBold | 16/22, 14/20 | row titles |
| bodyLarge/Medium/Small | Inter Regular | 16/24, 14/20, 12/16 | text |
| labelLarge/Medium/Small | Inter SemiBold/Medium | 14/20, 12/16, 11/16 | buttons, chips |
| numbers.hero/large | Sora SemiBold, **tabular** | 72/80, 44/52 | alarm ring, focus countdown |
| numbers.medium/small | Inter, **tabular** | 22/28, 13/18 | times in lists, counts |

## Spacing, shape, stroke, elevation
- **Spacing (4/8 grid):** 2, 4, 8, 12, 16, 24, 32, 48 dp. Screen margin 20 dp. List bottom clearance 120 dp (bottom bar and orb button).
- **Radius:** 8, 16, 24 dp and full (pills).
- **Stroke:** hairline 1, regular 1.5, strong 2, ring 8 dp.
- **Elevation:** glass, not shadow.
  - FLAT and LOW: a hairline whose top edge is brighter.
  - RAISED: a brighter edge.
  - GLOW: a soft halo in the primary or lane colour.
- **Touch target:** 48 dp minimum for every button, chip and row.

## Motion
- **Durations:** short 150 ms, medium 250 ms, long 400 ms.
- **Easing:** the standard curve (0.2, 0, 0, 1), and a gentle low-bounce spring.
- **Reduced motion:** when the system "Remove animations" setting is on (animator scale 0), `KairoTheme.reducedMotion` is true. Non-essential motion snaps, and the orbs are drawn static.

## Haptics
`rememberKairoHaptics()` provides two effects: `tick()` when something is applied (Apply, Undo, Extend) and `success()` when
something is completed (task done, focus done). Nothing fires on scroll.

## Components
- **Structure:** `KairoScaffold`, `TopBar`, `KairoBottomSheet` (+ `SheetFrame` for previews).
- **Surfaces:** `GlassCard`, `Modifier.glass`.
- **Buttons:** `PrimaryButton` (gradient), `SecondaryButton`, `KairoTextButton`, `KairoIconButton` (48 dp, optional filled gradient).
- **Chips and pills:** `RoleChip`, `StatusPill` (tones incl. MOMENT), `ChoicePill`, `SegmentedControl`.
- **Rows:** `SectionHeader` (TalkBack heading), `ListRow`, `ToggleRow` (whole row toggles), `SliderRow`.
- **Timeline:** `TimelineItem` (time column, lane rail, glass card, state pill), `NowMarker` (amber).
- **Progress and orb:** `ProgressRing` (gradient arc), `LoadingOrb` / `OrbGlyph`.
- **Feedback:** `EmptyState`, `PermissionCard`, `Banner` (info, warning, error), `KairoSnackbarHost`.

Each has a `@Preview`; `DesignSystemScreenshots` renders a gallery (`ds_controls.png`, `ds_feedback.png`).
