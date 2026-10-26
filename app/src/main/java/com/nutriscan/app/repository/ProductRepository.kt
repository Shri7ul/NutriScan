package com.nutriscan.app.repository

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductResult
import com.nutriscan.app.network.OffApi
import com.nutriscan.app.util.BarcodeValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Single entry point for product lookups.
 *
 * Two protections keep the Open Food Facts rate limit (15 product reads/minute/IP) in reach:
 *  - a small in-memory LRU cache, so reopening a product costs no request;
 *  - request de-duplication, so concurrent lookups of the same barcode share one HTTP call.
 * This is also what makes repeated scanner frames harmless: a duplicate detection never
 * produces a second request.
 */
class ProductRepository(
    private val api: OffApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, Product>(cacheCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Product>?): Boolean =
            size > cacheCapacity
    }

    private val inFlightLock = Any()
    private val inFlight = HashMap<String, Deferred<ProductResult>>()

    suspend fun getProduct(barcode: String, forceRefresh: Boolean = false): ProductResult {
        val key = BarcodeValidator.normalize(barcode)
        if (!BarcodeValidator.isValid(key)) {
            return ProductResult.Failure(ProductError.NotFound(barcode))
        }

        if (!forceRefresh) {
            cached(key)?.let { return ProductResult.Success(it, fromCache = true) }
        }

        val request = synchronized(inFlightLock) {
            inFlight[key] ?: repositoryScope.async { fetch(key) }.also { inFlight[key] = it }
        }

        return try {
            request.await()
        } finally {
            synchronized(inFlightLock) {
                if (inFlight[key] === request) inFlight.remove(key)
            }
        }
    }

    private fun cached(key: String): Product? = synchronized(cacheLock) { cache[key] }

    private fun store(product: Product) {
        synchronized(cacheLock) { cache[product.barcode] = product }
    }

    private suspend fun fetch(barcode: String): ProductResult = try {
        val response = api.getProduct(barcode)
        if (!response.isSuccessful) {
            ProductResult.Failure(httpError(barcode, response.code()))
        } else {
            val body = response.body()
            when {
                body == null -> ProductResult.Failure(ProductError.MalformedResponse)
                body.product == null ||
                    body.status == STATUS_FAILURE ||
                    body.result?.id == RESULT_PRODUCT_NOT_FOUND ->
                    ProductResult.Failure(ProductError.NotFound(barcode))
                else -> {
                    val product = OffProductMapper.map(barcode, body.product)
                    store(product)
                    ProductResult.Success(product)
                }
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (malformed: JsonParseException) {
        ProductResult.Failure(ProductError.MalformedResponse)
    } catch (malformed: MalformedJsonException) {
        // Gson reports syntactically broken JSON as an IOException subclass, not a JsonParseException.
        ProductResult.Failure(ProductError.MalformedResponse)
    } catch (truncated: EOFException) {
        // Empty or truncated body: the server answered, but not with a product.
        ProductResult.Failure(ProductError.MalformedResponse)
    } catch (timeout: SocketTimeoutException) {
        ProductResult.Failure(ProductError.Timeout)
    } catch (interrupted: InterruptedIOException) {
        // OkHttp surfaces a hit callTimeout as a plain InterruptedIOException.
        ProductResult.Failure(ProductError.Timeout)
    } catch (io: IOException) {
        // UnknownHostException, ConnectException, no route to host, connection reset...
        ProductResult.Failure(ProductError.Offline)
    } catch (unexpected: Exception) {
        ProductResult.Failure(ProductError.Unknown)
    }

    private fun httpError(barcode: String, statusCode: Int): ProductError = when (statusCode) {
        HTTP_NOT_FOUND -> ProductError.NotFound(barcode)
        HTTP_TOO_MANY_REQUESTS -> ProductError.RateLimited
        else -> ProductError.Server(statusCode)
    }

    private companion object {
        const val DEFAULT_CACHE_CAPACITY = 32
        const val STATUS_FAILURE = "failure"
        const val RESULT_PRODUCT_NOT_FOUND = "product_not_found"
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
