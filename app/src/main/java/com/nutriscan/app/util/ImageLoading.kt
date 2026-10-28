package com.nutriscan.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes a camera photo for display without loading the full resolution into memory.
 * Returns null when the file is missing or unreadable.
 */
fun decodeSampledBitmap(path: String, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > targetWidthPx * 2) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}
