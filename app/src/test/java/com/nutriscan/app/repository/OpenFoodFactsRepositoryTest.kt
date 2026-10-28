package com.nutriscan.app.repository

import com.nutriscan.app.model.LookupResult
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.model.SearchResult
import com.nutriscan.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class OpenFoodFactsRepositoryTest {

    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_760_000_000_000L
    private lateinit var cache: ProductCache

    private val thirteenDigitCode = "3017620422003"
    private val upcA = "012345000065"
    private val ean13Equivalent = "0012345000065"

    @Before
    fun setUp() {
        cache = ProductCache(File(temp.root, "cache.json"), NetworkModule.createGson(), clock = { now })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(
        baseUrl: String = server.url("/").toString(),
        client: OkHttpClient = NetworkModule.createOkHttpClient()
    ) = OpenFoodFactsRepository(
        api = NetworkModule.createApi(client, baseUrl),
        searchApi = NetworkModule.createSearchApi(client, baseUrl),
        cache = cache,
        clock = { now },
        ioDispatcher = Dispatchers.IO,
        pause = { }
    )

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    private fun ok(name: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(fixture(name))

    @Test
    fun foundProductIsMappedAndCached() {
        server.enqueue(ok("product_complete.json"))

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Found)
        val found = result as LookupResult.Found
        assertEquals("Sunrise Oat Biscuits", found.product.name)
        assertEquals(false, found.cache.fromCache)
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/v3/product/$thirteenDigitCode.json"))
        // Without product_type=all a barcode held in another family database looks like a 404.
        assertTrue(request.path!!.contains("product_type=all"))
        assertEquals("NutriScan/2.0 (Android; com.nutriscan.app)", request.getHeader("User-Agent"))
        assertEquals("Sunrise Oat Biscuits", cache.read(thirteenDigitCode)?.product?.name)
    }

    @Test
    fun unknownBarcodeIsNotFoundAndNotRetried() {
        server.enqueue(MockResponse().setResponseCode(404).setBody(fixture("product_not_found.json")))

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.NotFound)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun productHeldInAnotherFamilyDatabaseIsNotFoundNotMalformed() {
        server.enqueue(ok("product_found_other_family.json"))

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.NotFound)
    }

    @Test
    fun rateLimitIsRetriedThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(ok("product_complete.json"))

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Found)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverErrorIsRetriedThenReported() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Failed)
        assertEquals(ProductError.Server(503), (result as LookupResult.Failed).error)
        // Three attempts in total, then stop: no endless retry loop.
        assertEquals(3, server.requestCount)
    }

    @Test
    fun malformedBodyIsReportedWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("product_invalid.json")))

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Failed)
        assertEquals(ProductError.MalformedResponse, (result as LookupResult.Failed).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun upcALookupFallsBackToItsEan13Representation() {
        server.enqueue(MockResponse().setResponseCode(404).setBody(fixture("product_not_found.json")))
        server.enqueue(ok("product_complete.json"))

        val result = runBlocking { repository().lookup(upcA) }

        assertTrue(result is LookupResult.Found)
        assertEquals(ean13Equivalent, (result as LookupResult.Found).resolvedCode)
        assertEquals(2, server.requestCount)

        assertTrue(server.takeRequest().path!!.contains(upcA))
        assertTrue(server.takeRequest().path!!.contains(ean13Equivalent))
    }

    @Test
    fun secondLookupIsServedFromCache() {
        server.enqueue(ok("product_complete.json"))
        val repository = repository()

        val first = runBlocking { repository.lookup(thirteenDigitCode) }
        val second = runBlocking { repository.lookup(thirteenDigitCode) }

        assertTrue(first is LookupResult.Found)
        assertTrue(second is LookupResult.Found)
        assertEquals(false, (first as LookupResult.Found).cache.fromCache)
        assertEquals(true, (second as LookupResult.Found).cache.fromCache)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun forceRefreshIgnoresTheCache() {
        server.enqueue(ok("product_complete.json"))
        server.enqueue(ok("product_complete.json"))
        val repository = repository()

        runBlocking { repository.lookup(thirteenDigitCode) }
        runBlocking { repository.lookup(thirteenDigitCode, forceRefresh = true) }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun notFoundIsNegativelyCachedForAShortTimeOnly() {
        server.enqueue(MockResponse().setResponseCode(404).setBody(fixture("product_not_found.json")))
        val repository = repository()

        val first = runBlocking { repository.lookup(thirteenDigitCode) }
        val second = runBlocking { repository.lookup(thirteenDigitCode) }

        assertTrue(first is LookupResult.NotFound)
        assertTrue(second is LookupResult.NotFound)
        assertEquals(1, server.requestCount)

        // Past the negative TTL the product is looked up again, so new records are picked up.
        now += 11 * 60 * 1000
        server.enqueue(ok("product_complete.json"))
        val third = runBlocking { repository.lookup(thirteenDigitCode) }

        assertTrue(third is LookupResult.Found)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun staleCacheIsUsedWhenTheNetworkFails() {
        cache.write(seedProduct(), fetchedAt = now - 30L * 24 * 60 * 60 * 1000)
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = runBlocking { repository().lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Found)
        val found = result as LookupResult.Found
        assertEquals(true, found.cache.fromCache)
        assertEquals(true, found.cache.stale)
    }

    @Test
    fun offlineIsReportedAsOffline() {
        // Port 1 is never listening, so the connection fails immediately.
        val result = runBlocking { repository(baseUrl = "http://127.0.0.1:1/").lookup(thirteenDigitCode) }

        assertTrue(result is LookupResult.Failed)
        assertEquals(ProductError.Offline, (result as LookupResult.Failed).error)
    }

    @Test
    fun invalidBarcodeNeverReachesTheNetwork() {
        val result = runBlocking { repository().lookup("12ab") }

        assertTrue(result is LookupResult.Failed)
        assertTrue((result as LookupResult.Failed).error is ProductError.InvalidBarcode)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun duplicateConcurrentLookupsShareOneRequest() {
        server.enqueue(ok("product_complete.json").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val repository = repository()

        val results = runBlocking {
            coroutineScope {
                val first = async { repository.lookup(thirteenDigitCode) }
                val second = async { repository.lookup(thirteenDigitCode) }
                listOf(first.await(), second.await())
            }
        }

        assertTrue(results.all { it is LookupResult.Found })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellingACallerDoesNotWedgeLaterLookups() {
        server.enqueue(ok("product_complete.json").setBodyDelay(400, TimeUnit.MILLISECONDS))
        val repository = repository()

        runBlocking {
            val job = launch { repository.lookup(thirteenDigitCode) }
            delay(80)
            job.cancel()
            delay(600)
        }

        server.enqueue(ok("product_complete.json"))
        val second = runBlocking { repository.lookup(thirteenDigitCode) }

        assertTrue(second is LookupResult.Found)
        assertTrue(server.requestCount >= 2)
    }

    @Test
    fun searchMapsHits() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "count": 2,
                  "page": 1,
                  "page_size": 20,
                  "hits": [
                    {
                      "code": "3017620422003",
                      "product_name": "Example Spread",
                      "brands": ["Example Brand", "Second Brand"],
                      "image_small_url": "https://example.invalid/small.jpg",
                      "quantity": "400 g"
                    },
                    { "product_name": "No code, ignored" }
                  ]
                }
                """.trimIndent()
            )
        )

        val result = runBlocking { repository().search("example spread") }

        assertTrue(result is SearchResult.Success)
        val success = result as SearchResult.Success
        assertEquals(2, success.total)
        assertEquals(1, success.items.size)
        assertEquals("3017620422003", success.items.first().barcode)
        assertEquals("Example Brand, Second Brand", success.items.first().brand)
        assertTrue(server.takeRequest().path!!.startsWith("/search?q=example%20spread"))
    }

    @Test
    fun shortSearchQueryDoesNotHitTheNetwork() {
        val result = runBlocking { repository().search("a") }

        assertEquals(SearchResult.Empty, result)
        assertEquals(0, server.requestCount)
    }

    private fun seedProduct() = Product(
        barcode = thirteenDigitCode,
        productType = ProductType.FOOD,
        name = "Cached product",
        nameEn = "Cached product",
        nameBn = null,
        genericName = null,
        brand = null,
        categories = emptyList(),
        labels = emptyList(),
        quantity = null,
        servingSize = null,
        ingredients = null,
        ingredientsEn = null,
        ingredientsBn = null,
        ingredientCount = null,
        allergens = emptyList(),
        traces = emptyList(),
        imageUrl = null,
        imageFrontUrl = null,
        nutritionBasis = NutritionBasis.PER_100_G,
        nutrients = listOf(Nutrient(NutrientKind.FAT, 1.0, "g")),
        nutritionGrade = null,
        nutritionScore = null,
        novaGroup = null,
        lastModifiedEpochSeconds = 1_750_000_000L,
        offCompleteness = null,
        states = emptySet(),
        source = ProductSource.OPEN_FOOD_FACTS
    )
}
