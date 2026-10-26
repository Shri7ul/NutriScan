package com.nutriscan.app

import android.app.Application
import android.content.Context
import com.nutriscan.app.network.NetworkModule
import com.nutriscan.app.repository.ProductRepository
import com.nutriscan.app.repository.RecentScansRepository

/**
 * Manual dependency container. A DI framework (Hilt) is deliberately not used in this milestone;
 * the graph is two repositories deep and this keeps the build simple.
 */
class AppContainer(context: Context) {

    private val okHttpClient = NetworkModule.createOkHttpClient()

    val productRepository: ProductRepository =
        ProductRepository(NetworkModule.createApi(okHttpClient))

    val recentScansRepository: RecentScansRepository = RecentScansRepository(context)
}

class NutriScanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
