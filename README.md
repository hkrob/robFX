# Parity

Clean, sharp currency converter for Android (Compose, Material 3, target SDK 36).

- Rates: median of Frankfurter (ECB), fawazahmed0 currency-api and open.er-api.com, queried in parallel; status pill warns when sources differ >0.5%.
- Charts: Frankfurter time series, currency-api dated snapshots as fallback. Scrub by dragging.
- Calculator keypad with + - × ÷, widget, background rate alerts (~30 min), Auto/Light/Dark/AMOLED + Material You.

Build: `ANDROID_HOME=... ./gradlew :app:assembleDebug`. Headless screenshots: `ROBOLECTRIC_DEPS=<dir with android-all jar> ./gradlew :app:testDebugUnitTest` writes `app/build/screens/`.
