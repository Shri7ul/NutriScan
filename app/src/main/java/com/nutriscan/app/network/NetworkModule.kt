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

    /**
     * Open Food Facts asks every client to identify itself; anonymous traffic is treated as a bot
     * and rate limited harder. No API key is required for read access.
     */
    const val USER_AGENT = "NutriScan/1.0 (Android; com.nutriscan.app)"

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 30L

    fun createGson(): Gson = GsonBuilder()
        .registerTypeAdapterFactory(FlexibleDoubleAdapterFactory)
        .create()

    fun createOkHttpClient(): OkHttpClient = okHttpBuilder().build()

    /** Exposed so tests can keep the production interceptors while shortening timeouts. */
    fun okHttpBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(ClientHeaderInterceptor())

    fun createApi(
        client: OkHttpClient = createOkHttpClient(),
        baseUrl: String = BASE_URL
    ): OffApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(createGson()))
        .build()
        .create(OffApi::class.java)

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
