package com.nutriscan.app.repository

import com.nutriscan.app.model.LookupResult
import com.nutriscan.app.model.SearchResult

/**
 * The single entry point the UI uses for product data.
 *
 * Kept as an interface so another source (a national database, a retailer catalogue, a paid GS1
 * feed) can be added behind the same contract without touching the screens. This milestone ships
 * one implementation, [OpenFoodFactsRepository], which queries Open Food Facts and its family
 * databases.
 */
interface ProductLookupRepository {

    /**
     * Looks up [barcode], trying equivalent GTIN representations and falling back to the on-device
     * cache. Cancelling the calling coroutine cancels the HTTP request once no other caller is
     * waiting on it.
     */
    suspend fun lookup(barcode: String, forceRefresh: Boolean = false): LookupResult

    /** Full-text search by product name or brand. Returns [SearchResult.Empty] for short queries. */
    suspend fun search(query: String, limit: Int = 20): SearchResult
}
