# Exhale design system

Web style guide with live previews: https://claude.ai/artifact/XNKeWbtRnqA7Nz2Nc3uEC6 (private until shared). In the app: Settings → Prototype tools → Design system.

Burn less. Exhale more. Exhale is an Android app (Jetpack Compose) that helps people track, regulate and cut down cigarettes. This system is the single source for how it looks and feels. The code of truth lives in the app at `app/src/main/java/app/exhale/ui/theme/` (tokens) and `ui/components/` (components); this page mirrors it for the web.

## Principles

- **Calm, not clinical.** Near-black or soft white, one background on every screen, generous space, an editorial serif for feelings and a friendly sans for everything else.
- **Two temperatures.** Warm hues mean smoke, cool hues mean recovery. `ember` is a cigarette, `flare` is a craving, `oxy` is time earned back, `sky` sits between, `sun` is money. Never use a warm accent for success or a cool accent for smoking. As someone cuts down, the app literally cools.
- **Big, obvious actions.** Every screen has at most one `primary` button: 60px tall, full width, a pill. Everything else is `surface`-filled or a ghost.
- **No shame.** Copy never says "failed", "relapse" or "streak lost". Going over the allowance is "borrowed from tomorrow" and shown in `sun`/`flare`, never as an error.
- **Private and quiet.** No red error states for smoking, no exclamation marks, no badges that nag.

## Colour

Neutrals carry the UI; accents carry meaning.

- Background: always `bg`. Cards, chips, inputs and the tab bar sit on `surface`; selected or pressed things on `surface-high`.
- Text: `ink` for primary, `ink-soft` for secondary, `ink-faint` for tertiary. All pass 4.5:1 on `bg` and `surface` in both themes.
- Accents (`ember`, `flare`, `sky`, `oxy`, `sun`) are **fills and graphics**: rings, sticks, the orb, chart wedges, particles. For **text or icons** in an accent, use its `*-ink` token. In dark they are identical; in light they are darker so text meets 4.5:1 (for example `oxy-ink` is 5.1:1 on `bg`).
- Selected chips and option cards use a soft tint of the accent (18% in dark, 14% in light, over `surface`) with the `*-ink` label on it.
- Heat: one scale from `ember` (just smoked) through `sky` to `oxy` (next one earned). Anything that says "how recently did I smoke" uses it: the Ember orb, the Ember tab icon.
- Colour is never the only signal. A heat colour always comes with a time or a count; over-allowance sticks are also counted in text.

## Type

- **Newsreader** (serif, optical size 48) for what you feel: big states (`display`), screen titles and step questions (`title`), soft italic asides (`title-italic`), card titles (`headline`).
- **Figtree** (sans) for what you read and tap: `body`, `label` on buttons, `label-small` on chips, `caption`, uppercase `overline` labels.
- **Numbers that change** (timers, counts) use the `number-*` styles with tabular digits (`font-feature-settings: "tnum"`) so they don't jitter.
- Serif headlines are sentence case and short; break a two-line title with a deliberate line break, as in "The air / is clearing."

## Shape, space and size

- 4px spacing scale; screens use a 20px side gutter (`space-lg`) and 16px between blocks (`space-md`).
- Radii: `radius-pill` for anything tappable that is a single line (buttons, chips, badges, tab bar, segmented control), `radius-md` for inputs and option cards, `radius-lg` for cards.
- Sizes: `button-large` 60, `button-medium` 48, `icon-button` 48, `chip` 40, `tab-bar` 68. Touch targets are never under 44.
- Elevation: flat. The only shadow is `shadow-float` on the light tab bar; dark uses a `line` border instead.

## Navigation

A **floating tab bar**: a `surface` pill hovering 12px above the system navigation bar with a 20px gutter, icons only (each with an accessibility label). The selected tab sits in a `surface-high` inner pill and takes its meaning colour: Ember (heat), Regulate (`sky`), Rhythm (`flare`), Air (`oxy`), Money (`sun`). Screens under it leave `tab-bar-clearance` at the bottom. Full-screen flows (Urge Wave, Settings, Onboarding) hide it and use a round back or close button in the header instead.

## Iconography

Exhale's own line icons: 24px grid, 1.8 stroke, round caps and joins, no fills. They live in code (`ExIcons.kt`) and in the Icons group here. The SVG files draw in `ink` (light value, `#131318`); in the app they are tinted at runtime. Never mix in a filled icon set.

## Motion and haptics

- Tappable components squish to 97% on press with a spring; the tab pill and segmented thumb slide with a spring.
- Ambient loops are slow (orb breathing 4.2s, particles drifting) and stop when the screen leaves.
- Each haptic has one meaning: slow rise = holding to log, thud = logged, double tick = next one earned, low tick = breath phase change.

## Themes

Dark and light are equal citizens. The app follows the phone's setting; Settings → Appearance overrides it with System, Light or Dark. Status and navigation bar icons follow the app theme.

## Copy

