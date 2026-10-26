package com.nutriscan.app.model

/** Every failure mode the product lookup can produce, mapped to a user-facing message in the UI. */
sealed interface ProductError {
    /** Open Food Facts answered with HTTP 404, or with a `product_not_found` result. */
    data class NotFound(val barcode: String) : ProductError

    /** No usable connection (airplane mode, no signal, DNS failure). */
    data object Offline : ProductError

    /** The request exceeded the configured timeouts. */
    data object Timeout : ProductError

    /** HTTP 429 - Open Food Facts rate limits product reads to 15 requests/minute per IP. */
    data object RateLimited : ProductError

    /** Any other non-successful HTTP status. */
    data class Server(val statusCode: Int) : ProductError

    /** The response body was not valid JSON, or did not match the documented schema. */
    data object MalformedResponse : ProductError

    data object Unknown : ProductError
}

sealed interface ProductResult {
    data class Success(val product: Product, val fromCache: Boolean = false) : ProductResult
    data class Failure(val error: ProductError) : ProductResult
}
