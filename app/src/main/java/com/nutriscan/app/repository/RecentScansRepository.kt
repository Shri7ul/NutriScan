package com.nutriscan.app.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.RecentScan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the last few products on device so they can be reopened without a network round trip.
 * SharedPreferences is enough for a handful of records and adds no dependency.
 */
class RecentScansRepository(
    context: Context,
    private val gson: Gson = Gson()
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _scans = MutableStateFlow(readStoredScans())
    val scans: StateFlow<List<RecentScan>> = _scans.asStateFlow()

    fun record(product: Product, languageTag: String) {
        val entry = RecentScan(
            barcode = product.barcode,
            name = product.localizedName(languageTag) ?: product.nameEn,
            brand = product.brand,
            imageUrl = product.imageUrl,
            scannedAt = System.currentTimeMillis()
        )
        synchronized(this) {
            val updated = buildList {
                add(entry)
                addAll(_scans.value.filterNot { it.barcode == entry.barcode })
            }.take(MAX_ENTRIES)
            persist(updated)
            _scans.value = updated
        }
    }

    fun clear() {
        synchronized(this) {
            persist(emptyList())
            _scans.value = emptyList()
        }
    }

    private fun persist(scans: List<RecentScan>) {
        preferences.edit { putString(KEY_SCANS, gson.toJson(scans)) }
    }

    private fun readStoredScans(): List<RecentScan> {
        val raw = preferences.getString(KEY_SCANS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<RecentScan>>(raw, SCANS_TYPE).orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "nutriscan_recent_scans"
        const val KEY_SCANS = "scans"
        const val MAX_ENTRIES = 12

        val SCANS_TYPE = object : TypeToken<List<RecentScan>>() {}.type
    }
}
