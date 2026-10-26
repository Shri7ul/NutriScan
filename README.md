# NutriScan

Lightweight Android food health scanner for Bangladeshi consumers.

**Scan Smart. Eat Better.**

Scan a packaged food barcode with the phone camera and see what Open Food Facts actually knows
about the product: name, brand, ingredients, allergens and nutrition per 100 g. Bangla and English,
switchable in-app.

## Status — milestone 1

Working vertical slice: home → camera scan or manual entry → Open Food Facts v3 lookup →
product details, with typed error states. No nutrition scoring, OCR, accounts or sync yet.

## Stack

| Concern | Choice |
| --- | --- |
| Language / UI | Kotlin 2.3.0, Jetpack Compose (BOM 2026.01.01), Material 3 |
| Build | AGP 8.13.2, Gradle 8.13, JDK 21 (JDK 17+ works), compileSdk/targetSdk 36, minSdk 24 |
| Camera | CameraX 1.6.2 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| Barcode | Google ML Kit `barcode-scanning` 17.3.0 — **bundled** model, works offline after install |
| Network | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson |
| State | ViewModel + StateFlow, coroutines, lifecycle-aware collection |
| Navigation | Navigation Compose 2.9.7 |
| Localization | AppCompatDelegate per-app locales (platform API on Android 13+, AppCompat below) |

## Project layout

```
app/src/main/java/com/nutriscan/app/
  model/        Product, Nutrient, RecentScan, ProductError/ProductResult
  network/      OffApi (Retrofit), OFF v3 DTOs, NetworkModule (timeouts + User-Agent)
  repository/   ProductRepository (cache + request de-duplication), mapper, recent scans
  scanner/      BarcodeAnalyzer (CameraX ImageAnalysis + ML Kit)
  ui/           theme, home, scanner, details, shared components, navigation
  util/         barcode validation, formatting, language selection
```

## Open Food Facts integration

`GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json`

- Sends `User-Agent: NutriScan/1.0 (Android; com.nutriscan.app)`; read access needs no credentials.
- Timeouts: 15 s connect, 20 s read, 30 s call.
- `200` + `status: success|success_with_warnings` → parsed; `404` / `status: failure` → not found.
- `429` → rate limited (Open Food Facts allows ~15 product reads/minute/IP), `5xx` → server error,
  broken JSON → malformed response, `IOException` → offline, socket/call timeout → timeout.
- Missing fields stay missing: the UI shows "Not available" instead of guessing.

## Build and run

```bash
export JAVA_HOME="/path/to/jdk"     # Android Studio's bundled JBR works
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM tests
./gradlew :app:connectedDebugAndroidTest   # needs a device/emulator
```

In Android Studio: **Open** the project folder, let Gradle sync, pick the `app` configuration and a
device, then **Run**. `local.properties` must point at your SDK (`sdk.dir=...`).

Install on a physical phone: enable Developer options → USB debugging, connect, then
`adb install -r app/build/outputs/apk/debug/app-debug.apk` (or Run from Android Studio).

## Known limitations

- Nutrition is read from the per-100 g/ml nutriment keys only; per-serving values are not derived.
- Product names/ingredients come from Open Food Facts in the language contributors supplied them;
  Bangla variants exist only when the database has them (`product_name_bn`).
- Scanner accepts EAN-13, EAN-8, UPC-A and UPC-E only.
- No nutrition scoring, ingredient risk analysis, OCR, authentication or cloud sync (later milestones).
