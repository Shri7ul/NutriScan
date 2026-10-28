package com.nutriscan.app.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Full-text product search.
 *
 * Open Food Facts' v3 read API has no search endpoint (the feature table marks v3 structured search
 * as "not available"), and `/api/v2/search` on the world host answered HTTP 503 throughout testing.
 * The documented full-text service is Search-a-licious, which the Open Food Facts API introduction
 * points at for text search: `https://search.openfoodfacts.org/search`.
 */
interface SearchApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page_size") pageSize: Int = DEFAULT_PAGE_SIZE,
        @Query("page") page: Int = 1
    ): Response<SalResponse>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        /** Search is rate limited to 10 requests/minute per IP, so very short queries are refused. */
        const val MIN_QUERY_LENGTH = 2
    }
}

data class SalResponse(
    @SerializedName("count") val count: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
    @SerializedName("hits") val hits: List<SalHit>? = null
)

data class SalHit(
    @SerializedName("code") val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    /** An array in the search service, but tolerated as a plain string as well. */
    @SerializedName("brands") val brands: JsonElement? = null,
    @SerializedName("image_small_url") val imageSmallUrl: String? = null,
    @SerializedName("quantity") val quantity: String? = null
)
