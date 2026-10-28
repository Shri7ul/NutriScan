package com.nutriscan.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Open Food Facts read API (v3). Read access needs no credentials, only a descriptive User-Agent. */
interface OffApi {

    /**
     * [productType] defaults to `all`, which tells the server to look across every Open Food Facts
     * family database (food, beauty, pet food, products). Without it a barcode that exists only in
     * another family answers 200 with `result.id = product_found_with_a_different_product_type`,
     * which used to be shown as "product not found".
     */
    @GET("api/v3/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("product_type") productType: String = PRODUCT_TYPE_ALL
    ): Response<OffProductResponse>

    companion object {
        const val PRODUCT_TYPE_ALL = "all"
    }
}
