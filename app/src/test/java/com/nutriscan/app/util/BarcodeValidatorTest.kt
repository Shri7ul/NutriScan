package com.nutriscan.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {

    @Test
    fun `accepts the supported barcode lengths`() {
        assertTrue(BarcodeValidator.isValid("12345678"))            // EAN-8 / UPC-E
        assertTrue(BarcodeValidator.isValid("123456789012"))        // UPC-A
        assertTrue(BarcodeValidator.isValid("737628064502"))        // 12 digits
        assertTrue(BarcodeValidator.isValid("0737628064502"))       // EAN-13
        assertTrue(BarcodeValidator.isValid("12345678901234"))      // ITF-14
    }

    @Test
    fun `rejects too short, too long and non numeric input`() {
        assertFalse(BarcodeValidator.isValid("1234567"))
        assertFalse(BarcodeValidator.isValid("123456789012345"))
        assertFalse(BarcodeValidator.isValid(""))
        assertFalse(BarcodeValidator.isValid("1234abcd"))
    }

    @Test
    fun `normalize strips separators and whitespace`() {
        assertEquals("737628064502", BarcodeValidator.normalize(" 7376-2806-4502 "))
        assertEquals("737628064502", BarcodeValidator.normalize("7376 2806 4502"))
    }

    @Test
    fun `formatAmount drops meaningless decimals`() {
        assertEquals("385", formatAmount(385.0))
        assertEquals("7.69", formatAmount(7.69))
        assertEquals("1.5", formatAmount(1.5))
        assertEquals("0", formatAmount(0.0))
    }
}
