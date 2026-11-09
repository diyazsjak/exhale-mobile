# Exhale design system

Web style guide with live previews: https://claude.ai/artifact/XNKeWbtRnqA7Nz2Nc3uEC6 (private until shared). In the app (debug builds): Settings → Prototype tools → Design system.

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

- **Newsreader** (serif, optical size 48) for what you feel: big states (`display`), screen titles and step questions (`title`), soft italic asides (`title-italic`), card titles (`headline`), and lines in the user's own voice such as an if-then plan (`headline-italic`).
- **Figtree** (sans) for what you read and tap: `body` (`body-small` 14/20 in compact surfaces such as the Today popover), `label` on buttons, `label-small` on chips, `caption`, uppercase `overline` labels.
- **Numbers that change** (timers, counts) use the `number-*` styles with tabular digits (`font-feature-settings: "tnum"`) so they don't jitter.
- Serif headlines are sentence case and short; break a two-line title with a deliberate line break, as in "The air / is clearing."

## Shape, space and size

- 4px spacing scale; screens use a 20px side gutter (`space-lg`) and 16px between blocks (`space-md`).
- Radii: `radius-pill` for anything tappable that is a single line (buttons, chips, badges, tab bar, segmented control), `radius-md` for inputs and option cards, `radius-lg` for cards, `radius-xs` (6) for small drawn objects such as Money's pack icons.
- Sizes: `button-large` 60, `button-medium` 48, `icon-button` 48, `chip` 40, `tab-bar` 68, `row` 52 (list rows), `icon` 24, `icon-small` 16 and `wheel` 80 (a TimeWheel column). Touch targets are never under `touch` (44); segmented tracks are `segment` (44) tall.
- Strokes: `hairline` (1) for `line` borders, `ring` (1.5) for selection and focus rings.
- Elevation: flat. The only shadow is a soft one on the floating tab bar in light. Dark uses tone and a `line` outline instead of shadows.

## Navigation

A **floating tab bar**: a `chrome` pill hovering 12px above the system navigation bar with a 20px gutter, icons only (each with an accessibility label). `chrome` is a step lighter than cards in dark (with a `line` outline) and white in light. It is solid and content scrolls underneath it, darkening gradually from 88px above the bar to the screen edge (black at up to 40% in dark, `ink` at up to 12% in light). Light: a soft elevation shadow. Dark: no shadow; the lighter tone and hairline outline lift it (tonal elevation). The selected tab sits in a `chrome-selected` inner pill and takes its meaning colour: Ember (heat), Rhythm (`flare`), Air (`oxy`), Money (`sun`). Screens under it leave `tab-bar-clearance` at the bottom. Full-screen flows (Urge Wave, Settings, Onboarding) hide it and use a round back or close button in the header instead.

