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
| Reduce-to-quit ≈ abrupt quitting in quit rates (Lindson et al., Cochrane 2019) | Cutting down is first-class: allowance, countdown ring and taper plan |
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
  60dp pill button per screen. Floating iOS-style tab bar, icons only, and a shared app bar on top.
- **Themes**: dark and light; follows the phone, override in Settings → Appearance (`ThemeMode`).
- Colour is never the only signal: always pair with a number or label.

### Haptics vocabulary (`ui/components/Haptics.kt`)
| Pattern | Meaning |
|---|---|
| `charge(level)` rising ticks | Holding to log |
| `logged()` thud | Log committed |
| `loggedOver()` two soft thuds | A log that goes over today's allowance |
| `earned()` double tick | Next cigarette earned (Ember ring completes) |
| `breath()` low tick | Breath phase change (Urge Wave) |
Don't reuse a pattern for a different meaning.

### Copy rules
- Friendly, plain, short. Second person.
- Never: "failed", "relapse", "streak lost", "bad", exclamation marks on smoking events.
- A cigarette is information: "Logged. 8 of 12 today."
- Over allowance = "borrowed from tomorrow", shown in Sun/Flare, never an error.

---

## 3. Screens

Floating tab bar (icons only): **Ember · Rhythm · Air · Money**. Full-screen routes: **Urge Wave**, **Settings**, **History**.

