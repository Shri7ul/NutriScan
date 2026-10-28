package com.nutriscan.app.repository

import com.google.gson.Gson
import com.nutriscan.app.model.Product
import java.io.File

/**
 * On-device product cache.
 *
 * Room is not part of the project, and adding it for a handful of records would mean a compiler
 * plugin and a schema for one table. A single JSON file written atomically gives the same
 * offline behaviour with no new dependency.
 *
 * Two kinds of entry are stored:
 *  - successful lookups, with the fetch time so the UI can mark stale data;
 *  - negative entries, bounded by [negativeTtlMillis] so a product added to Open Food Facts is
 *    picked up again automatically and a manual retry is never blocked for long.
 */
class ProductCache(
    private val file: File,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val negativeTtlMillis: Long = DEFAULT_NEGATIVE_TTL_MILLIS
) {

    data class Entry(val product: Product, val fetchedAt: Long)

    /**
     * Serialised shape. Fields are nullable because Gson instantiates without calling the
     * constructor, so a truncated or older file yields nulls instead of defaults.
     */
    private class State(
        var version: Int = 0,
        var products: MutableMap<String, Entry>? = null,
        var missing: MutableMap<String, Long>? = null
    )

    private var state: State? = null

    @Synchronized
    fun read(barcode: String): Entry? = products()[barcode]

    @Synchronized
    fun write(product: Product, fetchedAt: Long = clock()) {
        products()[product.barcode] = Entry(product, fetchedAt)
        missing().remove(product.barcode)
        prune()
        persist()
    }

    @Synchronized
    fun markMissing(barcode: String) {
        missing()[barcode] = clock()
        prune()
        persist()
    }

    @Synchronized
    fun isKnownMissing(barcode: String): Boolean {
        val checkedAt = missing()[barcode] ?: return false
        val expired = clock() - checkedAt > negativeTtlMillis
        if (expired) {
            missing().remove(barcode)
            persist()
        }
        return !expired
    }

    @Synchronized
    fun forget(barcode: String) {
        val removedProduct = products().remove(barcode) != null
        val removedMissing = missing().remove(barcode) != null
        if (removedProduct || removedMissing) persist()
    }

    @Synchronized
    fun clear() {
        state = State(STORAGE_VERSION, mutableMapOf(), mutableMapOf())
        persist()
    }

    @Synchronized
    fun productCount(): Int = products().size

    private fun prune() {
        val products = products()
        if (products.size > maxEntries) {
            val keep = products.entries
                .sortedByDescending { it.value.fetchedAt }
                .take(maxEntries)
                .associate { it.key to it.value }
            products.clear()
            products.putAll(keep)
        }
        val now = clock()
        missing().entries.removeAll { now - it.value > negativeTtlMillis }
    }

    private fun products(): MutableMap<String, Entry> {
        val current = load()
        return current.products ?: mutableMapOf<String, Entry>().also { current.products = it }
    }

    private fun missing(): MutableMap<String, Long> {
        val current = load()
        return current.missing ?: mutableMapOf<String, Long>().also { current.missing = it }
    }

    private fun load(): State {
        state?.let { return it }
        val loaded = if (file.exists()) {
            runCatching { gson.fromJson(file.readText(), State::class.java) }.getOrNull()
        } else {
            null
        }
        // A cache written by an older schema is discarded rather than half-deserialised.
        val safe = loaded?.takeIf { it.version == STORAGE_VERSION } ?: State(STORAGE_VERSION)
        state = safe
        return safe
    }

    private fun persist() {
        val current = state ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(gson.toJson(current))
            // renameTo is available on every supported API level; on Windows it refuses to
            // overwrite, so the destination is removed first.
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    companion object {
        const val STORAGE_VERSION = 2
        const val DEFAULT_MAX_ENTRIES = 150
        const val DEFAULT_NEGATIVE_TTL_MILLIS = 10 * 60 * 1000L
        const val FILE_NAME = "product_cache.json"
    }
}
