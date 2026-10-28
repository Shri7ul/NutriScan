package com.nutriscan.app.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutriscan.app.model.UserProductDraft
import java.io.File

/**
 * Local store for products the user typed in themselves.
 *
 * Deliberately separate from the Open Food Facts cache: these records are never sent anywhere, and
 * they are only ever used to fill fields that the external source does not provide.
 */
class UserProductRepository(
    context: Context,
    private val gson: Gson = Gson()
) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private var entries: MutableMap<String, UserProductDraft>? = null

    @Synchronized
    fun find(barcode: String): UserProductDraft? = load()[barcode]

    @Synchronized
    fun all(): List<UserProductDraft> = load().values.sortedByDescending { it.updatedAt }

    @Synchronized
    fun save(draft: UserProductDraft) {
        load()[draft.barcode] = draft
        persist()
    }

    @Synchronized
    fun delete(barcode: String) {
        if (load().remove(barcode) != null) persist()
    }

    private fun load(): MutableMap<String, UserProductDraft> {
        entries?.let { return it }
        val loaded = if (file.exists()) {
            runCatching {
                gson.fromJson<Map<String, UserProductDraft?>>(file.readText(), TYPE)
            }.getOrNull()
        } else {
            null
        }
        val safe = loaded
            ?.mapNotNull { (code, draft) -> draft?.let { code to it } }
            ?.toMap()
            ?.toMutableMap()
            ?: mutableMapOf()
        entries = safe
        return safe
    }

    private fun persist() {
        runCatching {
            val current = entries ?: return
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(gson.toJson(current))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "user_products.json"
        val TYPE = object : TypeToken<Map<String, UserProductDraft?>>() {}.type
    }
}
