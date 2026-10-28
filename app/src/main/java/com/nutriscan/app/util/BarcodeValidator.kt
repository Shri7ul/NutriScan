package com.nutriscan.app.util

/**
 * GTIN handling for the symbologies the app actually scans: EAN-13, EAN-8, UPC-A and UPC-E.
 *
 * Rules that matter for lookup success:
 *  - the barcode is always handled as a *string*; it is never parsed into a number, so leading
 *    zeroes survive (`0009800800049` is a real Open Food Facts code);
 *  - input that is not purely digits is rejected instead of being silently cleaned, because
 *    stripping characters from a QR payload would invent a barcode that was never printed;
 *  - the mod-10 check digit is verified for 8/12/13/14 digit codes;
 *  - a UPC-E code is expanded to its UPC-A form, and UPC-A/EAN-13 equivalents are both tried,
 *    because Open Food Facts stores whichever form a contributor typed.
 */
object BarcodeValidator {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 14

    private val SUPPORTED_LENGTHS = setOf(8, 12, 13, 14)

    enum class Symbology(val digits: Int) {
        EAN_8(8),
        UPC_E(8),
        UPC_A(12),
        EAN_13(13),
        GTIN_14(14),
        UNSUPPORTED(0)
    }

    /**
     * Returns the digits of [raw] when it is a plausible barcode, or null when it is not.
     * Spaces and hyphens are accepted as grouping separators (manual entry); every other
     * character is rejected.
     */
    fun sanitize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val digits = StringBuilder(trimmed.length)
        for (char in trimmed) {
            when {
                char.isDigit() -> digits.append(char)
                char == ' ' || char == '-' || char == '\u00A0' -> Unit
                else -> return null
            }
        }
        val code = digits.toString()
        return code.takeIf { it.length in MIN_LENGTH..MAX_LENGTH }
    }

    fun isSupportedLength(code: String): Boolean = code.length in SUPPORTED_LENGTHS

    /**
     * Which symbology [code] most likely came from. An 8 digit code is ambiguous between EAN-8 and
     * UPC-E; [Symbology.EAN_8] is reported because that is what ML Kit reports for the EAN-8 format,
     * and [lookupVariants] still tries the UPC-E interpretation afterwards.
     */
    fun symbology(code: String): Symbology = when (code.length) {
        8 -> Symbology.EAN_8
        12 -> Symbology.UPC_A
        13 -> Symbology.EAN_13
        14 -> Symbology.GTIN_14
        else -> Symbology.UNSUPPORTED
    }

    /** GS1 mod-10 check digit. Returns false for lengths that do not carry one. */
    fun hasValidCheckDigit(code: String): Boolean {
        if (!isSupportedLength(code)) return false
        var sum = 0
        val body = code.dropLast(1)
        body.reversed().forEachIndexed { index, char ->
            val digit = char - '0'
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        val expected = (10 - sum % 10) % 10
        return expected == code.last() - '0'
    }

    /**
     * Expands a UPC-E code (8 digits) to UPC-A (12 digits) using the GS1 zero-suppression rules.
     * Returns null when the input is not an 8 digit code or the expansion does not produce a
     * valid UPC-A check digit, which means the code was not really UPC-E.
     */
    fun expandUpcE(code: String): String? {
        if (code.length != 8) return null
        val numberSystem = code[0]
        if (numberSystem != '0' && numberSystem != '1') return null
        val d = code.substring(1, 7)
        val check = code[7]
        val expanded = when (d[5]) {
            in '0'..'2' -> "$numberSystem${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}$check"
            '3' -> "$numberSystem${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}$check"
            '4' -> "$numberSystem${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}$check"
            else -> "$numberSystem${d.substring(0, 5)}0000${d[5]}$check"
        }
        return expanded.takeIf { hasValidCheckDigit(it) }
    }

    /**
     * The codes to try, in order, for a single scanned value. Open Food Facts stores UPC-A and
     * EAN-13 forms of the same product independently, so both representations are queried.
     */
    fun lookupVariants(code: String): List<String> {
        val variants = LinkedHashSet<String>()
        variants += code
        when (code.length) {
            8 -> {
                expandUpcE(code)?.let { upcA ->
                    variants += upcA
                    variants += "0$upcA"
                }
            }

            12 -> variants += "0$code"
            13 -> if (code.startsWith('0')) variants += code.substring(1)
        }
        return variants.toList()
    }

    /** Why a scanned or typed value was rejected, so the UI can explain it precisely. */
    enum class Rejection {
        OK,
        EMPTY,
        NON_NUMERIC,
        TOO_SHORT,
        TOO_LONG,
        UNSUPPORTED_LENGTH,
        BAD_CHECK_DIGIT
    }

    fun inspect(raw: String): Rejection {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Rejection.EMPTY
        val digits = trimmed.filter { it.isDigit() }
        val separators = trimmed.count { it == ' ' || it == '-' }
        if (digits.length + separators != trimmed.length) return Rejection.NON_NUMERIC
        if (digits.length < MIN_LENGTH) return Rejection.TOO_SHORT
        if (digits.length > MAX_LENGTH) return Rejection.TOO_LONG
        if (!isSupportedLength(digits)) return Rejection.UNSUPPORTED_LENGTH
        return if (hasValidCheckDigit(digits)) Rejection.OK else Rejection.BAD_CHECK_DIGIT
    }
}
