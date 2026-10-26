package com.nutriscan.app.repository

import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductResult
import com.nutriscan.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProductRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = NetworkModule.okHttpBuilder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .build()

        repository = ProductRepository(
            api = NetworkModule.createApi(client, server.url("/").toString()),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful lookup returns a product and sends the documented request`() = runBlocking {
        server.enqueue(jsonResponse(200, fixture("off_product_success.json")))

        val result = repository.getProduct(BARCODE)

        assertTrue(result is ProductResult.Success)
        val product = (result as ProductResult.Success).product
        assertTrue(product.name!!.startsWith("Thai peanut noodle kit"))

        val request = server.takeRequest()
        assertEquals("/api/v3/product/$BARCODE.json", request.path)
        assertEquals(NetworkModule.USER_AGENT, request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `404 is reported as product not found`() = runBlocking {
        server.enqueue(jsonResponse(404, fixture("off_product_not_found.json")))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.NotFound(BARCODE), (result as ProductResult.Failure).error)
    }

    @Test
    fun `200 with a product_not_found result is reported as not found`() = runBlocking {
        server.enqueue(jsonResponse(200, fixture("off_product_not_found.json")))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.NotFound(BARCODE), (result as ProductResult.Failure).error)
    }

    @Test
    fun `429 is reported as rate limited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.RateLimited, (result as ProductResult.Failure).error)
    }

    @Test
    fun `server errors keep the status code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.Server(503), (result as ProductResult.Failure).error)
    }

    @Test
    fun `malformed json is reported instead of crashing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{ this is not json"))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.MalformedResponse, (result as ProductResult.Failure).error)
    }

    @Test
    fun `empty body is reported as malformed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.MalformedResponse, (result as ProductResult.Failure).error)
    }

    @Test
    fun `a refused connection is reported as offline`() = runBlocking {
        server.shutdown()

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.Offline, (result as ProductResult.Failure).error)
    }

    @Test
    fun `a silent server is reported as a timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = repository.getProduct(BARCODE)

        assertEquals(ProductError.Timeout, (result as ProductResult.Failure).error)
    }

    @Test
    fun `reopening a product does not hit the network again`() = runBlocking {
        server.enqueue(jsonResponse(200, fixture("off_product_success.json")))

        repository.getProduct(BARCODE)
        val second = repository.getProduct(BARCODE)

        assertEquals(1, server.requestCount)
        assertTrue((second as ProductResult.Success).fromCache)
    }

    @Test
    fun `concurrent lookups of the same barcode issue one request`() = runBlocking {
        server.enqueue(
            jsonResponse(200, fixture("off_product_success.json"))
                .setBodyDelay(300, TimeUnit.MILLISECONDS)
        )

        val results = listOf(
            async(Dispatchers.Default) { repository.getProduct(BARCODE) },
            async(Dispatchers.Default) { repository.getProduct(BARCODE) },
            async(Dispatchers.Default) { repository.getProduct(BARCODE) }
        ).awaitAll()

        assertTrue(results.all { it is ProductResult.Success })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `force refresh bypasses the cache`() = runBlocking {
        server.enqueue(jsonResponse(200, fixture("off_product_success.json")))
        server.enqueue(jsonResponse(200, fixture("off_product_success.json")))

        repository.getProduct(BARCODE)
        repository.getProduct(BARCODE, forceRefresh = true)

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an invalid barcode never reaches the network`() = runBlocking {
        val result = repository.getProduct("12ab")

        assertTrue(result is ProductResult.Failure)
        assertEquals(0, server.requestCount)
    }

    private fun jsonResponse(code: Int, body: String): MockResponse =
        MockResponse().setResponseCode(code).setBody(body)

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing test fixture $name"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val BARCODE = "737628064502"
    }
}
