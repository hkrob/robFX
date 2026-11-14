# robFX

Clean, sharp currency converter for Android (Compose, Material 3, compile and target SDK 37 / Android 17).

- Rates: median of Frankfurter (ECB), fawazahmed0 currency-api and open.er-api.com, queried in parallel; status pill warns when sources differ >0.5%.
- Charts: Frankfurter time series, currency-api dated snapshots as fallback. Scrub by dragging.
- Unit price: compare shop items by price per 100 g / kg / lb / L / fl oz / m / each (weight, volume, length, count), with multipack quantities, prices in any currency converted to your home currency, recent comparisons, and optional tags ("Pasta", "Milk") that are kept in history and filterable.
- Calculator keypad with + - × ÷, widget, background rate alerts (~30 min), Auto/Light/Dark/AMOLED + Material You.

Build: `ANDROID_HOME=... ./gradlew :app:assembleDebug`. Headless screenshots: `ROBOLECTRIC_DEPS=<dir with android-all jar> ./gradlew :app:testDebugUnitTest` writes `app/build/screens/`.

## Updates

robFX updates itself from this repo's GitHub releases.

- It checks on launch (at most every 3 hours) and in the background every 6 hours. Settings -> Updates shows the state and has a manual check.
- "Update automatically" (on by default) downloads a new version in the background. Android never allows a fully silent *first* install, so you confirm once; after that Android 12+ may apply updates without a prompt, otherwise a notification takes you to the system install dialog.
- On first use, Android asks you to allow robFX to install apps ("Install unknown apps"). Settings -> Updates -> Allow installs takes you there.
- The APK's SHA-256 is checked against the release when GitHub provides it, the file must be this app and a newer version, and Android itself refuses anything not signed with the same key.

## CI and releasing

- `.github/workflows/ci.yml` runs unit tests, lint and a debug build on every push and pull request.
- `.github/workflows/release.yml` builds the signed APK and publishes a GitHub release. Steps:
  1. Add four repository secrets (Settings -> Secrets and variables -> Actions):
     `KEYSTORE_BASE64` (`base64 -w0 robfx.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
     (the values are in your `keystore.properties`).
  2. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and push to `main`.
  3. Actions -> Release -> Run workflow (or push a tag matching the version, e.g. `v1.0.4`).

  The job refuses to publish if the APK isn't signed with the key whose SHA-256 is pinned in the workflow, since phones that already have robFX would reject it.
