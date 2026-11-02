# Exhale — Product & Engineering Spec

This is the source of truth for humans and AI agents working on this repo.
When code and this spec disagree, fix one of them in the same change and say which.

---

## 1. Product

**Exhale** is an Android app that helps people track, regulate and reduce cigarette
consumption. It supports two goals equally:

- **Cut down** (default): a daily allowance that tapers over time.
- **Quit**: the same tools, with the allowance tapering to zero.

### Users
Adult smokers who want control over their smoking, often not ready to quit outright.
Many have tried and slipped before. They are sensitive to judgement.

### Core jobs (in priority order)
1. **Log a cigarette in under 2 seconds**, from anywhere.
2. **Know whether "now" is within plan**: how long since the last one, when the next one is earned.
3. **Get through a craving** without smoking.
4. **See patterns**: when and why they smoke.
5. **Feel progress**: body recovery and money kept.

### Evidence the design rests on
| Finding | Design consequence |
|---|---|
| Cravings peak and fade within ~3–5 min (NHS, CDC guidance) | Urge Wave: a timed 4-minute wave with paced breathing |
| Reduce-to-quit ≈ abrupt quitting in quit rates (Lindson et al., Cochrane 2019) | Regulate mode is first-class, not a fallback |
| Self-monitoring reduces automatic smoking | One-gesture logging on every surface |
| Triggers are predictable; if-then plans help | Optional trigger tags + hour heatmap + if-then suggestion |
| Abstinence-violation effect: one slip → giving up | **No streak resets, no shame copy, no red error states for smoking** |
| Recovery timeline is well documented (WHO) | Recovery milestones with sources shown in-app |

---

## 2. Design language

The full design system (tokens, components, usage rules) is in [`DESIGN-SYSTEM.md`](DESIGN-SYSTEM.md).
In short:

- **Two temperatures**: warm = smoke (`ember`, `flare`), cool = recovery (`sky`, `oxy`), `sun` = money.
  Soft pastel accents on one shared background. `c.heat(h)` maps heat 1 (just smoked) → 0 (earned).
- **Accents are fills**; text and small icons in an accent use its `*Ink` token (`c.inkOf(accent)`),
  identical in dark, darker in light for 4.5:1.
- **Type**: Newsreader (serif) for feelings and big states, Figtree (sans) for reading and tapping,
  tabular digits for timers. Use `ExType` styles only.
- **Layout**: every screen in `ExScreen` (shared `bg`, gutter, insets, tab-bar clearance). One primary
  60dp pill button per screen. Floating iOS-style tab bar, icons only.
- **Themes**: dark and light; follows the phone, override in Settings → Appearance (`ThemeMode`).
- Colour is never the only signal: always pair with a number or label.

### Haptics vocabulary (`ui/components/Haptics.kt`)
| Pattern | Meaning |
|---|---|
| `charge(level)` rising ticks | Holding to log |
| `logged()` thud | Log committed |
| `earned()` double tick | Next cigarette earned (Regulate) |
| `breath()` low tick | Breath phase change (Urge Wave) |
Don't reuse a pattern for a different meaning.

### Copy rules
- Friendly, plain, short. Second person.
- Never: "failed", "relapse", "streak lost", "bad", exclamation marks on smoking events.
- A cigarette is information: "Logged. 8 of 12 today."
- Over allowance = "borrowed from tomorrow", shown in Sun/Flare, never an error.

---

## 3. Screens

Floating tab bar (icons only): **Ember · Regulate · Rhythm · Air · Money**. Full-screen routes: **Urge Wave**, **Settings**.
First launch shows **Onboarding** (§3.7).

### 3.1 Ember (home) — `EmberScreen.kt`
- Header: today's count / allowance (Flare when at/over), Settings link.
- Hero: time since last cigarette (`Fmt.gap`), subline with time until next earned.
- **Orb** (`EmberOrb.kt`, concept "Gather" from the Ember Lab): a molten core coloured by `heat`
  with four droplets orbiting it. At rest the core pulses every 4 s and the drops drift in and out,
  sometimes touching the core and merging with a liquid bridge (blur + alpha threshold RenderEffect,
  Android 12+; plain drops below that).
- **Hold to log**: press 1.5 s. The drops are pulled in and merge, the core swells and warms, a
  trail runs around the ember along the heat scale, and 12 `charge()` ticks rise in strength;
  release early cancels. On completion a `logged()` thud, the drops fling out with a ring and a
  splash, and a log is written.
