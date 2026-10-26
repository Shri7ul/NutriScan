package com.nutriscan.app.util

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
