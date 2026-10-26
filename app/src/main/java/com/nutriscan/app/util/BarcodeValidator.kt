package com.nutriscan.app.util

/**
 * Barcodes accepted by the app: EAN-8, UPC-E (8), UPC-A (12), EAN-13 (13) and ITF-14 (14).
 * The check digit is intentionally not enforced - Open Food Facts stores some codes whose
 * check digit is wrong, and rejecting them would hide a product that does exist.
 */
object BarcodeValidator {

    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 14

    fun normalize(raw: String): String = raw.filter { it.isDigit() }

    fun isValid(raw: String): Boolean {
        val code = normalize(raw)
        return code.length in MIN_LENGTH..MAX_LENGTH
    }
}