- **Toast** after logging: count, **Undo**, optional trigger chips. Auto-dismisses after 8 s.
- "I want one. Help me wait." → Urge Wave.

### 3.2 Regulate — `RegulateScreen.kt`
- Ring = elapsed / interval since last cigarette, with 60 ticks. Centre: countdown or "Now".
- Allowance sticks: used (Sun→Ember), next slot (dashed Oxy), over-allowance (Flare), remaining (Line).
- Borrowed note when yesterday went over.
- Plan card: this week's base allowance and next week's.
- `earned()` haptic when the countdown reaches zero while on screen.

### 3.3 Urge Wave — `UrgeWaveScreen.kt`
- 240 s wave; crest at ~46 % (minute ~2). Dot rides the curve.
- Breath pacing 4-2-6 (in-hold-out), `breath()` haptic on phase change.
- Headline copy changes with progress (rising, crest, fading).
- Breathing guide (`BreathGuide.kt`), text only: the current instruction fills letter by letter over
  its duration; the next ("then hold · 2s") waits below and brightens in the final second; on a
  change it glides up and grows into place. The wave dot's halo swells and shrinks with the breath.
- Optional trigger selection.
- Before end: "I'm okay now" (SURVIVED) / "Smoke anyway" (SMOKED + log).
  After end: "No. I rode it out." / "Yes, log one". "Close" records nothing.

### 3.4 Rhythm — `RhythmScreen.kt`
- 24-hour radial heatmap ("Smoke Clock"), hour 0 at top, clockwise. Wedge heat = avg per day.
- Toggles: Last 30 days / 30 days before; All / Weekdays / Weekends.
- Tap a wedge → hour card with per-day average, top trigger and an if-then suggestion.
- Trigger bar list; count of craving waves ridden out in the period.

### 3.5 Air — `AirScreen.kt`
- Particle field: ash-grey particles turn Oxygen as `clearFraction` (progress to 12 h) rises.
- Time since last; longest gap in 7 days.
- WHO milestones list; locked ones show a countdown. Source line at the bottom.

### 3.6 Money — `MoneyScreen.kt`
Same background as every screen; money is carried by `sun` accents.
- Saved = (baseline × days since plan start − cigarettes smoked since) × price per cigarette, floored at 0.
- Sticks row: fraction of the current hundred not smoked.
- Goal card with progress and weeks-to-goal at current pace.

### 3.7 Onboarding — `OnboardingScreen.kt`
One question per screen. Nothing is saved until the last step; system Back goes to the previous step.
A segmented progress bar (steps 1–4) fills with colours that cool from Ember to Oxygen.

| Step | Asks | Notes |
|---|---|---|
| 0 Welcome | — | Thesis, warm/cool gradient legend, privacy line. "Get started". |
| 1 Habit | Cigarettes a day before Exhale | Big stepper, 1–60, default 15. |
| 2 Cost | Pack price, cigarettes per pack, currency | Currency defaults from the device locale. Live line: cost per cigarette and per month. Next is disabled until valid. |
| 3 Plan | Starting allowance + taper type | Stepper 0..baseline, default baseline − 2. Taper cards preview weeks 1–4. |
| 4 Goal (optional) | Goal name and amount | Shows money kept in the first 4 weeks if the plan is followed. "Start" or "Skip the goal for now". |

No demo data in onboarding.

### 3.8 Settings — `SettingsScreen.kt`
Appearance (System / Light / Dark, applies instantly) and all onboarding fields in one form; keeps the original plan start.
Prototype tools: Design system (opens `UiKitScreen`, the live component catalogue).
Prototype tools: replace data with 30 days of demo data (explained in-screen), erase all data (two-tap confirm).

### 3.9 Outside the app
- **Quick-settings tile** — `tile/LogTileService.kt`: tap to log; subtitle shows today's count.
- Planned: Glance home-screen widget, Wear OS tile, smart nudges (see §6).

---

## 4. Domain rules (`domain/`, pure Kotlin, unit-tested)

### Allowance — `PlanMath`
- `weeksIntoPlan = floor(daysSince(planStart) / 7)`, 0 before start.
- `MINUS_ONE_WEEKLY`: `max(0, start − weeks)`
- `TEN_PERCENT_WEEKLY`: `max(0, min(ceil(start × 0.9^weeks), start − weeks))` (always ≥ 1 step/week)
- `HOLD`: `start`
- **Debt**: `max(0, smokedYesterday − baseAllowance(yesterday))`, only for days on/after plan start.
  Today's allowance = `max(0, base(today) − debt)`. Debt only looks back one day.
