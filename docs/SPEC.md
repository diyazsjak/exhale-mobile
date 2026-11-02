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

## 2. Design language — "Two temperatures"

Warm = smoke. Cool = recovery. This is the main data channel; learn it once, read it everywhere.

| Token | Hex | Meaning — never use for anything else |
|---|---|---|
| `Ex.Void` | `#0D0B1C` | Base background |
| `Ex.Panel` | `#17142D` | Raised surfaces |
| `Ex.Line` | `#2C2750` | Borders, empty slots |
| `Ex.Ink` / `Ex.Mute` | `#F4EFFF` / `#A49DCB` | Text / secondary text |
| `Ex.Ember` | `#FF5A1F` | A cigarette, a log |
| `Ex.Flare` | `#FF2E88` | Craving, peak, over-allowance |
| `Ex.Sky` | `#6C7BFF` | Transition between hot and cool |
| `Ex.Oxy` | `#2EF2D0` | Recovery, earned time, primary actions |
| `Ex.Sun` | `#FFC53D` | Money, milestones (Money screen background) |

Rules:
- `Ex.heatColor(heat)` maps `heat` (1 = just smoked, 0 = interval earned) Ember → Sky → Oxygen.
  Any element showing "how recently did I smoke" uses it.
- Colour is never the only signal: always pair with a number or label.
- Dark-only app. The Money screen is the single deliberate exception (flat Sun background).

**Type** (bundled variable fonts in `res/font`, OFL — see `docs/FONTS-OFL.txt`):
- Display — Bricolage Grotesque, condensed (`wdth` 75–85), weight 700–800. Big states and headlines.
- Body — Hanken Grotesk 400–600. Copy and coaching.
- Data — Martian Mono. Timers, counts, eyebrows. Tabular digits so timers don't jitter.
Use the styles in `ExType`; don't create ad-hoc `TextStyle`s in screens.

**Motion**: springs with visible mass (toast, press squish), slow ambient loops (orb breathing,
particles). Long loops must stop when the screen leaves composition.

### Haptics vocabulary (`ui/components/Haptics.kt`)
| Pattern | Meaning |
|---|---|
| `logging()` slow rise | Holding to log |
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

Bottom bar: **Ember · Regulate · Rhythm · Air · Money**. Full-screen routes: **Urge Wave**, **Settings**.
First launch shows **Onboarding** (the Settings form in onboarding mode).

### 3.1 Ember (home) — `EmberScreen.kt`
- Header: today's count / allowance (Flare when at/over), Settings link.
- Hero: time since last cigarette (`Fmt.gap`), subline with time until next earned.
- **Orb**: radial gradient coloured by `heat`, breathing scale loop.
- **Hold to log**: press 1.5 s, ring fills, `logging()` haptic; release early cancels.
  On completion `logged()` haptic and a log is written. TalkBack: long-press action logs directly.
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
- Saved = (baseline × days since plan start − cigarettes smoked since) × price per cigarette, floored at 0.
- Sticks row: fraction of the current hundred not smoked.
- Goal card with progress and weeks-to-goal at current pace.

### 3.7 Settings / Onboarding — `SettingsScreen.kt`
Fields: baseline per day, cigarettes per pack, pack price, currency (ISO 4217), starting allowance
(default baseline − 2), taper type, goal name, goal amount.
Prototype actions: load demo data (30 days), erase all data (two-tap confirm).

### 3.8 Outside the app
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