Plain, short, second person. A cigarette is information: "Logged. 8 of 12 today." Buttons say what happens: "Continue", "Start", "Ride it out", "Save changes".

## Components

Button, IconButton, Chip, Badge, Card, OptionCard, TextField, Stepper, SegmentedControl, StepProgress, GradientRing, WeekStrip, ListRow and FloatingTabBar. Each card below has guidelines and a live web rendition styled by `components/bundle.css`. In the app they are Compose functions in `ui/components/` (`ExButton`, `ExIconButton`, `ExChip`, `ExBadge`, `ExCard`, `OptionCard`, `ExTextField`, `Stepper`, `SegmentedControl`, `StepProgress`, `GradientRing`, `WeekStrip`, `ListRow`, `FloatingTabBar`). Every screen sits in `ExScreen`, which applies `bg`, the status-bar inset, the gutter and the tab-bar clearance. The app also has a live catalogue: Settings → Prototype tools → Design system.

## Colour tokens

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | `#0c0c10` | `#f3f3f6` | The one background every screen uses. Never swap it per screen. |
| `surface` | `#18181e` | `#ffffff` | Cards, chips, inputs, the tab bar, secondary buttons. |
| `surface-high` | `#26262e` | `#e7e7ec` | Selected tab pill, pressed surfaces, the logged card, empty progress tracks. |
| `line` | `#2e2e37` | `#dcdbe2` | Hairline borders: the dark tab bar outline, swatch edges. Use rarely. |
| `ink` | `#f4f3f7` | `#131318` | Primary text and icons on bg and surfaces (16:1 or more). |
| `ink-soft` | `#a6a4b0` | `#5c5a66` | Secondary text: subtitles, captions, unselected tab icons (6:1 or more). |
| `ink-faint` | `#8a8894` | `#6f6d79` | Tertiary text: placeholders, source lines, empty week days (4.5:1 on bg). |
| `primary` | `#f4f3f7` | `#131318` | Primary button fill. Inverted ink: white on dark, near-black on light. |
| `on-primary` | `#0c0c10` | `#f3f3f6` | Label on the primary button. |
| `ember` | `#ffa285` | `#e97e5a` | Smoke. A cigarette, a log, used allowance sticks, the hot end of heat. Fills and graphics only. |
| `flare` | `#f59dc3` | `#d9689a` | Craving and over-allowance: hot wedges, over sticks, trigger chips. Fills and graphics only. |
| `sky` | `#8fa2ff` | `#5b6fe6` | In between hot and cool: the middle of heat, the start of the interval ring, focused input ring. |
| `oxy` | `#86e3b6` | `#2ea676` | Recovery and earned time: the ring end, particles, the Accent button in dark, unlocked milestones. |
| `sun` | `#f3d27a` | `#c9972a` | Money and milestones: golden sticks, the goal bar start. |
| `ember-ink` | `#ffa285` | `#ac4527` | Text and icons in ember. Equals ember in dark; darker in light for 4.5:1. |
| `flare-ink` | `#f59dc3` | `#b03c72` | Text and icons in flare (selected trigger chips, over badges). |
| `sky-ink` | `#8fa2ff` | `#4253cc` | Text and icons in sky. |
| `oxy-ink` | `#86e3b6` | `#187550` | Text and icons in oxygen; Accent button fill in light (white label 5.7:1). |
| `sun-ink` | `#f3d27a` | `#86600f` | Text in sun ("kept in your pocket"). |

## Component reference

### Button

Pill button in four styles; Large (60) is the default and spans the width for screen actions.

Use **Primary** for the one main action of a screen (`primary` fill, `on-primary` label). **Secondary** (`surface`) for other actions, optionally with a leading icon. **Accent** for an action with meaning, such as "I'm okay now" in the Urge Wave: `oxy` in dark, `oxy-ink` in light so the white label stays 5.7:1. **Ghost** for skip or cancel.

- Sizes: Large `button-large` (60) with `label`; Medium `button-medium` (48) with `label-small`.
- Disabled: `surface-high` fill, `ink-faint` label. Explain why nearby in a caption.
- Squishes to 97% on press. Labels say what happens: "Continue", "Save changes", "Ride it out".
- Compose: `ExButton(text, onClick, style = ButtonStyle.Primary, size = ButtonSize.Large, icon = ExIcons.Wave)`.

### IconButton

Round 48px icon button for headers: back, close, settings.

`surface` circle with an `ink` icon at 24px. The plain variant drops the fill for low-emphasis spots. Always give it an accessibility label ("Back", "Close", "Settings").

- Compose: `ExIconButton(ExIcons.Back, "Back", onClick)`; `filled = false` for plain.

### Chip

Selectable pill for tags such as triggers; selected chips take a soft accent tint with the accent's ink label.

Unselected: `surface` fill, `ink` label. Selected: the accent at 14–18% over `surface`, label in the accent's `*-ink`. Triggers use `flare`; other choices default to `sky`. Chips wrap in rows (FlowRow), never scroll sideways. Min height `chip` (40).

