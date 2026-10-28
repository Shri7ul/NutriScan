package com.nutriscan.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Package photos taken during manual recovery. Stored inside the app's private files directory and
 * shared with the camera app through a FileProvider URI, so nothing is written to shared storage
 * and no storage permission is required.
 */
object PackagePhotoStore {

    private const val DIRECTORY = "package_photos"

    fun newPhotoFile(context: Context, barcode: String): File {
        val dir = File(context.applicationContext.filesDir, DIRECTORY)
        dir.mkdirs()
        val safeCode = barcode.filter { it.isDigit() }.ifEmpty { "unknown" }
        return File(dir, "${safeCode}_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
