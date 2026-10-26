package com.nutriscan.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Open Food Facts read API (v3). Read access needs no credentials, only a descriptive User-Agent. */
interface OffApi {

    @GET("api/v3/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<OffProductResponse>
}
