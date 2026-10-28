# NutriScan

Lightweight Android food health scanner for Bangladeshi consumers.

**Scan Smart. Eat Better.**

Scan a packaged food barcode with the phone camera and see what Open Food Facts actually knows
about the product: name, brand, ingredients, allergens and nutrition per 100 g. Bangla and English,
switchable in-app.

## Status — milestone 2 (product data coverage)

Milestone 1 delivered the vertical slice: home → camera scan or manual entry → Open Food Facts v3
lookup → product details, with typed error states.

Milestone 2 keeps that slice intact and makes it honest about missing data:

- **Barcode handling** — EAN-13, EAN-8, UPC-A and UPC-E are validated (length, digits, mod-10 check
  digit) before any request. UPC-E is expanded to UPC-A, and UPC-A/EAN-13 equivalents are tried.
- **Lookup** — `product_type=all` reaches every Open Food Facts family, not only food. Far more
  fields are mapped (categories, labels, traces, generic name, grades, NOVA, completeness,
  last-modified, states).
- **Field-level completeness** — each of eight information groups reports Available / Missing /
  Unverified / Not applicable / User entered, with provenance and a source timestamp.
- **Multi-source** — Open Food Facts by barcode, a user-triggered name/brand search, and a local
  user entry for products no database holds. Merging happens only on an exact barcode match.
- **Unknown-product recovery** — photograph the package, type the label values, keep them on device.
- **Offline** — a JSON cache with freshness marking, a bounded negative cache and a stale banner.

Nothing is invented: missing values stay missing, no score is computed locally, and a failed request
is never presented as "product not found".

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

No database, no DI framework, no backend: the cache and the user entries are JSON files written
atomically in the app's private storage.

## Project layout

```
app/src/main/java/com/nutriscan/app/
  model/        Product, Nutrient, ProductType/ProductSource, provenance, completeness,
                ProductError/LookupResult, SearchResult, UserProductDraft
  network/      OffApi + SearchApi (Retrofit), v3 DTOs, NetworkModule (timeouts + User-Agent)
  repository/   OpenFoodFactsRepository (lookup, retries, de-duplication, cache fallback),
                OffProductMapper, ProductCache, ProductMerger, UserProductRepository,
                RecentScansRepository, ProductLookupRepository (interface)
  scanner/      BarcodeAnalyzer (CameraX ImageAnalysis + ML Kit)
  ui/           theme, home, scanner, details, search, manual entry, shared components, navigation
  util/         BarcodeValidator, PackagePhotoStore, image decoding, formatting, language
```

## Root causes found in milestone 1

| Symptom | Cause | Fix |
| --- | --- | --- |
| Products known to Open Food Facts shown as "not found" | No `product_type=all`; a barcode held by another family answers `status: failure` with `result.id = product_found_with_a_different_product_type` | Request `product_type=all`; only `product_not_found` / 404 becomes NotFound |
| QR codes and typos treated as barcodes | `normalize()` stripped every non-digit and never checked the check digit | `BarcodeValidator.inspect()` validates length, digits and mod-10 check digit, and rejects with a reason |
| Equivalent codes missed | No UPC-E expansion, no UPC-A ↔ EAN-13 handling | `expandUpcE()` + `lookupVariants()` try the documented equivalents |
| Most documented fields never shown | DTOs only covered name/brand/ingredients/nutriments | DTOs and mapper extended to the documented v3 fields |
| No offline behaviour | Cache lived in memory | JSON file cache with freshness, staleness and bounded negative entries |
| No way forward for an unknown product | No search, no manual entry | Search screen + on-device user entry with package photo |

## Open Food Facts integration

`GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json?product_type=all`

- `User-Agent: NutriScan/2.0 (Android; com.nutriscan.app)`; read access needs no credentials.
- Timeouts: 8 s connect, 12 s read, 15 s call. One retry for a timeout, up to three attempts with
  backoff for `429`/`5xx` — never for not-found, malformed or invalid input.
- `200` + `status: success|success_with_warnings` → mapped. `status: failure` → not found.
- `404` → not found, `429` → rate limited, `5xx` → server error, broken/empty body → malformed,
  `IOException` → offline. Malformed JSON is detected before the generic I/O branch, so a broken
  response is never reported as "no internet".
- Search: `GET https://search.openfoodfacts.org/search?q=…` (the v3 product API has no search
  endpoint; `/api/v2/search` answered 503 during testing). Runs only on an explicit tap because the
  public service allows about 10 searches per minute.
