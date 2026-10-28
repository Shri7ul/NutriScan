package com.nutriscan.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats an API amount without inventing precision: 385.0 -> "385", 7.69 -> "7.69", 1.5 -> "1.5". */
fun formatAmount(value: Double): String {
    if (!value.isFinite()) return ""
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

/**
 * Date only, in the device time zone. `java.time` needs core library desugaring on minSdk 24, so
 * this stays on SimpleDateFormat.
 */
fun formatDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("d MMM yyyy", locale).format(Date(epochMillis))

fun formatDateFromSeconds(epochSeconds: Long, locale: Locale = Locale.getDefault()): String =
    formatDate(epochSeconds * 1000, locale)

/** Whole days between [epochMillis] and now, never negative. */
fun daysSince(epochMillis: Long, now: Long = System.currentTimeMillis()): Long {
    val diff = now - epochMillis
    return if (diff <= 0) 0 else diff / (24L * 60 * 60 * 1000)
}
