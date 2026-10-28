package com.nutriscan.app.repository

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.nutriscan.app.model.BarcodeProblem
import com.nutriscan.app.model.CacheInfo
import com.nutriscan.app.model.LookupResult
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductSummary
import com.nutriscan.app.model.SearchResult
import com.nutriscan.app.network.OffApi
import com.nutriscan.app.network.SearchApi
import com.nutriscan.app.util.BarcodeValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Open Food Facts implementation of [ProductLookupRepository].
 *
 * Lookup order for one scan:
 *  1. validate the code (string based, check digit included) and build equivalent GTIN variants;
 *  2. serve a fresh cache entry, or a bounded negative-cache entry;
 *  3. otherwise query `?product_type=all`, which also reaches the beauty / pet-food / products
 *     databases, trying each variant in order;
 *  4. on a transport failure, fall back to whatever is cached (flagged stale) so offline use works.
 *
 * Rate limits: 15 product reads and 10 searches per minute per IP. A duplicate scan never issues a
 * second request because lookups are de-duplicated while in flight, and only transient failures are
 * retried.
 */
class OpenFoodFactsRepository(
    private val api: OffApi,
    private val searchApi: SearchApi,
    private val cache: ProductCache,
    private val clock: () -> Long = System::currentTimeMillis,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val freshTtlMillis: Long = DEFAULT_FRESH_TTL_MILLIS
) : ProductLookupRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()
    private val inFlight = HashMap<String, Call>()

    private class Call(val deferred: Deferred<LookupResult>) {
        var waiters: Int = 0
    }

    override suspend fun lookup(barcode: String, forceRefresh: Boolean): LookupResult {
        val code = BarcodeValidator.sanitize(barcode)
            ?: return LookupResult.Failed(ProductError.InvalidBarcode(barcode, problemOf(barcode)))
        if (!BarcodeValidator.isSupportedLength(code)) {
            return LookupResult.Failed(ProductError.InvalidBarcode(code, BarcodeProblem.UNSUPPORTED_LENGTH))
        }
        val variants = BarcodeValidator.lookupVariants(code)

        if (!forceRefresh) {
            cachedFresh(variants)?.let { return it }
            if (cache.isKnownMissing(code)) return LookupResult.NotFound(code, variants)
        }

        val call = synchronized(lock) {
            inFlight[code] ?: Call(scope.async { fetchVariants(variants) }).also { inFlight[code] = it }
        }
        synchronized(lock) { call.waiters++ }

        return try {
            call.deferred.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            synchronized(lock) {
                call.waiters--
                if (call.waiters <= 0) {
                    if (inFlight[code] === call) inFlight.remove(code)
                    // Nobody is waiting any more: stop the HTTP call instead of finishing it.
                    if (!call.deferred.isCompleted) call.deferred.cancel()
                }
            }
        }
    }

    override suspend fun search(query: String, limit: Int): SearchResult {
        val trimmed = query.trim()
        if (trimmed.length < SearchApi.MIN_QUERY_LENGTH) return SearchResult.Empty

        var attempt = 0
        while (true) {
            val outcome = searchOnce(trimmed, limit)
            val retry = (outcome as? SearchResult.Failure)?.let { retryDelay(it.error, attempt) }
            if (retry == null) return outcome
            pause(retry)
            attempt++
        }
    }

    private suspend fun fetchVariants(variants: List<String>): LookupResult {
        var failure: ProductError? = null
        for (variant in variants) {
            when (val result = fetchWithRetry(variant)) {
                is LookupResult.Found -> return result
                is LookupResult.NotFound -> Unit
                is LookupResult.Failed -> {
                    // A transport problem will not be fixed by trying another representation.
                    failure = result.error
                    break
                }
            }
        }

        if (failure != null) {
            return cachedAny(variants) ?: LookupResult.Failed(failure)
        }

        cache.markMissing(variants.first())
        return LookupResult.NotFound(variants.first(), variants)
    }

    private suspend fun fetchWithRetry(code: String): LookupResult {
        var attempt = 0
        while (true) {
            val result = fetchOnce(code)
            val retry = (result as? LookupResult.Failed)?.let { retryDelay(it.error, attempt) }
            if (retry == null) return result
            pause(retry)
            attempt++
        }
    }

    private suspend fun fetchOnce(code: String): LookupResult = try {
        val response = api.getProduct(code)
        if (!response.isSuccessful) {
            when (response.code()) {
                HTTP_NOT_FOUND -> LookupResult.NotFound(code, listOf(code))
                HTTP_TOO_MANY_REQUESTS -> LookupResult.Failed(ProductError.RateLimited)
                else -> LookupResult.Failed(ProductError.Server(response.code()))
            }
        } else {
            val body = response.body()
            val dto = body?.product
            if (dto == null || body.status == STATUS_FAILURE) {
                // "failure" covers product_not_found and product_found_with_a_different_product_type.
                LookupResult.NotFound(code, listOf(code))
            } else {
                val product = OffProductMapper.map(code, dto)
                val fetchedAt = clock()
                cache.write(product, fetchedAt)
                LookupResult.Found(
                    product = product,
                    cache = CacheInfo(fromCache = false, stale = false, fetchedAt = fetchedAt),
                    resolvedCode = code
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (malformed: JsonParseException) {
        LookupResult.Failed(ProductError.MalformedResponse)
    } catch (malformed: MalformedJsonException) {
        // Gson reports syntactically broken JSON as an IOException subclass, not a JsonParseException.
        LookupResult.Failed(ProductError.MalformedResponse)
    } catch (truncated: EOFException) {
        // Empty or truncated body: the server answered, but not with a product.
        LookupResult.Failed(ProductError.MalformedResponse)
    } catch (timeout: SocketTimeoutException) {
        LookupResult.Failed(ProductError.Timeout)
    } catch (interrupted: InterruptedIOException) {
        // OkHttp surfaces a hit callTimeout as a plain InterruptedIOException.
        LookupResult.Failed(ProductError.Timeout)
    } catch (io: IOException) {
        // UnknownHostException, ConnectException, no route to host, connection reset...
        LookupResult.Failed(ProductError.Offline)
    } catch (unexpected: Exception) {
        LookupResult.Failed(ProductError.Unknown)
    }

    private suspend fun searchOnce(query: String, limit: Int): SearchResult = try {
        val response = searchApi.search(query, limit.coerceIn(1, MAX_SEARCH_PAGE_SIZE))
        if (!response.isSuccessful) {
            when (response.code()) {
                HTTP_TOO_MANY_REQUESTS -> SearchResult.Failure(ProductError.RateLimited)
                else -> SearchResult.Failure(ProductError.Server(response.code()))
            }
        } else {
            val body = response.body()
            val items = body?.hits.orEmpty().mapNotNull { hit ->
                val code = hit.code?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ProductSummary(
                    barcode = code,
                    name = hit.productName?.trim()?.ifEmpty { null },
                    brand = brandsOf(hit.brands),
                    imageUrl = hit.imageSmallUrl?.trim()?.ifEmpty { null },
                    quantity = hit.quantity?.trim()?.ifEmpty { null }
                )
            }
            if (items.isEmpty()) SearchResult.Empty else SearchResult.Success(items, body?.count ?: items.size)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (malformed: JsonParseException) {
        SearchResult.Failure(ProductError.MalformedResponse)
    } catch (malformed: MalformedJsonException) {
        SearchResult.Failure(ProductError.MalformedResponse)
    } catch (truncated: EOFException) {
        SearchResult.Failure(ProductError.MalformedResponse)
    } catch (timeout: SocketTimeoutException) {
        SearchResult.Failure(ProductError.Timeout)
    } catch (interrupted: InterruptedIOException) {
        SearchResult.Failure(ProductError.Timeout)
    } catch (io: IOException) {
        SearchResult.Failure(ProductError.Offline)
    } catch (unexpected: Exception) {
        SearchResult.Failure(ProductError.Unknown)
    }

    private fun brandsOf(element: com.google.gson.JsonElement?): String? {
        val value = element ?: return null
        return when {
            value.isJsonArray -> value.asJsonArray
                .mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.trim() }
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString(", ")
                .ifEmpty { null }

            value.isJsonPrimitive -> value.asString.trim().ifEmpty { null }
            else -> null
        }
    }

    private fun cachedFresh(variants: List<String>): LookupResult? {
        for (variant in variants) {
            val entry = cache.read(variant) ?: continue
            val age = clock() - entry.fetchedAt
            if (age in 0..freshTtlMillis) {
                return LookupResult.Found(
                    product = entry.product,
                    cache = CacheInfo(fromCache = true, stale = false, fetchedAt = entry.fetchedAt),
                    resolvedCode = variant
                )
            }
        }
        return null
    }

    private fun cachedAny(variants: List<String>): LookupResult? {
        for (variant in variants) {
            val entry = cache.read(variant) ?: continue
            return LookupResult.Found(
                product = entry.product,
                cache = CacheInfo(fromCache = true, stale = true, fetchedAt = entry.fetchedAt),
                resolvedCode = variant
            )
        }
        return null
    }

    /**
     * Retry only what can succeed on a second try. A missing product, a malformed body or a bad
     * barcode is never retried, so the app cannot hammer the API.
     */
    private fun retryDelay(error: ProductError, attempt: Int): Long? {
        if (attempt >= MAX_ATTEMPTS - 1) return null
        return when (error) {
            ProductError.RateLimited -> BACKOFF_MILLIS[attempt.coerceAtMost(BACKOFF_MILLIS.lastIndex)]
            is ProductError.Server ->
                if (error.statusCode >= 500) BACKOFF_MILLIS[attempt.coerceAtMost(BACKOFF_MILLIS.lastIndex)] else null

            ProductError.Timeout -> if (attempt == 0) TIMEOUT_RETRY_MILLIS else null
            else -> null
        }
    }

    private fun problemOf(raw: String): BarcodeProblem {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return BarcodeProblem.EMPTY
        val digits = trimmed.count { it.isDigit() }
        val separators = trimmed.count { it == ' ' || it == '-' }
        if (digits + separators != trimmed.length) return BarcodeProblem.NON_NUMERIC
        if (digits < BarcodeValidator.MIN_LENGTH) return BarcodeProblem.TOO_SHORT
        if (digits > BarcodeValidator.MAX_LENGTH) return BarcodeProblem.TOO_LONG
        if (!BarcodeValidator.isSupportedLength(digits.toString())) return BarcodeProblem.UNSUPPORTED_LENGTH
        return BarcodeProblem.BAD_CHECK_DIGIT
    }

    private companion object {
        const val DEFAULT_FRESH_TTL_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_ATTEMPTS = 3
        const val MAX_SEARCH_PAGE_SIZE = 50
        const val TIMEOUT_RETRY_MILLIS = 500L
        val BACKOFF_MILLIS = longArrayOf(700L, 1_600L)

        const val STATUS_FAILURE = "failure"
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