- **Interval** (minutes) = `wakingMinutes / allowance`; when allowance is 0, = `wakingMinutes`.

### Heat — `Stats.heat`
`1 − clamp(minutesSinceLast / interval, 0, 1)`. No logs ever → 0 (cool).

### Other stats — `Stats`
- `notSmoked(baseline, since, now, smokedSince)` uses fractional days.
- `hourly(...)` returns 24 per-day averages over the window, honouring the day filter.
- `longestGap(...)` = longest gap between consecutive logs or last log → now inside the window;
  the gap before the first in-window log counts only if an earlier log anchors it.

### Milestones — `Milestones`
WHO timeline; lower bound of each range unlocks it. `clearFraction` = progress to 12 h, min 0.05.

---

## 5. Architecture

```
app/src/main/java/app/exhale/
  ExhaleApp.kt            Application; manual DI (one ExhaleRepository)
  MainActivity.kt         Edge-to-edge, sets ExhaleTheme { ExhaleRoot() }
  data/                   Room (smoke_log, urge) + DataStore settings + repository
  domain/                 Pure models and math. No Android imports.
  ui/
    ExhaleViewModel.kt    Combines settings, logs, urges and a 1 s ticker into ExhaleState
    ExhaleNav.kt          Onboarding gate, NavHost, custom bottom bar
    Format.kt             Durations, money
    theme/Theme.kt        Ex colour tokens, ExType, fonts
    components/           Pills, buttons, segments, haptics
    screens/              One file per screen; stateless, take ExhaleState + callbacks
  tile/LogTileService.kt  Quick-settings tile
```

- **Single source of state**: `ExhaleViewModel.state: StateFlow<ExhaleState>`. Screens are stateless
  except for local UI state (toggles, animation). Derive numbers in the ViewModel or `domain/`, not in screens,
  unless purely presentational.
- **Local only**: Room DB `exhale.db`, DataStore `settings`. No network permission. `allowBackup=false`.
- **Schema changes**: bump `@Database(version)`, add a Migration, commit the exported schema in `app/schemas/`.
- minSdk 26, target/compile 36, Kotlin 2.2, Compose BOM, Material 3, Navigation Compose, KSP for Room.

### Testing
- `./gradlew testDebugUnitTest` — domain unit tests (`app/src/test/.../domain`) and screenshot tests.
- `./gradlew recordRoborazziDebug` — renders every screen with fixed sample data on the JVM
  (Robolectric, native graphics) into `app/build/outputs/roborazzi/`. Copy updated images to
  `docs/screenshots/` when a screen changes visibly.
- `./gradlew assembleDebug` — APK at `app/build/outputs/apk/debug/app-debug.apk`.

### Releases
- Version: `versionName` in `version.properties` (MAJOR.MINOR.PATCH). `versionCode` = MAJOR×1,000,000 + MINOR×1,000 + PATCH.
- `.github/workflows/release.yml` runs on pushes to `main` that touch `version.properties` (or manually).
  If tag `v<versionName>` doesn't exist yet, it runs tests, builds the debug APK and creates a GitHub Release with `exhale-<version>.apk`.
- APKs are signed with the committed `app/debug.keystore` so updates install over earlier versions. A Play Store build will need a private release key in GitHub secrets.

Every change to `domain/` needs a unit test. Every new screen needs a screenshot test.

---

## 6. Roadmap

**Now (prototype, v0.1)** — everything above, local data only.

**Next**
- Glance home-screen widget: count, countdown, long-press to log; colour follows heat.
- Notifications: "next one earned" (opt-in) and nudges before the user's own hot hours (max 2/day).
- AGSL `RuntimeShader` orb and particle field on API 33+, current Canvas versions as fallback.
- Accelerometer drift on the Air particles; flip-face-down mode for Urge Wave.
- CSV export; biometric app lock; discreet icon/notification option.
- Quit mode: explicit quit date, milestones counted from it.

**Later**
- Wear OS tile and complication.
- Optional encrypted backup/sync (explicit opt-in; stays off by default).

## 7. Non-goals
- Social feeds or leaderboards.
- Medical advice. The app links to sources and suggests seeing a professional or quitline for NRT/medication.
- Any analytics or network calls in the prototype.
