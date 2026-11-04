# Exhale — agent guide

Read `docs/SPEC.md` before changing behaviour, and `docs/DESIGN-SYSTEM.md` before changing visuals. It defines the product, the
"two temperatures" design language, the domain rules and the architecture.

## Commands
- Build: `./gradlew assembleDebug`
- Tests (unit + screenshot): `./gradlew testDebugUnitTest`
- Re-render screenshots: `./gradlew recordRoborazziDebug` → `app/build/outputs/roborazzi/`
- Needs JDK 17+ and an Android SDK (`local.properties` → `sdk.dir=...`, or `ANDROID_HOME`).

## Rules
- Kotlin + Jetpack Compose only. No XML layouts.
- Local data only (Room + DataStore). Do not add network, analytics or the INTERNET permission.
- `domain/` stays pure Kotlin (no `android.*` imports) and every rule change gets a unit test in `app/src/test`.
- Screens are stateless: take `ExhaleState` + callbacks. Derive numbers in `ExhaleViewModel` or `domain/`.
- Colours only from `Ex.colors` (`val c = Ex.colors`), text only from `ExType`, spacing/radii/sizes from `ExSpace`/`ExRadius`/`ExSize`. Warm colours mean smoke, cool colours mean recovery; never swap them.
- Accents (`c.ember`, `c.oxy`, …) are for fills and graphics. Text or small icons in an accent use `c.oxyInk` etc. or `c.inkOf(accent)` (contrast in light theme).
- Build screens from the kit in `ui/components/` (`ExScreen`, `ExButton`, `ExCard`, `ExChip`, …); add a new component there and to `UiKitScreen` rather than styling inline. Every screen must look right in both themes; screenshot tests render dark and light.
- Read colours inside Canvas/DrawScope from values captured before the lambda (`val dot = c.ink`), never by calling `Ex.colors` inside it.
- Copy never shames: no "failed", "relapse", "streak lost". Over allowance is "borrowed from tomorrow".
- Keep each haptic pattern tied to one meaning (see `Haptics.kt`).
- Every new screen gets a case in `ScreenshotTest`; update `docs/screenshots/` when visuals change.
- Room schema change → bump version, add a Migration, commit `app/schemas/`.
- Git: work on the branch you are on. Don't create new branches unless asked.
- Releases: the version lives only in `version.properties`. Don't bump it unless asked; bumping it and pushing to `main` publishes a release.
- `app/debug.keystore` is a shared debug key committed on purpose (stable signature for sideloaded updates). Don't replace or delete it.
- Update `docs/SPEC.md` in the same change when behaviour or rules change.