**Shared app bar** (`ExAppBar`, drawn in `ExhaleNav` over the tabs, like the tab bar): each tab's
**leading** on the left and the **Settings** button on the right, on every tab. The leading is
Ember's Today badge, "Rhythm · Last 30 days" ("Rhythm" while a day is picked), "Recovery · if you
stop now", or "Money · since 4 September". When it changes, the new one drops in from 16dp above and
fades in (260ms, the page slide's length) while the old one drops 16dp and fades out (200ms); the
Settings button never moves. The bar is solid `bg`; while the tab's content is scrolled under it, a
1dp `line` border shows along its bottom (fading in and out in 150ms), as in Material 3's scrolled
state. It scrolls "enter always": it slides up out of view as the content scrolls up, comes back as
soon as it scrolls down, and settles fully shown or hidden when the scroll ends; the status bar strip
above it stays. Switching tabs brings it back. On Air it stays with the held hero instead, and only
goes up with it once the next card pushes the hero (scrolling back still brings it back at once). The screens beneath leave the room the header row
took (`ExScreen(underAppBar = true)`).
Opening a full-screen route, the app bar and the tab bar recede with the tab screen (a third to the
left, fading) and are covered by the incoming screen's edge; going back, they return the same way.

Full-screen routes (Settings, History, Design system) have their own header row with a back button;
it is 64dp like the app bar (8dp above and below the back button), stays pinned while the content
scrolls under it, and has the same border while content is under it.
First launch shows **Onboarding** (§3.7). Regulate is no longer a tab: its countdown lives on Ember
(the ring) and the week lives on Rhythm; the plan is edited in Settings.

### 3.1 Ember (home) — `EmberScreen.kt`
- App bar leading: the **Today badge** ("7 of 10 today", 14sp, a touch larger than other
  badges). It turns pink (flare tint, flare ink) while today is over the allowance; the text doesn't
  change. A small sun-coloured dot appears on days whose allowance was lowered by yesterday's
  overage, until the user opens the popover that day (`borrowNoticeSeenDay`). No week row.
- **Today popover**: tapping the badge opens a card just below it, full width within the gutter:
  the count, today's sticks (used ember, next dashed oxygen, over flare, remaining empty), and the
  lines that apply: "N over today. It's borrowed from tomorrow, so tomorrow starts at X instead of
  Y." / "N left today. Going over isn't failing…" / "That's all for today…", plus "Today is A
  instead of B: N were borrowed yesterday." on a lowered day, and a footer with this week's and next
  week's allowance. Tap outside, press Back or start holding the ember to close it.
  "Over today" (the badge's pink, the count, the flare sticks) is against today's allowance, but what
  is borrowed from tomorrow follows §4 Debt: only what goes past today's *base*
  (`TodayPlan.borrowingFromTomorrow`). X is `TodayPlan.tomorrowAllowance`
  (`PlanMath.allowance(tomorrow, todayCount)`), Y tomorrow's base. So on a lowered day (base 10, 2
  borrowed, allowance 8) the over line reads "1 over today. You're still within your usual 10, so
  nothing is borrowed: tomorrow starts at 10." at 9, and "4 over today. 2 of them are past your
  usual 10 and borrowed from tomorrow, so tomorrow starts at 8 instead of 10." at 12; the other
  lines say "anything past your usual 10" instead of "anything more/over".
- Hero, one number that switches meaning: while waiting, time **until** the next one is earned
  ("58m · until your next one"); once earned, time **since** the last one ("2h 41m · since your last
  one · next one earned"). Before any log: "—" and "nothing logged yet".
- **Orb** (`EmberOrb.kt`, concept "Gather" from the Ember Lab): a molten core coloured by `heat`
  with four droplets orbiting it. At rest the core pulses every 4 s and the drops drift in and out,
  sometimes touching the core and merging with a liquid bridge (blur + alpha threshold RenderEffect,
  Android 12+; plain drops below that).
- **Countdown ring** around the ember (6dp, radius 0.47 of the ember): fills sky → oxygen as the gap
  (waking minutes / allowance) is earned; once earned it is a full oxygen ring with a soft glow that
  breathes with the ember. `earned()` haptic when it completes while on screen. The drops orbit
  inside it.
- **Hold to log**: press 1.5 s. The ring hands over to the charge, which runs around the same
  ring along the heat scale; the drops are pulled in and merge, the core swells and warms, and 12
  `charge()` ticks rise in strength; release early cancels. On completion a `logged()` thud, the
  drops fling out with a ring and a splash, and a log is written.
- **Reactive hint** under the ember, one line that slides to its next state: "Hold the ember to log"
  (rest), "Keep holding…" (pressed), back to rest after a log (the Logged card reports it; a log
  that goes over gets a pink burst and a `loggedOver()` double thud),
  "Hold a little longer" (released early), and on a quick tap the ember wobbles and the hint repeats
  "Hold the ember to log".
- **Logged card** after logging: count ("12 of 8 today · 2 borrowed from tomorrow", the borrowed part only
  when something is, §4 Debt), **Undo**, optional trigger chips. Auto-dismisses after 8 s.
- "I want one. Help me wait." → Urge Wave.

### 3.3 Urge Wave — `UrgeWaveScreen.kt`
- 240 s wave; crest at ~46 % (minute ~2). Dot rides the curve.
- Breath pacing 4-2-6 (in-hold-out), `breath()` haptic on phase change.
- Headline copy changes with progress (rising, crest, fading).
- Breathing guide (`BreathGuide.kt`), text only: the current instruction fills letter by letter over
  its duration; the next ("then hold · 2s") waits below and brightens in the final second; on a
  change it glides up and grows into place. The wave dot's halo swells and shrinks with the breath.
- Optional trigger selection.
- Before end: "I'm okay now" (SURVIVED) / "Smoke anyway". After end: "No. I rode it out." /
  "Yes, smoke anyway". Smoking records the urge as SMOKED but never logs a cigarette: it returns to
  the Ember screen, where the user logs it by holding the ember. "Close" records nothing.

### 3.4 Rhythm — `RhythmScreen.kt`
Shows the last 30 days (today and the 29 before), or one picked day. Order:
- **App bar leading**: "Rhythm · Last 30 days"; while a day is picked just "Rhythm": the day is
  named and counted in the filter bar. The average a day leads the comparison card instead.
- **Title**: "When you light up". No line under it: the busiest hour is in the clock centre and the
  top trigger in the triggers card, so nothing on the screen says the same thing twice.
- **Last 7 days · tap a day** (rolling, today last) with an **All history ›** link to History (§3.10). Each
  tracked day is tinted oxygen within the allowance and ember when over; days before tracking began
  are empty rings and not tappable. Tap a day to filter the whole screen to it; tap again, or "Show
  last 30 days", to return. The picked day is hoisted in `ExhaleNav` so History can set it.
- **Filter**: All days / Weekdays / Weekends. While a day is picked it is replaced by an
  `ActiveFilter`: "Sunday · 6 of 8" or "Sunday · 10, 2 borrowed", with "Show last 30 days".
- **Smoke Clock**: 24 hours, midnight at top, clockwise. Each hour's bar length and warmth is its
  average a day (any smoking shows; the top quarter is flare). A **thin line** on each hour marks
  where it was in the 30 days before. The centre shows the busiest hour ("Busiest · 6 pm · 1.2 a
  day · Tap an hour"); tapping an hour shows it there instead ("8–9 am · 0.8 a day · Mostly coffee
  · 16 of 16 tagged", trigger once 3+ are tagged), with an ink outline and its thin line in full
  ink. Tap the centre or the same hour to clear. Labels follow the phone's 12/24-hour setting.
  Screen readers get a summary and Next/Previous/Clear hour actions.
- **Comparison card** (when the 30 days before hold at least 7 tracked days, not on a picked day):
  led by the **average** for the day filter: "Average · last 30 days" ("Average · weekdays, last 30
  days"; "Average · since you started" when tracking began inside the window), a big "9.3" and "a
  day" ("a weekday", "a weekend day"). It stays the whole period's average when an hour is picked.
  Then "4.6 fewer a day than the 30 days before (14.0)." in oxygen when fewer, plain ink when more or
  about the same; with an hour picked it narrows to that hour: "This hour: 1.1 fewer a day than the
  30 days before (1.9)." (the hour itself is named in the clock centre). Tapping the card lights up the thin lines (bars dim, lines turn full ink,
  card outlined) and explains them; tapping again goes back. Without enough history, the same
  average, then "Your first month. In N days you'll see how each hour compares with the 30 days
  before."
- **Plan card** (a week of history, not on a picked day): "A plan for your busiest hour" and an
  if-then in the user's voice that names the hour and its top trigger once: "If it's a drink at
  6 pm, / then I ride one wave first." "Help me wait" opens the Urge Wave; "Another idea" cycles the then-line.
- **Triggers**: bars for the period with coverage ("181 of 275 tagged.").
- **Craving waves**: "8 craving waves ridden out in the last 30 days." on an oxygen tint, or an
  invitation to use "Help me wait" when there are none.
- Everything on the screen follows the same period, day filter and picked day.
- **Motion**: changes glide rather than jump (about 280 ms). Clock bars and thin lines move (0.36 s, fast start, soft stop) to their
  new lengths; the clock centre, the filter bar and the comparison text cross-fade; lighting up the
  lines fades; the comparison and plan cards fade and fold in or out.

### 3.5 Air — `AirScreen.kt`
What the body gets back after each cigarette, built so that **nothing resets**: a gap counts from
its last cigarette, and a milestone that any gap has reached stays reached. Order:
- **App bar leading**: "Recovery · if you stop now". Particle field behind everything: ash-grey particles
  turn oxygen as `clearFraction` (progress to 12 h) rises.
- **Hero**: time since the last one ("2h 41m", `oxy-ink`), "since your last one", and "Right now: …"
  with what the body is doing on the way to the next milestone (`Milestone.underway`). Before any
  log: "Nothing logged yet." and how the map works.
- **Sky**: a dashed path from the last cigarette (ember dot) through every milestone; 5, 10 and 15
  years are one small cluster, "5–15 years · The long road". A milestone is a lit oxygen dot with a
  halo once any gap has reached it, and stays lit; the next one is an oxygen ring; the rest are small
  grey dots. The current gap is a ringed dot with an oxygen trail behind it, placed by
  `Milestones.position`; it glides back to the start after a log. Every point is labelled, so
  nothing needs a tap: "12 h · reached Thursday / Carbon monoxide normal". A key below:
  "reached at least once" (only once something is) and "this gap".
- **Scrolling**: the hero stays in place until the content after the sky
  (the next card) comes up to meet it, a normal 16dp gap below it, then goes up with that content.
  Meanwhile the cards slide over the sky, which stays filled and readable above them: each label
  (and the key) fades out over the last 48dp before the next card reaches it. The dimmed sky stays
  visible behind and between the cards.
  While the hero is held the sky rises only 10% of the scroll (at most 24dp, clear of the hero,
  which is drawn over it); after that it rises at 35% up to 120dp in all. Its dots and lines keep
  full strength for the first half of the held stretch, then dim to 30% by 120dp after it.
- **Next card**: a sky→oxygen ring of the way to the next milestone with the time to go in the
  middle ("9h 19m", then "13 days", "11 months", "1 year"), "Next · 12 hours", the milestone and when
  ("Today at 23:40", "Tomorrow at 09:10", "Saturday at 10:00", "On 18 Nov").
- **Today's air**: today's gaps as oxygen blocks on a 24-hour line, the taller the longer (log scale
  with dashed lines at 20 min and 12 h); ember ticks are today's cigarettes. The running gap is solid
  with a dot at its top and a dashed outline of where it reaches 12 hours. The night counts in full
  from yesterday's last one. Under it: "5 gaps past 20 minutes today. Longest: 9h 43m.", or, while
  no gap has reached 20 minutes, when this one will; with nothing today, how long this gap has run.
- **Body recovery**: every milestone on a vertical line: the last one, milestones this gap reached
  (filled with a check, "Reached at 12:19"), "You are here · 2h 41m", the next ("12 hours · in 9h
  19m"), then the rest with WHO's range and how far off. A milestone reached in an earlier gap gets a
  "You've been here · Thursday" badge. 5, 10 and 15 years fold into "The long road" until this gap is
  past a year. Source line at the bottom.
- **Cutting down**: on an oxygen tint, "30 fewer than your old 16 a day in the last 7 days" (or
  "since you started" under a week in), "Cutting down clears the air too." Hidden at 0.
- Times follow the phone's 12/24-hour setting.

### 3.6 Money — `MoneyScreen.kt`
Same background as every screen; money is carried by `sun` accents. All numbers come from
`Money.summary` (§4), so the amount, recent amounts, pace, packs and goal agree.
- App bar leading: "Money · since 4 September" (the plan start).
- **Hero**: the amount kept since the plan started on one line (digits, then the currency at 45%,
  shrinking from 88sp to fit; all money text puts the currency after the number), "kept since you
  started", and "You used to smoke 18 a day. Since you started, you've smoked 372 fewer than that."
  (before any: "Every one you skip from here adds up.").
- **Recent pills** under the hero: "35 $ in the last 7 days", "130 $ in the last 30 days", each only
  once the plan is older than that window (before then it would equal the total).
- **Packs you didn't buy**: "18 packs you didn't buy", one icon per pack (up to 39, then "+N"), the
  next pack filling up, "The last one is filling up: 12 of 20." ("Your first pack is filling up…"
  before the first).
- **At this pace** (when the pace is above 0): "If the next weeks go like your last 7 days (35 $ a
  week), you'll keep:" then in a month (30 days), 6 months (182) and a year (365). Under a week in,
  the pace is the average week so far ("your days so far").
- **Goal card** on a sun tint: progress ring with the percentage, "Saving for", the name (two lines
  at most, then "…") and its price, then "413 $ still to go" and "~12 weeks at this pace" (the same
  pace), or "Reached · go get it". When the name is cut, tapping the card opens it in full in a
  dialog. Goal names are capped at 40 characters where they are typed (Onboarding, Settings).
- Footer: "A pack of 20 costs 10.00 $, so each one is 0.50 $. Change it in Settings."

### 3.7 Onboarding — `OnboardingScreen.kt`
One question per screen. Nothing is saved until the last step; system Back goes to the previous step.
A segmented progress bar (steps 1–4) fills in one colour (`ink`).

| Step | Asks | Notes |
|---|---|---|
| 0 Welcome | — | Thesis, warm/cool gradient legend, privacy line. "Get started". |
| 1 Habit | Cigarettes a day before Exhale | Big stepper, 1–60, default 15. |
| 2 Cost | Pack price, cigarettes per pack, currency | Currency defaults from the device locale. Live line: cost per cigarette and per month. Next is disabled until valid. |
| 3 Plan | Starting allowance + taper type | Stepper 0..baseline, default baseline − 2. Taper cards preview weeks 1–4. |
| 4 Goal (optional) | Goal name and amount | Shows money kept in the first 4 weeks if the plan is followed. "Start" or "Skip the goal for now". |

Pack price and goal amount are filtered and read like Settings' (`SettingsInput.money` / `minor`):
digits, one decimal mark and two decimals, so "1e3" can't be typed.

No demo data in onboarding.

### 3.8 Settings — `SettingsScreen.kt`
Edits everything set during onboarding; keeps the original plan start. Sections, top to bottom:
- **Appearance**: System / Light / Dark. Applies and saves the moment it's tapped.
- **Plan**, one card: **Before Exhale** and **Starting allowance** as counter rows (1–100 and 0–100;
  holding −/+ repeats), then **How it drops** (current taper) opening its own screen. Under the card a
  caption shows the plan as counted on screen: "This week 10 a day, next week 9." ("Holding at 14 a
  day." for Hold steady), plus "That start is above your usual 16." when the start is above Before Exhale.
- **Money**, one card: **A pack** ("20 for 10.00 $") and **Saving for** (the amount on the right, the
  goal name on a second line that is cut short when long, so the amount always shows whole). Each opens
  its own screen.
- **Your data**: Erase all data, then "Exhale keeps everything on this phone. No account, no cloud."
- **Prototype tools**, debug builds only (`BuildConfig.DEBUG`): Design system (opens `UiKitScreen`,
  the live component catalogue; its route only exists in debug builds) and Load demo data (60 days, so
  Rhythm has a "30 days before" to compare with).

Saving:
- The counters don't save on their own. While either differs from what's saved, its value shows in
  `sky` ink and **Save changes** rises from the bottom; saving shows a "Saved" toast and hides it.
- Back (button or system) with unsaved counts asks "Save your changes?": Save changes, Discard, Keep editing.
- **How it drops**, **A pack** and **Saving for** have no Save button: going back saves what changed and
  shows "Saved". A faint line on each says "Changes save when you go back." If nothing changed, nothing is said.
- How it drops: the three taper cards with their "Weeks 1–4" preview from the starting allowance on screen
  (saved or not; the subtitle says when it isn't saved yet).
- A pack: Per pack and Pack price fields, currency chips (the phone region's currency first, then USD,
  EUR, KZT; three in all) and Other, which shows a 3-letter code field.
- Saving for: name (40 characters at most; empty is saved as "Something nice") and amount.

Validation (`domain/SettingsInput`): fields filter what is typed. `quickCurrencies()` gives the currency chips
(the region's, then USD, EUR, KZT; three). Goal names are at most
`GOAL_NAME_MAX` (40) characters; an empty one is `GOAL_NAME_BLANK` ("Something nice"). Pack size takes digits only, three at
most; prices and amounts take digits, one decimal mark (`.` or `,`) and two decimals, seven whole digits
at most; currency codes take three Latin letters and turn upper case. What filtering can't prevent (an
empty field, a pack of 0 or over 100, an unknown currency) gets a note under the field in `ink` after a
`sky` dot, naming what going back keeps: "Enter the pack price, like 10.00. Going back keeps 10.00 $."
Going back with a note showing saves the valid fields, keeps the saved value of the others and says so:
"Saved. Pack price stays 10.00 $."

Erase all data and Load demo data each ask first in a dialog ("Erase everything?" / "Replace your
data?") with the safe choice as a Ghost button; neither uses a warm colour.

### 3.9 Outside the app
- **Quick-settings tile** — `tile/LogTileService.kt`: tap to log; subtitle shows today's count.
- Planned: Glance home-screen widget, Wear OS tile, smart nudges (see §6).

### 3.10 History — `HistoryScreen.kt`
Full-screen route from Rhythm's "All history ›" (back button, no tab bar).
- "Every day so far", "Since 4 September. Tap a day to see it on Rhythm.", and a key: within
  allowance (oxygen), borrowed from the next day (ember).
- One `MonthCalendar` per month, newest first, from the month tracking began. Each tracked day shows
  its count, tinted like the week strip; days before tracking began are empty rings; days before the
  plan start are neutral; future days show only their date and weeks still to come are left out.
- Under each month: average a day, "N of M days within your allowance", and the longest gap.
- Tapping a day opens Rhythm filtered to that day.

---

## 4. Domain rules (`domain/`, pure Kotlin, unit-tested)

### Allowance — `PlanMath`
- `weeksIntoPlan = floor(daysSince(planStart) / 7)`, 0 before start.
- `MINUS_ONE_WEEKLY`: `max(0, start − weeks)`
- `TEN_PERCENT_WEEKLY`: `max(0, min(ceil(start × 0.9^weeks), start − weeks))` (always ≥ 1 step/week)
- `HOLD`: `start`
- **Debt**: `max(0, smokedYesterday − baseAllowance(yesterday))`, only for days on/after plan start.
  Today's allowance = `max(0, base(today) − debt)`. Debt only looks back one day: it is measured
  against the base, not the lowered allowance, so smoking past a lowered allowance but within the
  base borrows nothing more.
- **Interval** (minutes) = `wakingMinutes / allowance`; when allowance is 0, = `wakingMinutes`.
- `outlook(s, today)`: base allowance today, tomorrow and from the next plan week (its first day),
  for the Today popover.

### Today — `TodayPlan`
`TodayPlan.of(settings, logs, now, zone)` is everything Ember needs about today: the count, the
allowance (after yesterday's debt), the interval, the cigarettes borrowed, the last log and the time
since it, when the next one is earned (last log + interval) and the heat. It also answers what Ember
and the Today popover ask: whether the next one is `earned` (and `earnedFraction` of the gap, the
ring), `overAllowance`, `overToday`, `leftToday`, `borrowingFromTomorrow` (§4 Debt, against the base),
`tomorrowAllowance`, `borrowNoticeDue` and the plan `outlook`. `ExhaleState` holds it as `plan` and
reads through to it.

### Heat — `Stats.heat`
`1 − clamp(minutesSinceLast / interval, 0, 1)`. No logs ever → 0 (cool).

### Money — `Money`
- Kept over a stretch = (baseline × days in it − smoked in it) × price of one, floored at 0, only
  counting time since the plan start. `notSmokedBetween(baseline, logs, from, to)` counts fractional
  days and the logs in `[from, to)`. `summary(...)` gives the total, the last
  7 and 30 days (null until the plan is older than the window), the weekly pace (last 7 days, else
  the average week so far once a day in), packs and remainder, goal left, progress and weeks to it
  (`ceil(left / weekly)`, 0 once reached, null at a pace of 0). Projections are `weekly × days / 7`.
- `keptFollowingPlan(s, days)`: what the first `days` of the plan keep if exactly the base allowance is
  smoked each day (Onboarding's "first 4 weeks" line).

### Other stats — `Stats`
- `hourly(logs, period, now, zone, dataStart)` returns 24 per-day averages. A `Period` is a range of
  calendar days (the last 30 is today and the 29 before it) plus a day filter. Each hour is averaged
  over the days on which it has started by `now` and that are on or after `dataStart` (earlier of
  plan start and first log), so a new user's average isn't diluted by days before they started and
  today's unfinished hours don't count. A one-day period gives that day's counts.
- `topTrigger` needs at least 3 tagged logs; `triggers` also reports how many were tagged.
- `coveredDays(period, now, zone, dataStart)`: days of a period that count (filter, tracking began,
  started). Rhythm compares with the 30 days before only when that period covers 7+ days.
- `peaks(hours)`: the busiest hour, plus the next busiest at least 3 hours away (round the clock)
  and at least 70% as busy.
- `longestGapBetween(logs, from, to)`: longest gap between consecutive logs or last log → `to`
  inside the window; the gap before the first in-window log counts only if an earlier log anchors
  it, clipped at `from`.

### Rhythm — `Rhythm`
`Rhythm.view(...)` is everything Rhythm shows for the last 30 days under a day filter, or for one
picked day: the hourly averages, the 30 days before (only when they cover 7+ days, never on a picked
day), covered days, whether tracking began inside the period ("since you started"), the period's
logs, triggers and peaks, craving waves ridden out, days tracked and days until a comparison, and
the picked day's tally. `topTrigger(hour)` is the busiest trigger at an hour (not on a picked day);
`showPlan` needs 7 covered days and something logged.

### Breathing — `Breath.kt`
`breathAt(t)`: the 4-2-6 cycle (in, hold, out) `t` seconds into a craving wave of `WAVE_SECONDS`
(240): phase, progress through it, whole seconds left, and how full the lungs are (eased).

### History — `History`
- `months(...)`: every month from the one tracking began in to today's, newest first. Each day is
  null before tracking began or after today; otherwise its count and allowance (`PlanMath.allowance`
  with the day before's count), null before the plan start. Per month: average over tracked days
  (today counts so far), planned days, days within the allowance, longest gap clipped to the month
  (`Stats.longestGapBetween`, up to now).
- `week(...)`: the last 7 days, today last, for Rhythm's week strip: null before tracking began,
  otherwise the day's count and allowance (null before the plan start).

### Demo data — `DemoData`
`DemoData.generate(today, now, zone, current, random)` is what Settings → Load demo data writes: an
onboarded profile (written over the `current` settings, so theme and waking hours are kept; the taper
is −1 a week, which the history follows) whose plan started 59 days ago, 60 days of logs tapering from about 16 to 8 a day with
hour-of-day weights and triggers, and 0–2 urges a day (4 min, trigger Stress). Nothing after `now`.

### Milestones — `Milestones`
WHO timeline; lower bound of each range unlocks it. `clearFraction` = progress to 12 h, min 0.05.
- `next(since)`: the first milestone not reached; null with no logs or once all are. `reached(since)`
  counts the ones reached.
- `position(since)`: where the current gap sits on the sky's path, 0 (last cigarette) to 8: linear up
  to 20 minutes, then a log scale between milestones (2 h is halfway from 20 min to 12 h).

### Recovery — `Recovery`
- `lastReached(logs, now)`: per milestone, the most recent moment a gap reached it (its start +
  the milestone's time), counting gaps between consecutive logs and last log → now. The stretch
  before the first log counts for nothing. Once reached it stays set.
- `day(logs, now, zone)`: today's gaps, oldest first: the one ending with today's first log when an
  earlier log starts it (kept at full length), the ones between today's logs, and the running one to
  now. With no log today, the running gap is the only one.
- `summary(...).fewer`: `Money.notSmokedBetween` against the baseline over the last 7 days, or from
  the plan start when that is newer; null under a day in.

---

## 5. Architecture

```
app/src/main/java/app/exhale/
  ExhaleApp.kt            Application; manual DI (one ExhaleRepository)
  MainActivity.kt         Edge-to-edge, sets ExhaleTheme { ExhaleRoot() }
  data/                   Room (smoke_log, urge) + DataStore settings + repository; no rules, only storage and mapping
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