- Compose: `ExChip(text, selected, onClick, accent = c.flare)`; `TriggerChips(selected, onPick)` for the trigger set.

### Badge

Small static pill for counts, dates and status in headers.

Neutral by default (`surface`, `ink-soft`). With an accent: a tint fill and the accent's ink, for example `flare` when today is over the allowance. Uses `number-small` with tabular digits.

- Compose: `ExBadge("7 of 12 today", accent = if (over) c.flare else null)`.

### Card

Rounded surface that groups related content: an overline, a headline or stats, and supporting text.

`surface` fill, `radius-lg`, `space-lg` padding, `space-xs` between children. No borders or shadows. Over a busy background (the Air particles) the fill may drop to 88% opacity. On the logged card use `surface-high` so `surface` chips inside stay visible.

- Compose: `ExCard { Overline("Plan · −1 per week"); Row { Stat(...) } }`.

### OptionCard

Selectable card for a choice that needs a description and a preview, such as taper plans.

Behaves as a radio. Selected: oxygen tint fill, 1.5px `oxy` ring, a filled check dot, preview line in `oxy-ink`. Unselected: `surface`, empty `surface-high` dot. `radius-md`, 18px padding.

- Compose: `OptionCard(title, description, selected, onClick, trailing = "Weeks 1–4: 13 → 12 → 11 → 10")`.

### TextField

Filled text field with the label above and a soft focus ring.

Label in `caption`/`ink-soft` above a 56px `surface` box with `radius-md`; text in `body`/`ink`, placeholder in `ink-faint`. Focus draws a 1.5px `sky` ring. Put related short fields side by side with `space-sm` gaps. Validation messages go in a caption below the group, not in red.

- Compose: `ExTextField("Pack price", value, onChange, type = KeyboardType.Decimal, placeholder = "10.00")`.

### Stepper

Big serif number with round minus and plus buttons, for counts changed a few steps at a time.

The value is the hero of its screen: Newsreader at 96px over a `caption` unit. Buttons are 64px `surface` circles; at the range ends they disable. Use it for daily counts and allowances, never for money.

- Compose: `Stepper(value, onChange, 1..60, "cigarettes a day")`.

### SegmentedControl

iOS-style segmented control with a sliding thumb for switching views or modes.

44px track (`surface-high` in light, `surface` in dark) with a thumb one step lighter or darker; selected label `ink`, others `ink-soft`. 2–3 short options. Used for theme, time ranges, weekday filters.

- Compose: `SegmentedControl(listOf("System", "Light", "Dark"), selected, onSelect)`.

### StepProgress

Segmented progress bar for multi-step flows; filled segments cool from ember to oxygen.

4px pill segments with 6px gaps. Done segments take the heat scale from warm (first) to cool (last), so finishing onboarding literally cools the bar. Pair with a "2 / 4" badge.

- Compose: `StepProgress(step, total)`.

### GradientRing

Thick progress ring with a sky-to-oxygen sweep and content in the middle; the Regulate countdown.

Track `surface`, 28px stroke, round caps, fills clockwise from 12 o'clock. Colours run `sky` → `oxy` (time being earned back). The centre holds a `number-large` value and a caption.

- Compose: `GradientRing(progress, listOf(c.sky, c.oxy)) { Text(...) }`.

### WeekStrip

S M T W T F S row of circles: each past day shows its count, tinted oxygen within the allowance and ember when over.

40px circles. Within allowance: oxygen tint with `oxy-ink` count. Over: ember tint with `ember-ink` count. Today: 1.5px `ink` ring and an `ink` weekday letter. Future days are empty `surface` circles. First day follows the locale.

- Compose: `WeekStrip(days, today)` with `DayDot(date, count, allowance)`.

### ListRow

Tappable row inside a card: label, optional value, chevron.

52px minimum height, `body` label, `ink-soft` value, `ink-faint` chevron. Group rows in a `surface` card with `space-sm` padding; no dividers.

- Compose: `ListRow("Design system", onClick, value = null)`.

### FloatingTabBar

iOS-style floating pill tab bar, icons only; the selected tab sits in a lighter inner pill and takes its meaning colour.

`surface` pill, 68px tall, 6px inner padding, floating 12px above the navigation bar with a 20px gutter. Light: `shadow-float`; dark: a 1px `line` ring. Selected: `surface-high` inner pill and the icon in its colour's ink (Ember follows heat). Unselected icons `ink-soft`. Every tab has an accessibility label. Screens leave `tab-bar-clearance` at the bottom.

- Compose: `FloatingTabBar(items = listOf(TabItem(ExIcons.Ember, "Ember", tint)), selected, onSelect)`.

## Not synced

- The web previews are static HTML/CSS renditions written by hand from the Compose components; they are not generated from the Kotlin code.
- Animated components (the Ember orb, the Urge Wave, the particle field, the Smoke Clock heatmap) are screen-specific and are not in this kit.
