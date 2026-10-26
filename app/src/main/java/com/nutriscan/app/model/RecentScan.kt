package com.nutriscan.app.model

/** A lightweight record kept on device so the user can reopen a product without rescanning. */
data class RecentScan(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val imageUrl: String?,
    val scannedAt: Long
)
