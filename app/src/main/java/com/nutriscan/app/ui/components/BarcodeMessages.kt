package com.nutriscan.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutriscan.app.R
import com.nutriscan.app.util.BarcodeValidator

/** One place for the "why was this rejected" wording, shared by the dialog and the scanner. */
@Composable
fun barcodeRejectionMessage(rejection: BarcodeValidator.Rejection): String = stringResource(
    when (rejection) {
        BarcodeValidator.Rejection.EMPTY -> R.string.error_barcode_empty
        BarcodeValidator.Rejection.NON_NUMERIC -> R.string.error_barcode_non_numeric
        BarcodeValidator.Rejection.TOO_SHORT -> R.string.error_barcode_too_short
        BarcodeValidator.Rejection.TOO_LONG -> R.string.error_barcode_too_long
        BarcodeValidator.Rejection.UNSUPPORTED_LENGTH -> R.string.error_barcode_unsupported_length
        BarcodeValidator.Rejection.BAD_CHECK_DIGIT -> R.string.error_barcode_bad_check_digit
        BarcodeValidator.Rejection.OK -> R.string.manual_entry_invalid
    }
)
