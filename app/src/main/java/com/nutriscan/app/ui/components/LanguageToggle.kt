package com.nutriscan.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nutriscan.app.R
import com.nutriscan.app.util.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageToggle(
    currentTag: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
        AppLanguage.BANGLA to stringResource(R.string.language_bangla)
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = currentTag == tag,
                onClick = { onLanguageSelected(tag) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