A **shared app bar** sits at the top of the four tabs, drawn over them like the tab bar: the tab's leading on the left (a label or badge) and the Settings icon button on the right, which never moves. When the tab changes, the new leading drops in from 16px above and fades in (260ms, the page slide's length) while the old one drops 16px and fades out (200ms). It is solid `bg`; while content is scrolled under it a 1px `line` border shows along its bottom (Material 3's scrolled state). It hides as the content scrolls up and comes back as soon as it scrolls down ("enter always"), settling fully shown or hidden. On Air it stays with the held hero and leaves with it. When a full-screen flow opens, both bars recede with the tab screen and the new screen covers them. Full-screen flows have their own pinned header row, 64px tall like the app bar (8px above and below the 48px button), with the same scrolled-under border.

## Iconography

Exhale's own line icons: 24px grid, 1.8 stroke, round caps and joins, no fills. They live in code (`ExIcons.kt`) and in the Icons group here. The SVG files draw in `ink` (light value, `#131318`); in the app they are tinted at runtime. Never mix in a filled icon set.

## Motion and haptics

- Tappable components squish to 97% on press with a spring; the tab pill and segmented thumb slide with a spring.
- Screen transitions are short (260ms) and slide: tabs move like pages in tab order; full-screen flows push in from the right over a receding screen and pop back.
- The craving screen's breathing guide is text only. The current instruction fills with colour letter by letter over its duration; centred under the wave; the next waits below, small and faded ("then hold · 2s"), and brightens and lifts in the last second; on a change it glides up and grows into place while the old one floats away. The halo around the wave's dot swells and shrinks continuously with the breath.
- The Ember and Glow heroes' number breathes out when it changes (`ExhaleText`): a number that changed drifts up about 0.4em, swells 10% and blurs away (340ms) while the new one rises 0.12em into place out of a blur (300ms, after 90ms), all its digits at once. Numbers and units that didn't change hold still, gliding to their new place (390ms) if the width changed. At rest it draws exactly like plain text, on one line, its type shrunk to fit if it is wider than the screen.
- Ambient loops are slow (orb breathing 4.2s, particles drifting) and stop when the screen leaves.
- Air's header holds still while scrolling until the cards after the sky reach it, then leaves with them. The cards slide over the sky, which rises only a little and stays readable above them: each label fades just before a card reaches it, and the dimmed sky stays visible behind and between the cards. Scroll-linked effects only move and fade what is already drawn.
- The Glow home's edge light breathes every 4s and stays fixed while pages slide: it fades out quickly when you leave Ember and back in over the slide when you return. Holding runs a warm trail along its countdown line, like the ember's charge.
- The Ember ("Gather") is a molten core that pulses every 4s with four droplets orbiting slowly, each touching and merging with it about a third of the time. Holding pulls the drops in and runs a trail around it along the heat scale; logging flings them out with a ring and a splash.
- Each haptic has one meaning: rising ticks = charging the hold (12 over 1.5s), thud = logged, two soft thuds = a log that goes over today's allowance, double tick = next one earned, low tick = breath phase change. Every pattern checks that the phone supports it and falls back to plain vibration, so it works on Samsung and Xiaomi phones too.

## Themes

Dark and light are equal citizens. The app follows the phone's setting; Settings → Appearance overrides it with System, Light or Dark. Status and navigation bar icons follow the app theme.

## Copy

Plain, short, second person. A cigarette is information: "Logged. 8 of 12 today." Buttons say what happens: "Continue", "Start", "Ride it out", "Save changes".

## Components

Button, IconButton, Link, Chip, Badge, Card, OptionCard, TextField, FieldNote, Footnote, Stepper, CounterRow, TimeWheel, SegmentedControl, ActiveFilter, StepProgress, GradientRing, WeekStrip, MonthCalendar, LegendItem, DayTimeline, DayLine, ListRow, HandleRow, ReorderColumn, TriggerChips, ExhaleText, Dialog, ChoiceDialog, LogEditor, Toast, FloatingTabBar and AppBar. Each card below has guidelines and a live web rendition styled by `components/bundle.css`. In the app they are Compose functions in `ui/components/` (`ExButton`, `ExIconButton`, `ExLink`, `ExChip`, `ExBadge`, `ExCard`, `OptionCard`, `ExTextField`, `FieldNote`, `Footnote`, `Stepper`, `CounterRow`, `TimeWheel`, `SegmentedControl`, `ActiveFilter`, `StepProgress`, `GradientRing`, `WeekStrip`, `MonthCalendar`, `LegendItem`, `DayTimeline`, `DayLine`, `ListRow`, `HandleRow`, `ReorderColumn`, `TriggerChips`, `ExhaleText`, `ExDialog`, `ExChoiceDialog`, `LogEditor`/`LogEditDialog`, `ExToast`, `FloatingTabBar`, `ExAppBar`). Every screen sits in `ExScreen`, which applies `bg`, the status-bar inset, the gutter and the tab-bar clearance. The app also has a live catalogue in debug builds: Settings → Prototype tools → Design system.

## Colour tokens

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | `#0c0c10` | `#f3f3f6` | The one background every screen uses. Never swap it per screen. |
| `surface` | `#18181e` | `#ffffff` | Cards, chips, inputs, secondary buttons. |
| `surface-high` | `#26262e` | `#e7e7ec` | Pressed surfaces, the logged card, empty progress tracks. |
| `line` | `#2e2e37` | `#dcdbe2` | Hairline borders: the dark tab bar outline, swatch edges. Use rarely. |
| `chrome` | `#222229` | `#ffffff` | Floating tab bar fill (solid), a step apart from cards. |
| `chrome-selected` | `#34343e` | `#e9e9ee` | Selected tab pill. |
| `ink` | `#f4f3f7` | `#131318` | Primary text and icons on bg and surfaces (16:1 or more). |
| `ink-soft` | `#a6a4b0` | `#5c5a66` | Secondary text: subtitles, captions, unselected tab icons (6:1 or more). |
| `ink-faint` | `#8a8894` | `#6f6d79` | Tertiary text: placeholders, source lines, empty week days (4.5:1 on bg). |
| `primary` | `#f4f3f7` | `#131318` | Primary button fill. Inverted ink: white on dark, near-black on light. |
| `on-primary` | `#0c0c10` | `#f3f3f6` | Label on the primary button. |
| `on-oxy` | `#0c0c10` | `#ffffff` | Label or check on the oxygen fill (`oxy` in dark, `oxy-ink` in light): the Accent button, a selected OptionCard's dot. |
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

Pill button in five styles; Large (60) is the default and spans the width for screen actions.

Use **Primary** for the one main action of a screen (`primary` fill, `on-primary` label). **Secondary** (`surface`) for other actions, optionally with a leading icon. **Tonal** (`surface-high`) is Secondary inside a card, where `surface` would disappear, such as "Help me wait" on Rhythm's plan card. **Accent** for an action with meaning, such as "I'm okay now" in the Urge Wave: `oxy` in dark, `oxy-ink` in light so the `on-oxy` (white) label stays 5.7:1. **Ghost** for skip or cancel.

- Sizes: Large `button-large` (60) with `label`; Medium `button-medium` (48) with `label-small`.
- Disabled: `surface-high` fill, `ink-faint` label. Explain why nearby in a caption.
- Squishes to 97% on press. Labels say what happens: "Continue", "Save changes", "Ride it out".
- Compose: `ExButton(text, onClick, style = ButtonStyle.Primary, size = ButtonSize.Large, icon = ExIcons.Wave)`.

### IconButton

Round 48px icon button for headers: back, close, settings.

`surface` circle with an `ink` icon at 24px. The plain variant drops the fill for low-emphasis spots. Always give it an accessibility label ("Back", "Close", "Settings").

- Compose: `ExIconButton(ExIcons.Back, "Back", onClick)`; `filled = false` for plain.

### Link

Inline way onward from a line of text, such as "All history ›" beside Rhythm's "Last 7 days".

`label-small` in `ink` with a 16px chevron, no fill. Keeps a 40px touch height without adding visual weight. Use it for navigation, never for an action that changes data.

- Compose: `ExLink("All history", onClick)`.

### Chip

Selectable pill for tags such as triggers; selected chips take a soft accent tint with the accent's ink label.

Unselected: `surface` fill, `ink` label. Selected: the accent at 14–18% over `surface`, label in the accent's `*-ink`. Triggers use `flare`; other choices default to `sky`. Chips wrap in rows (FlowRow), never scroll sideways. Min height `chip` (40).

- Compose: `ExChip(text, selected, onClick, accent = c.flare)`; `TriggerChips` for the trigger set (below).

### TriggerChips

The user's trigger chips in their order, with the two action chips that can follow them.

Trigger chips are Chips with the `flare` accent. Up to nine show; with eleven or more, a **more** chip ("3 more") opens the rest in place. With `onAdd`, a **Your own** chip ends the row. Both action chips are quiet: no fill, a 1px `ink-faint` outline, a `label-small` label in `ink-soft`, and for Your own a 16px plus. Tapped, Your own becomes a full-width pill field with a 1.5px `sky` ring and a 32px button inside it, Primary "Add" once a name is typed, `surface-high` "Cancel" while it is empty; a `caption` in `ink-faint` under the row counts letters ("11 / 20 · It joins your list for next time."). Back cancels. Motion: Your own fades and shrinks a little as the field unfolds under the row (240ms), and comes back as it folds; a chip that arrives after the first frame (a trigger just added) springs in from 60%; selection tints ease in (220ms). With `addInDialog`, Your own opens an InputDialog instead, for screens whose bottom the keyboard would cover.

- Compose: `TriggerChips(state.shownTriggers, selected, onPick, onAdd = { name -> … }, onEditing = { typing -> … })`; `addInDialog = true` on Urge Wave.

### InputDialog

A dialog for typing something short, such as a new trigger's name. Like ChoiceDialog (a `headline` title, then Medium buttons stacked full width, the main one Primary, Cancel Ghost), with fields in between; the same modal over the system scrim, moving up for the keyboard so the field stays in view. A button can be disabled until the input is valid. Tapping outside or Back cancels. TextField's `autoFocus` brings the keyboard up with it, and `onDone` makes the keyboard's Done act.

- Compose: `ExInputDialog("Your own trigger", onDismiss, listOf(DialogAction("Add", ButtonStyle.Primary, enabled = ok) { … }, DialogAction("Cancel", ButtonStyle.Ghost, onClick = onDismiss))) { ExTextField(…, autoFocus = true, onDone = add) }`.

### Badge

Small static pill for counts, dates and status in headers.

Neutral by default (`surface`, `ink-soft`). With an accent: a tint fill and the accent's ink, for example `flare` when today is over the allowance. Uses `number-small` with tabular digits.

- Compose: `ExBadge("7 of 12 today", accent = if (over) c.flare else null)`.

### Card

Rounded surface that groups related content: an overline, a headline or stats, and supporting text.

`surface` fill, `radius-lg`, `space-lg` padding, `space-xs` between children. No borders or shadows. Over a busy background (the Air particles) the fill may drop to 88% opacity. On the logged card use `surface-high` so `surface` chips inside stay visible. A whole card can be tappable; when tapping switches something on (Rhythm's comparison card lighting up the clock's lines) it is `outlined` with a 1.5px `ink` ring while on. A stat that is good news, such as craving waves ridden out, may sit on an oxygen tint card with its number in `oxy-ink`.

- Compose: `ExCard { Overline("Plan · −1 per week"); Row { Stat(...) } }`; `ExCard(onClick = …, outlined = on)`; `ExCard(color = c.tint(c.oxy))`.

### OptionCard

Selectable card for a choice that needs a description and a preview, such as taper plans.

Behaves as a radio. Selected: oxygen tint fill, 1.5px `oxy` ring, a filled check dot, preview line in `oxy-ink`. Unselected: `surface`, empty `surface-high` dot. `radius-md`, 18px padding.

- Compose: `OptionCard(title, description, selected, onClick, trailing = "Weeks 1–4: 13 → 12 → 11 → 10")`.

### TextField

Filled text field with the label above and a soft focus ring.

Label in `caption`/`ink-soft` above a 56px `surface` box with `radius-md`; text in `body`/`ink`, placeholder in `ink-faint`. Focus draws a 1.5px `sky` ring. Put related short fields side by side with `space-sm` gaps. Validation messages go in a caption below the group, not in red.

- With `maxLength`, input is cut at that length and a "12 / 40" counter in `ink-faint` sits beside the label (goal names).
- With `suffix`, a unit or currency code sits at the end of the box in `label-small`/`ink-soft` ("USD", "cigs").
- Filter what is typed (`SettingsInput.digits`, `money`, `currency`) rather than reporting it; what filtering can't prevent goes in a FieldNote.
- Compose: `ExTextField("Pack price", value, onChange, type = KeyboardType.Decimal, placeholder = "10.00", suffix = "USD")`; `maxLength = SettingsInput.GOAL_NAME_MAX`.

### FieldNote

A note under a field or group about a value that can't be saved as typed, and what is kept instead.

`caption` in `ink` after a 6px `sky` dot: noticeable, never red. Say what to enter and what going back keeps: "Enter the pack price, like 10.00. Going back keeps 10.00 $." Shows nothing when the text is empty.

- Compose: `FieldNote(if (SettingsInput.minor(price) == null) "Enter the pack price…" else "")`.

### Footnote

A caption under a card that explains or qualifies it ("Changes save when you go back.", "Shown in debug builds only.").

`caption` in `ink-faint` (or `ink-soft` when it carries numbers), inset 4px so it lines up with the card's rows.

- Compose: `Footnote("Exhale keeps everything on this phone.")`; `Footnote(annotated, color = c.inkSoft)`.

### Stepper

Big serif number with round minus and plus buttons, for counts changed a few steps at a time.

The value is the hero of its screen: Newsreader at 96px over a `caption` unit. Buttons are 64px `surface` circles; at the range ends they disable. Use it for daily counts and allowances, never for money.

- Compose: `Stepper(value, onChange, 1..60, "cigarettes a day")`.

### CounterRow

A count inside a list card, lighter than the Stepper: for settings changed a step or two at a time, such as Before Exhale and Starting allowance in Settings.

`body` label with an optional `caption` line on the left; on the right a 36px pill with a 1px `line` outline holding a 16px − in `ink-soft`, the value in `number-small` at body size, and a 16px +. Each button keeps a 44px touch area; holding repeats (after 420ms, every 70ms). At the range ends a button disables. `changed` shows the value in `sky-ink`, e.g. while it waits for Save. 52px minimum height, inset like ListRow so the labels line up.

- Compose: `CounterRow("Starting allowance", value, onChange, 0..100, caption = "a day, since 4 October", changed = value != saved)`.

### TimeWheel

A time of day as two wheels, hour and minutes, for moving a logged cigarette. It behaves like the iOS and Android spinners people already know.

Each wheel is two 44px rows tall: the chosen value whole in the middle (`number-large`, `ink`), its neighbours above (earlier) and below (later) cut to half, smaller and fading out to the edges, as on iOS; values that can't be chosen (later than now) are dimmer still. Drag a wheel up for later, down for earlier; it follows the finger, turns a value every half row and settles back in 160ms. Tapping the lower half steps later, the upper half earlier (each a full 44px target), one step per tap: holding doesn't repeat, since a hold is often the start of a drag. Each wheel is `wheel` (80) wide. Hours go round the clock, minutes round the hour. A `number-large` colon in `ink-soft` sits between the wheels, and in 12-hour mode the AM/PM in `label`/`ink-soft` after them. Screen readers get "One minute later" / "earlier" buttons and the time in the phone's 12/24-hour form.

- Compose: `TimeWheel(minuteOfDay, onChange, canSet = { LogEdit.moved(at, it, zone, now) != null }, h24)`.

### SegmentedControl

iOS-style segmented control with a sliding thumb for switching views or modes.

44px track (`surface-high` in light, `surface` in dark) with a thumb one step lighter or darker; selected label `ink`, others `ink-soft`. 2–3 short options. Used for theme, time ranges, weekday filters. Each segment is marked selected or not, so screen readers say which one is on.

- Compose: `SegmentedControl(listOf("System", "Light", "Dark"), selected, onSelect)`.

### ActiveFilter

Takes a segmented control's place while something more specific filters the screen, such as a day picked in Rhythm's week strip.

Same 44px track as the segmented control. The filter is named in `label-small`/`ink` ("Sunday · 10, 2 borrowed") and a thumb-coloured pill button on the right clears it ("Back to 30 days").

- Compose: `ActiveFilter(text, action = "Back to 30 days", onAction)`.

### StepProgress

Segmented progress bar for multi-step flows; filled segments are one neutral colour.

4px pill segments with 6px gaps. Done segments are `ink`, the rest `surface-high`. It only shows progress, so it stays out of the warm/cool meaning colours. Pair with a "2 / 4" badge.

- Compose: `StepProgress(step, total)`.

### GradientRing

Thick progress ring with a sky-to-oxygen sweep and content in the middle. (The Ember screen draws its own thinner countdown ring around the ember.)

Track `surface`, 28px stroke, round caps, fills clockwise from 12 o'clock. Colours run `sky` → `oxy` (time being earned back). The centre holds a `number-large` value and a caption.

- On a tinted card pass `track = c.tint(accent)`, e.g. the 104px `sun` goal ring on Money (12px stroke).
- Compose: `GradientRing(progress, listOf(c.sky, c.oxy)) { Text(...) }`.

### Dialog

A small centred card over the system scrim for something that didn't fit where it was, such as a long goal name. Content plus a Tonal "Close" button; tap outside or Back also closes it.

- Compose: `ExDialog(onDismiss) { Overline("Saving for"); Text(name, style = ExType.headline) }`.

### ChoiceDialog

A question before something that can't be undone or would be lost: erasing data, loading demo data, leaving with unsaved changes.

A centred card (`space-xl` padding) over the scrim: a `headline` title as a question, a `body`/`ink-soft` line on what happens, then Medium buttons stacked full width, the main one first as Primary, others Tonal (inside the card) or Ghost for the safe choice. Tapping outside or Back is the safe choice. No warm colour: `flare` and `ember` keep their meanings; the dialog does the warning.

- Compose: `ExChoiceDialog("Erase everything?", "Your logs… leave this phone for good.", listOf(DialogAction("Erase everything", ButtonStyle.Primary) { … }, DialogAction("Keep my data", ButtonStyle.Ghost) { … }), onDismiss)`.

### LogEditor

The modal behind a tap on a DayTimeline entry: change a cigarette's time or trigger, or a wave's trigger, or delete either.

A `surface-high` card (so the `surface` trigger chips stand out, as on the Logged card) with `space-xl` padding. The header is an `overline` ("Today · 2 of 10", "Sunday · wave at 4:38 PM, 4 min") with a 48px trash IconButton in `ink-soft` at the right, away from Save so it isn't hit by mistake. Then a "Time" overline and a TimeWheel (cigarettes only: a wave keeps when it was and how long), a "Trigger" overline and TriggerChips (tap the picked one again to clear it; "Your own" adds one), and a full-width Primary "Save". The trash asks first with a ChoiceDialog ("Delete the one at 1:05 PM?", Primary "Delete", Ghost "Keep it"). Tapping outside or Back closes without saving. Scrolls on a short screen.

- Compose: `LogEditDialog(heading, itemKey, trigger, triggers, onSave = { at, trigger -> … }, onDelete = { … }, deleteLabel = "Delete this one", onDismiss = { … }, time = EditTime(at, zone, now, h24), onAddTrigger = vm::addTrigger)`; leave out `time` for a wave. `LogEditor(...)` is the same card without the dialog window (catalogue, screenshots), built on `ExDialogCard`, the card every kit dialog uses (here with no title: the header row is its own).

### Toast

A short note that rises from the bottom for about two seconds after something saved quietly, e.g. "Saved" when going back from an edit screen, or "Saved. Pack price stays 10.00 $."

`chrome` pill with a 1px `line` ring, an 18px check in `oxy-ink`, `label-small` in `ink`; 24px above the navigation bar, or above a bottom bar when one shows. Announced politely to screen readers. Not for errors or anything that needs an answer.

- Compose: `val toast = rememberToastState()`; `toast.show("Saved")`; `ExToast(toast, Modifier.align(Alignment.BottomCenter))`.

### WeekStrip

Row of seven day circles (Rhythm shows the last 7 days, today last): each tracked day shows its count, tinted oxygen within the allowance and ember when over.

40px circles. Within allowance: oxygen tint with `oxy-ink` count. Over: ember tint with `ember-ink` count. Before the plan started (no allowance): `surface-high` with an `ink` count. Zero: `surface` with an `ink-faint` 0. Before tracking began: a hairline `line` ring, no count, not tappable. Future days: no circle. Today: 1.5px `ink` ring and an `ink` weekday letter.

- Compose: `WeekStrip(days, today)` with `DayDot(date, count, allowance)` (`count` null = not tracked, `allowance` null = before the plan); pass `selected` and `onSelect` to make tracked days tappable (Rhythm), which moves the outline from today to the picked day.

### MonthCalendar

One month of the same day circles in a 7-column grid, for History.

Weekday letters (`overline`, `ink-faint`, first day per locale), then per day its date (`overline`) above a WeekStrip circle. Today has the `ink` ring and an `ink` date. Future days show only their date, and weeks that are entirely still to come are left out. Tracked days are tappable.

- Compose: `MonthCalendar(month, days, today, onSelect = { date -> … })`.

### LegendItem

Colour key entry: a 12px dot in the accent and its meaning in `caption`/`ink-soft`. Use the full accent for the dot (not the tint) so it reads in dark.

- Compose: `Row { LegendItem(c.oxy, "Within allowance"); LegendItem(c.ember, "Borrowed from the next day") }`.

### DayTimeline

One day cigarette by cigarette, on Rhythm while a day is picked. It hangs on a 3px line in `oxy` at 55%: warm dots for smoke, the cool line between them for time without one.

Rows are 44px (a touch target): an 11px dot on the line, the time in `number-small`, the trigger in `body-small` (`ink-faint` "No trigger" when untagged) and "3 of 8" in `caption`/`ink-faint` at the end. A cigarette is an `ember` dot, `flare` past the allowance with its count in `flare-ink`. Between rows the line's length grows with the gap (log scale, 18–56px) and, before a cigarette, is labelled "3h 27m" in `caption`/`oxy-ink`. A craving wave ridden out is an `oxy` ring with its time and "Rode out a wave · Stress" in `oxy-ink`. The day before's last one opens the list as a hollow `ink-faint` dot with `caption` text; today ends with a filled `oxy` dot on an `oxy` tint halo, "Now" and the running gap in `oxy-ink`. Cigarette rows are tappable (edit); screen readers hear each as one item.

- Compose: `DayTimeline(times, today, zone, h24, onEdit = { log -> … })` with `times = DayTimes.of(...)`.

### DayLine

Today in one line, the row under the app bar on Ember. Tapping it opens today's DayTimeline on Rhythm.

A 44px `surface` pill: a 2px `line` track from 6 am (or the hour of an earlier first one) to midnight, an 8px `ember` dot per cigarette (`flare` past the allowance, each with a 1.5px `surface` ring so close ones stay apart), a 2px `oxy` stretch from the last one to now and a 10px `oxy` ring at now, at least 6px right of the newest dot so a log just made peeks out from behind it. Then "Last 11:40" (`ink-soft` "Last", the time in `number-small` at label-small size), or "Nothing yet" in `oxy-ink`, and a 16px chevron in `ink-soft`. It doesn't repeat the count: the Today badge above says it.

- Compose: `DayLine(times, now, zone, h24, onClick = { … })`.

### ListRow

Tappable row inside a card: label, optional value, chevron.

52px minimum height, `body` label, `ink-soft` value, `ink-faint` chevron. Group rows in a `surface` card with `space-sm` padding; no dividers. Rows of different kinds (CounterRow, ListRow) can share one card. With `sub`, the label gets a second `caption` line, cut short with "…" when long, while the value (a number) always shows whole: Settings' "Saving for / New headphones … 599 $".

- Compose: `ListRow("Design system", onClick, value = null)`; `ListRow("Saving for", onClick, value = "599 $", sub = goalName)`.

### HandleRow and ReorderColumn

A list row that can be dragged into a new order by its handle, with one trailing icon action, as in Settings → Triggers.

Like ListRow (52px minimum, `body` label, inside a `surface` card with `space-sm` padding) with a 44px handle area on the left (20px drag icon in `ink-faint`, `ink` while dragging) and a plain IconButton on the right (an eye that hides, an eye-off that shows). An optional badge after the label is a Badge with the `sky` accent ("Yours"); `dimmed` puts the label in `ink-soft` (a hidden trigger), and a `caption` line can sit under it. Without a handle (hidden rows) the label lines up with ListRow's. The dragged row lifts onto `surface-high` and follows the finger; the others slide a row aside (160ms) as it passes their middle, without moving in the layout, and the new order is laid out and reported once it is let go. The trailing icon crossfades when its action flips. Screen readers get "Move up" and "Move down" actions instead.

- Compose: `ReorderColumn(items, key = { it.key }, onMove = { from, to -> … }) { item, handle, dragging -> HandleRow(label, onOpen, ExIcons.Eye, "Hide $label", onHide, handle = handle, dragging = dragging, badge = "Yours") }`.

### FloatingTabBar

iOS-style floating pill tab bar, icons only; the selected tab sits in a lighter inner pill and takes its meaning colour.

`chrome` pill, 68px tall, 6px inner padding, floating 12px above the navigation bar with a 20px gutter. Light: `shadow-float`; dark: a 1px `line` ring. Selected: `chrome-selected` inner pill and the icon in its colour's ink (Ember follows heat). Unselected icons `ink-soft`. Every tab has an accessibility label. Screens leave `tab-bar-clearance` at the bottom.

- Compose: `FloatingTabBar(items = listOf(TabItem(ExIcons.Ember, "Ember", tint)), selected, onSelect)`.

### AppBar

The shared top bar of the tabs: a leading that changes per tab, and the Settings button.

Drawn over the screens (not inside them), so it stays put while pages slide. Leading: an `overline` label ("Rhythm · Last 30 days") or a badge (Ember's "7 of 10 today"). Trailing: the 48px Settings `IconButton`. Background: solid `bg` (see-through on the Glow home while its edge light shows), with a 1px `line` border along the bottom only while content is scrolled under it. A new leading drops in from 16px above with a fade (260ms) while the old drops 16px and fades (200ms). With a scroll behaviour it hides as content scrolls up and returns as it scrolls down; the status bar strip stays. Screens under it leave the header row's room (`underAppBar`).

- Compose: `ExAppBar(key = tab, onSettings, scrolled = under, scrollBehavior = behavior) { t -> Overline(labelOf(t)) }` with `val behavior = rememberAppBarScrollBehavior()` and `Modifier.nestedScroll(behavior)` around the screens; screens: `ExScreen(underAppBar = true)`. `ExScreen(header = …)` pins its header row with the same border.

## Not synced

- The web previews are static HTML/CSS renditions written by hand from the Compose components; they are not generated from the Kotlin code.
- Animated and chart components (the Ember orb, the Urge Wave, the particle field, the Smoke Clock, Air's recovery sky, gap chart and timeline) are screen-specific and are not in this kit.
- Link, ActiveFilter, MonthCalendar, LegendItem, DayTimeline, DayLine, TimeWheel, LogEditor, Dialog, ChoiceDialog, Toast, CounterRow, FieldNote, Footnote, AppBar, the Tonal button, the outlined card, the two-line ListRow and the capped and suffixed text fields are in the in-app catalogue but have no web preview yet.
