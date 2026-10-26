# NutriScan — project notes

## Stack decisions (milestone 1, 2026-10-27)
- Kotlin 2.3.0 + AGP 8.13.2 + Gradle 8.13 + JDK 21 (Android Studio JBR), compileSdk/targetSdk 36, minSdk 24.
- Compose BOM 2026.01.01; `material-icons-core` must be declared explicitly (Material3 1.4 no longer pulls icons transitively).
- CameraX 1.6.2, ML Kit `barcode-scanning` 17.3.0 (bundled model), Retrofit 2.11.0 + OkHttp 4.12.0 + Gson.
- No Hilt: `AppContainer` in `NutriScanApplication`, ViewModels built with `viewModelFactory { initializer { } }`.
- Per-app language via `AppCompatDelegate.setApplicationLocales` → `MainActivity` extends `AppCompatActivity`, theme parent `Theme.AppCompat.DayNight.NoActionBar`.

## Build commands
- JAVA_HOME must point at `C:/Program Files/Android/Android Studio/jbr` when running `./gradlew` from the shell.
- `local.properties` holds `sdk.dir` and is gitignored.

## Open Food Facts facts (verified live)
- v3 endpoint `https://world.openfoodfacts.org/api/v3/product/{barcode}.json`; envelope `code/status/product/errors/result`.
- Unknown barcode → HTTP 404, `status: failure`, `product` absent, `result.id: product_not_found`.
- Found products often report `status: success_with_warnings`, not `success`.
- Nutriments are per-100 g keys (`energy-kcal_100g`, `fat_100g`, ...); `nutrition_data_per` may be `100ml`.
- Numeric fields occasionally arrive as strings → custom Gson `FlexibleDoubleAdapterFactory`.
- Malformed/empty body surfaces as `MalformedJsonException`/`EOFException` (IOException subclasses), not `JsonParseException` — must be mapped to MalformedResponse before the generic IOException → Offline branch.
- Rate limit ≈ 15 product reads/min/IP → 429; repository caches + de-duplicates in-flight requests.

## Testing notes
- JVM tests use real captured payloads in `app/src/test/resources/`.
- `connectedDebugAndroidTest` proves the bundled ML Kit model decodes a generated EAN-13 (`app/src/androidTest/assets/`); passes in airplane mode.
- Release APK is large (~24 MB) because ML Kit ships `libbarhopper_v3.so` per ABI (~19 MB) + `.tflite` models. Use ABI splits / App Bundle before distribution.
