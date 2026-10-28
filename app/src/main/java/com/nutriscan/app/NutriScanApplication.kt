package com.nutriscan.app

import android.app.Application
import android.content.Context
import com.nutriscan.app.network.NetworkModule
import com.nutriscan.app.repository.OpenFoodFactsRepository
import com.nutriscan.app.repository.ProductCache
import com.nutriscan.app.repository.ProductLookupRepository
import com.nutriscan.app.repository.RecentScansRepository
import com.nutriscan.app.repository.UserProductRepository
import java.io.File

/**
 * Manual dependency container. A DI framework (Hilt) is deliberately not used in this milestone;
 * the graph is three repositories deep and this keeps the build simple.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext
    private val okHttpClient = NetworkModule.createOkHttpClient()
    private val gson = NetworkModule.createGson()

    val productCache: ProductCache = ProductCache(
        file = File(applicationContext.filesDir, ProductCache.FILE_NAME),
        gson = gson
    )

    val productRepository: ProductLookupRepository = OpenFoodFactsRepository(
        api = NetworkModule.createApi(okHttpClient),
        searchApi = NetworkModule.createSearchApi(okHttpClient),
        cache = productCache
    )

    val userProductRepository: UserProductRepository = UserProductRepository(applicationContext, gson)

    val recentScansRepository: RecentScansRepository = RecentScansRepository(applicationContext)
}

class NutriScanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
