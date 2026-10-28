package com.nutriscan.app.model

/**
 * Technical failures only. "The database does not have this barcode" is *not* an error here:
 * it is a normal lookup outcome ([LookupResult.NotFound]), which keeps missing data and
 * transport problems separate in the UI.
 */
sealed interface ProductError {
    /** The scanned/typed value cannot be a GTIN (wrong characters, wrong length). */
    data class InvalidBarcode(val raw: String, val reason: BarcodeProblem) : ProductError

    /** No usable connection (airplane mode, no signal, DNS failure). */
    data object Offline : ProductError

    /** The request exceeded the configured timeouts. */
    data object Timeout : ProductError

    /** HTTP 429 - Open Food Facts limits product reads to 15 requests/minute per IP. */
    data object RateLimited : ProductError

    /** Any other non-successful HTTP status (503 is the global rate limit). */
    data class Server(val statusCode: Int) : ProductError

    /** The response body was not valid JSON, or did not match the documented schema. */
    data object MalformedResponse : ProductError

    data object Unknown : ProductError
}

enum class BarcodeProblem { EMPTY, NON_NUMERIC, TOO_SHORT, TOO_LONG, UNSUPPORTED_LENGTH, BAD_CHECK_DIGIT }

/** Where a returned product came from, and whether it may be out of date. */
data class CacheInfo(
    val fromCache: Boolean,
    val stale: Boolean,
    /** Epoch millis of the successful fetch that produced this record. */
    val fetchedAt: Long?
) {
    companion object {
        val LIVE = CacheInfo(fromCache = false, stale = false, fetchedAt = null)
    }
}

sealed interface LookupResult {
    data class Found(
        val product: Product,
        val cache: CacheInfo,
        /** Codes actually queried, in order. Useful when a UPC-A/EAN-13 equivalent matched. */
        val resolvedCode: String? = null
    ) : LookupResult

    data class NotFound(
        val barcode: String,
        val triedCodes: List<String> = emptyList()
    ) : LookupResult

    data class Failed(val error: ProductError) : LookupResult
}
