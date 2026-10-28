package com.nutriscan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nutriscan.app.R
import com.nutriscan.app.model.CompletenessGroup
import com.nutriscan.app.model.FieldNote
import com.nutriscan.app.model.FieldState
import com.nutriscan.app.model.FieldStatus
import com.nutriscan.app.model.ProductCompleteness

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** Label / value row with an optional per-field state chip. */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    status: FieldStatus? = null,
    monospace: Boolean = false,
    emptyLabel: String = stringResource(R.string.details_missing_value)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(116.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value ?: emptyLabel,
                style = if (monospace && value != null) {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal
            )
            if (status != null && status.state != FieldState.AVAILABLE) {
                Spacer(Modifier.height(4.dp))
                FieldStatusChip(status = status)
            }
        }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
fun FieldStatusChip(status: FieldStatus, modifier: Modifier = Modifier) {
    val colors = statusColors(status.state)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.first)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = stateLabel(status.state),
            style = MaterialTheme.typography.labelSmall,
            color = colors.second
        )
    }
}

@Composable
private fun statusColors(state: FieldState): Pair<Color, Color> = when (state) {
    FieldState.AVAILABLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    FieldState.USER_ENTERED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    FieldState.UNVERIFIED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    FieldState.MISSING -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    FieldState.NOT_APPLICABLE -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun stateLabel(state: FieldState): String = stringResource(
    when (state) {
        FieldState.AVAILABLE -> R.string.status_available
        FieldState.USER_ENTERED -> R.string.status_user_entered
        FieldState.UNVERIFIED -> R.string.status_unverified
        FieldState.MISSING -> R.string.status_missing
        FieldState.NOT_APPLICABLE -> R.string.status_not_applicable
    }
)

@Composable
fun noteLabel(note: FieldNote): String = stringResource(
    when (note) {
        FieldNote.READ_FROM_LABEL -> R.string.note_read_from_label
        FieldNote.TYPED_BY_USER -> R.string.note_typed_by_user
        FieldNote.SOURCE_INCOMPLETE -> R.string.note_source_incomplete
        FieldNote.ONLY_TRACES -> R.string.note_only_traces
        FieldNote.RECORD_OUTDATED -> R.string.note_record_outdated
    }
)

@Composable
fun completenessGroupLabel(group: CompletenessGroup): String = stringResource(
    when (group) {
        CompletenessGroup.IDENTIFICATION -> R.string.group_identification
        CompletenessGroup.NAME_AND_BRAND -> R.string.group_name_brand
        CompletenessGroup.INGREDIENTS -> R.string.group_ingredients
        CompletenessGroup.NUTRITION -> R.string.group_nutrition
        CompletenessGroup.ALLERGENS -> R.string.group_allergens
        CompletenessGroup.IMAGE -> R.string.group_image
        CompletenessGroup.SERVING_SIZE -> R.string.group_serving_size
        CompletenessGroup.DATA_FRESHNESS -> R.string.group_data_freshness
    }
)

/**
 * Shows the coverage figure together with the eight groups it is averaged over, so the number is
 * never a black box.
 */
@Composable
fun CompletenessCard(completeness: ProductCompleteness, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.completeness_title), modifier = modifier) {
        Text(
            text = stringResource(
                R.string.completeness_summary,
                completeness.scorePercent,
                completeness.availableCount,
                completeness.applicableCount
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { completeness.scorePercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(12.dp))

        completeness.groups.forEachIndexed { index, group ->
            if (index > 0) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = completenessGroupLabel(group.group),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FieldStatusChip(status = group.status)
            }
            group.status.note?.let { note ->
                Text(
                    text = noteLabel(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.completeness_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFlow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
