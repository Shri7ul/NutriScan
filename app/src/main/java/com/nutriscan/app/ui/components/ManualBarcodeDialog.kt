package com.nutriscan.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutriscan.app.R
import com.nutriscan.app.util.BarcodeValidator

private const val MAX_BARCODE_LENGTH = 14

/**
 * Manual entry validates the same way the scanner does: a code must be 8-14 digits with a correct
 * check digit for its length, so a typo is reported instead of being looked up.
 */
@Composable
fun ManualBarcodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf("") }
    var rejection by rememberSaveable { mutableStateOf<BarcodeValidator.Rejection?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_entry_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        value = input.filter { it.isDigit() || it == ' ' || it == '-' }.take(MAX_BARCODE_LENGTH)
                        rejection = null
                    },
                    label = { Text(stringResource(R.string.manual_entry_hint)) },
                    singleLine = true,
                    isError = rejection != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                val current = rejection
                if (current != null) {
                    Text(
                        text = barcodeRejectionMessage(current),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = BarcodeValidator.inspect(value)) {
                        BarcodeValidator.Rejection.OK ->
                            BarcodeValidator.sanitize(value)?.let(onConfirm)

                        else -> rejection = result
                    }
                }
            ) {
                Text(stringResource(R.string.action_look_up))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
