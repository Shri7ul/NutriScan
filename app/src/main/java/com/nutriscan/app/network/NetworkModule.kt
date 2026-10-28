package com.nutriscan.app.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val BASE_URL = "https://world.openfoodfacts.org/"
    const val SEARCH_BASE_URL = "https://search.openfoodfacts.org/"

    /**
     * Open Food Facts asks every client to identify itself; anonymous traffic is treated as a bot
     * and rate limited harder. No API key is required for read access.
     */
    const val USER_AGENT = "NutriScan/2.0 (Android; com.nutriscan.app)"

    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val READ_TIMEOUT_SECONDS = 12L
    private const val CALL_TIMEOUT_SECONDS = 15L

    fun createGson(): Gson = GsonBuilder()
        .registerTypeAdapterFactory(FlexibleDoubleAdapterFactory)
        .create()

    fun createOkHttpClient(): OkHttpClient = okHttpBuilder().build()

    /** Exposed so tests can keep the production interceptors while shortening timeouts. */
    fun okHttpBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Redirects are expected: `product_type=all` answers 302 to the family host that owns
        // the barcode (openbeautyfacts.org, openpetfoodfacts.org, ...).
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor(ClientHeaderInterceptor())

    fun createApi(
        client: OkHttpClient = createOkHttpClient(),
        baseUrl: String = BASE_URL
    ): OffApi = retrofit(baseUrl, client).create(OffApi::class.java)

    fun createSearchApi(
        client: OkHttpClient = createOkHttpClient(),
        baseUrl: String = SEARCH_BASE_URL
    ): SearchApi = retrofit(baseUrl, client).create(SearchApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(createGson()))
        .build()

    private class ClientHeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            return chain.proceed(request)
        }
    }
}
