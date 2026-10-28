package com.nutriscan.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {

    @Test
    fun keepsLeadingZeroes() {
        // A real Open Food Facts code: turning this into a number would destroy it.
        assertEquals("0009800800049", BarcodeValidator.sanitize("0009800800049"))
        assertEquals("0009800800049", BarcodeValidator.sanitize("  0009800800049  "))
    }

    @Test
    fun acceptsGroupingSeparatorsFromManualEntry() {
        assertEquals("012345000065", BarcodeValidator.sanitize("0 12345 00006 5"))
        assertEquals("012345000065", BarcodeValidator.sanitize("0-12345-00006-5"))
    }

    @Test
    fun rejectsAnythingThatIsNotDigits() {
        assertNull(BarcodeValidator.sanitize("https://example.com/p/012345000065"))
        assertNull(BarcodeValidator.sanitize("ABCDEFGH"))
        assertNull(BarcodeValidator.sanitize(""))
        assertNull(BarcodeValidator.sanitize("01234X000065"))
    }

    @Test
    fun rejectsCodesOutsideSupportedLengths() {
        assertNull(BarcodeValidator.sanitize("1234567"))
        assertNull(BarcodeValidator.sanitize("123456789012345"))
    }

    @Test
    fun validatesMod10CheckDigit() {
        assertTrue(BarcodeValidator.hasValidCheckDigit("012345000065"))
        assertTrue(BarcodeValidator.hasValidCheckDigit("0009800800049"))
        assertFalse(BarcodeValidator.hasValidCheckDigit("012345000066"))
        assertFalse(BarcodeValidator.hasValidCheckDigit("12345"))
    }

    @Test
    fun expandsUpcEToUpcA() {
        assertEquals("012345000065", BarcodeValidator.expandUpcE("01234565"))
    }

    @Test
    fun refusesUpcEExpansionWithBrokenCheckDigit() {
        assertNull(BarcodeValidator.expandUpcE("01234566"))
        assertNull(BarcodeValidator.expandUpcE("21234565"))
        assertNull(BarcodeValidator.expandUpcE("0123456"))
    }

    @Test
    fun triesUpcAThenItsEan13Equivalent() {
        assertEquals(
            listOf("012345000065", "0012345000065"),
            BarcodeValidator.lookupVariants("012345000065")
        )
    }

    @Test
    fun triesEan13ThenItsUpcAEquivalent() {
        assertEquals(
            listOf("0012345000065", "012345000065"),
            BarcodeValidator.lookupVariants("0012345000065")
        )
    }

    @Test
    fun triesBothReadingsOfAnEightDigitCode() {
        assertEquals(
            listOf("01234565", "012345000065", "0012345000065"),
            BarcodeValidator.lookupVariants("01234565")
        )
    }

    @Test
    fun doesNotInventVariantsForOrdinaryEan13() {
        assertEquals(listOf("3017620422003"), BarcodeValidator.lookupVariants("3017620422003"))
    }

    @Test
    fun reportsWhyAValueWasRejected() {
        assertEquals(BarcodeValidator.Rejection.OK, BarcodeValidator.inspect("012345000065"))
        assertEquals(BarcodeValidator.Rejection.EMPTY, BarcodeValidator.inspect("  "))
        assertEquals(BarcodeValidator.Rejection.NON_NUMERIC, BarcodeValidator.inspect("abc"))
        assertEquals(BarcodeValidator.Rejection.TOO_SHORT, BarcodeValidator.inspect("1234567"))
        assertEquals(BarcodeValidator.Rejection.TOO_LONG, BarcodeValidator.inspect("123456789012345"))
        assertEquals(BarcodeValidator.Rejection.UNSUPPORTED_LENGTH, BarcodeValidator.inspect("123456789"))
        assertEquals(BarcodeValidator.Rejection.BAD_CHECK_DIGIT, BarcodeValidator.inspect("012345000066"))
    }
}
