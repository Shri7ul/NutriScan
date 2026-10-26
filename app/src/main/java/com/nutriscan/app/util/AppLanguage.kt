package com.nutriscan.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language selection backed by the platform API on Android 13+ and by AppCompat below it.
 * Changing the language recreates the activity, so every string resource is re-resolved.
 */
object AppLanguage {

    const val ENGLISH = "en"
    const val BANGLA = "bn"

    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) {
            Locale.getDefault().toLanguageTag()
        } else {
            locales.toLanguageTags()
        }
        return if (tag.lowercase(Locale.ROOT).startsWith(BANGLA)) BANGLA else ENGLISH
    }

    fun setLanguage(languageTag: String) {
        if (currentTag() == languageTag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
