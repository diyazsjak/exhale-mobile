# Exhale — agent guide

Read `docs/SPEC.md` before changing behaviour or visuals. It defines the product, the
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
- Colours only from `Ex` tokens, text only from `ExType`. Warm colours mean smoke, cool colours mean recovery; never swap them.
- Copy never shames: no "failed", "relapse", "streak lost". Over allowance is "borrowed from tomorrow".
- Keep each haptic pattern tied to one meaning (see `Haptics.kt`).
- Every new screen gets a case in `ScreenshotTest`; update `docs/screenshots/` when visuals change.
- Room schema change → bump version, add a Migration, commit `app/schemas/`.
- Update `docs/SPEC.md` in the same change when behaviour or rules change.