- Nutriments are read from the `*_100g` keys only. Salt and sodium are both reported and converted
  with `salt = sodium × 2.5`, flagged as calculated; total and added sugars stay separate. Energy is
  shown in kcal and kJ, with kJ↔kcal conversions flagged. The Nutri-Score and NOVA values are
  displayed only as Open Food Facts publishes them — never computed. A record whose grade is
  `unknown` shows "Nutri-Score is not published for this product".

## Data honesty rules

- A missing value is never turned into `0`, "unknown" or an empty string.
- `categories_tags: ["en:null"]` and similar placeholders are treated as missing, not as a category.
- Values the user typed are marked "Your entry" with a "Read from the package label" note, and are
  never presented as database data. When only a user entry exists, the screen says so instead of
  claiming a cached copy.
- A stale cached record is labelled with its fetch date; a fresh cache hit is silent.
- Conflicts between a database record and a user entry are reported, with the database value kept.

## Build and run

```bash
export JAVA_HOME="/path/to/jdk"     # Android Studio's bundled JBR works
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM tests
./gradlew :app:lintDebug            # Android lint
./gradlew :app:assembleRelease      # unsigned release APK
./gradlew :app:connectedDebugAndroidTest   # needs a device/emulator
```

In Android Studio: **Open** the project folder, let Gradle sync, pick the `app` configuration and a
device, then **Run**. `local.properties` must point at your SDK (`sdk.dir=...`).

Install on a physical phone: enable Developer options → USB debugging, connect, then
`adb install -r app/build/outputs/apk/debug/app-debug.apk` (or Run from Android Studio).

## Verified on device (Android 16 / API 36 emulator)

| Flow | Result |
| --- | --- |
| Food product, complete record | Nutella: categories, labels, nutrition table, NOVA 3, allergens, 88 % coverage |
| Food product, `nutriscore_grade: unknown` | Nutrition table with NOVA 3 and "Nutri-Score is not published" |
| Non-food product (`product_type: beauty`) | Resolved instead of "not found"; non-food banner, groups marked Not applicable |
| Placeholder tag `en:null` | No bogus "Null" category chip |
| Barcode not in any database | "Product not found" with add-details, search, retry and rescan |
| Invalid check digit | Rejected in the manual dialog with an explanation |
| Search by name | 631 results with images and barcodes; a query with no hits shows the empty state |
| Unknown product → manual entry | Package photo captured, values saved, shown as "Your entry" |
| Airplane mode, uncached barcode | "No internet connection" with retry |
| Airplane mode, fresh cache | Product shown from cache, no banner |
| Airplane mode, aged cache | "Showing a saved copy from 28 Sep 2026. It may be out of date." |
| Bangla | Home, details, manual entry, banners and error states all translated |

## Test results

- `testDebugUnitTest`: **63 tests, 0 failures** — BarcodeValidator (12), OffProductMapper (14),
  ProductCompleteness (8), ProductMerger (6), OpenFoodFactsRepository (17, MockWebServer),
  ProductCache (6).
- `lintDebug`: **0 errors**, 26 warnings (dependency-version suggestions, plurals suggestions,
  monochrome launcher icon).
- `assembleRelease`: succeeds; unsigned APK ≈ 24.5 MB, mostly the bundled ML Kit barcode model.
- Fixtures in `app/src/test/resources/` are synthetic records shaped like the documented schema:
  complete, name-only, ingredients-only, nutrition-only, not found, other-family, beauty, invalid
  JSON, `unknown` grade and placeholder tags.

## Known limitations

- Nutrition is read from the per-100 g/ml keys only; per-serving values are not derived.
- Product names and ingredients are shown as contributors supplied them; Bangla exists only when the
  database has `product_name_bn` / `ingredients_text_bn`.
- The scanner decodes EAN-13, EAN-8, UPC-A and UPC-E. QR codes and GS1 DataMatrix are not product
  barcodes and are rejected rather than guessed at.
- User entries stay on the device. There is no upload, account or sync, and no OCR of the package
  photo.
- The release APK is large because the ML Kit model ships per ABI; ABI splits or an App Bundle are
  needed before distribution.

## Next milestone

Upload of user-contributed products with an explicit confirmation step, OCR assistance for the
package photo, and per-serving nutrition derived only when the serving size is trustworthy.
