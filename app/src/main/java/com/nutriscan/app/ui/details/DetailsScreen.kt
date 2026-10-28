package com.nutriscan.app.ui.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutriscan.app.R
import com.nutriscan.app.model.BarcodeProblem
import com.nutriscan.app.model.FieldState
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutrientOrigin
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.model.CompletenessGroup
import com.nutriscan.app.ui.components.CompletenessCard
import com.nutriscan.app.ui.components.FieldStatusChip
import com.nutriscan.app.ui.components.InfoRow
import com.nutriscan.app.ui.components.LoadingState
import com.nutriscan.app.ui.components.MessageState
import com.nutriscan.app.ui.components.RemoteProductImage
import com.nutriscan.app.ui.components.RowDivider
import com.nutriscan.app.ui.components.SectionCard
import com.nutriscan.app.ui.components.TagFlow
import com.nutriscan.app.ui.rememberAppContainer
import com.nutriscan.app.util.AppLanguage
import com.nutriscan.app.util.decodeSampledBitmap
import com.nutriscan.app.util.formatAmount
import com.nutriscan.app.util.formatDate
import com.nutriscan.app.util.formatDateFromSeconds
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    barcode: String,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onSearch: () -> Unit,
    onAddInfo: (String) -> Unit
) {
    val container = rememberAppContainer()
    val languageTag = AppLanguage.currentTag()

    val viewModel: DetailsViewModel = viewModel(
        key = "details-$barcode-$languageTag",
        factory = viewModelFactory {
            initializer {
                DetailsViewModel(
                    barcode = barcode,
                    productRepository = container.productRepository,
                    userProductRepository = container.userProductRepository,
                    recentScansRepository = container.recentScansRepository,
                    languageTag = languageTag
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (val current = state) {
            DetailsUiState.Loading -> LoadingState(modifier = contentModifier)

            is DetailsUiState.Error -> ErrorContent(
                error = current.error,
                barcode = current.barcode,
                onRetry = { viewModel.load(forceRefresh = true) },
                onRescan = onRescan,
                onSearch = onSearch,
                onAddInfo = { onAddInfo(current.barcode) },
                modifier = contentModifier
            )

            is DetailsUiState.NotFound -> NotFoundContent(
                barcode = current.barcode,
                onRetry = { viewModel.load(forceRefresh = true) },
                onRescan = onRescan,
                onSearch = onSearch,
                onAddInfo = { onAddInfo(current.barcode) },
                modifier = contentModifier
            )

            is DetailsUiState.Content -> ProductContent(
                state = current,
                languageTag = languageTag,
                onRescan = onRescan,
                onSearch = onSearch,
                onEditEntry = { onAddInfo(current.product.barcode) },
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun NotFoundContent(
    barcode: String,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onSearch: () -> Unit,
    onAddInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MessageState(
            title = stringResource(R.string.error_not_found_title),
            body = stringResource(R.string.error_not_found_body, barcode),
            isError = true,
            modifier = Modifier.weight(1f),
            primaryLabel = stringResource(R.string.action_add_product),
            onPrimary = onAddInfo,
            secondaryLabel = stringResource(R.string.action_search_product),
            onSecondary = onSearch
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_retry))
            }
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_rescan))
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: ProductError,
    barcode: String,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onSearch: () -> Unit,
    onAddInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (error) {
        is ProductError.InvalidBarcode -> stringResource(R.string.error_invalid_barcode_title)
        ProductError.Offline -> stringResource(R.string.error_offline_title)
        ProductError.Timeout -> stringResource(R.string.error_timeout_title)
        ProductError.RateLimited -> stringResource(R.string.error_rate_limited_title)
        is ProductError.Server -> stringResource(R.string.error_server_title)
        ProductError.MalformedResponse -> stringResource(R.string.error_malformed_title)
        ProductError.Unknown -> stringResource(R.string.error_unknown_title)
    }

    val body = when (error) {
        is ProductError.InvalidBarcode -> invalidBarcodeBody(error.reason)
        ProductError.Offline -> stringResource(R.string.error_offline_body)
        ProductError.Timeout -> stringResource(R.string.error_timeout_body)
        ProductError.RateLimited -> stringResource(R.string.error_rate_limited_body)
        is ProductError.Server -> stringResource(R.string.error_server_body, error.statusCode)
        ProductError.MalformedResponse -> stringResource(R.string.error_malformed_body)
        ProductError.Unknown -> stringResource(R.string.error_unknown_body)
    }

    val retryable = error !is ProductError.InvalidBarcode

    MessageState(
        title = title,
        body = body,
        isError = true,
        modifier = modifier,
        primaryLabel = if (retryable) stringResource(R.string.action_retry) else stringResource(R.string.action_rescan),
        onPrimary = if (retryable) onRetry else onRescan,
        secondaryLabel = when {
            !retryable -> null
            error == ProductError.Offline -> stringResource(R.string.action_add_product)
            else -> stringResource(R.string.action_search_product)
        },
        onSecondary = when {
            !retryable -> null
            error == ProductError.Offline -> onAddInfo
            else -> onSearch
        }
    )
}

@Composable
private fun invalidBarcodeBody(problem: BarcodeProblem): String = stringResource(
    when (problem) {
        BarcodeProblem.EMPTY -> R.string.error_barcode_empty
        BarcodeProblem.NON_NUMERIC -> R.string.error_barcode_non_numeric
        BarcodeProblem.TOO_SHORT -> R.string.error_barcode_too_short
        BarcodeProblem.TOO_LONG -> R.string.error_barcode_too_long
        BarcodeProblem.UNSUPPORTED_LENGTH -> R.string.error_barcode_unsupported_length
        BarcodeProblem.BAD_CHECK_DIGIT -> R.string.error_barcode_bad_check_digit
    }
)

@Composable
private fun ProductContent(
    state: DetailsUiState.Content,
    languageTag: String,
    onRescan: () -> Unit,
    onSearch: () -> Unit,
    onEditEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val product = state.product
    val locale = remember(languageTag) { Locale.forLanguageTag(languageTag) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ProductHeader(product = product, languageTag = languageTag) }

        if (state.userEntryOnly) {
            // Nothing came from a database, so this is not a cached copy and must not be called one.
            item {
                NoticeBanner(
                    text = stringResource(R.string.details_user_entry_banner),
                    isWarning = false
                )
            }
        } else if (state.cache.stale) {
            item {
                val fetchedAt = state.cache.fetchedAt
                NoticeBanner(
                    text = if (fetchedAt != null) {
                        stringResource(R.string.details_cached_banner, formatDate(fetchedAt, locale))
                    } else {
                        stringResource(R.string.details_cached_banner_unknown_date)
                    },
                    isWarning = true
                )
            }
        }

        state.loadError?.let { error ->
            item {
                NoticeBanner(
                    text = when (error) {
                        ProductError.Offline -> stringResource(R.string.details_offline_banner)
                        ProductError.RateLimited -> stringResource(R.string.error_rate_limited_body)
                        is ProductError.Server -> stringResource(R.string.error_server_body, error.statusCode)
                        else -> stringResource(R.string.error_unknown_body)
                    },
                    isWarning = true
                )
            }
        }

        if (product.userPhotoPath != null) {
            item { UserPhoto(path = product.userPhotoPath) }
        }

        if (state.completeness.availableCount < state.completeness.applicableCount) {
            item {
                NoticeBanner(
                    text = stringResource(
                        if (state.userEntryOnly) {
                            R.string.details_user_entry_incomplete_banner
                        } else {
                            R.string.details_incomplete_banner
                        },
                        state.completeness.availableCount,
                        state.completeness.applicableCount
                    ),
                    isWarning = false
                )
            }
        }

        if (state.conflicts.isNotEmpty()) {
            item { NoticeBanner(text = stringResource(R.string.details_conflict_banner), isWarning = true) }
        }

        if (!product.isFood) {
            item {
                NoticeBanner(
                    text = stringResource(R.string.details_non_food, productTypeLabel(product.productType)),
                    isWarning = false
                )
            }
        }

        item { IdentityCard(product = product, completeness = state.completeness) }
        item { NutritionCard(product = product) }
        item { IngredientsCard(product = product, languageTag = languageTag) }
        item { AllergensCard(product = product, completeness = state.completeness) }
        item { CompletenessCard(completeness = state.completeness) }
        item { SourceCard(product = product, locale = locale) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRescan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_rescan))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onEditEntry, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (state.hasUserEntry) R.string.details_edit_entry else R.string.action_add_product
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_search_short),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(text: String, isWarning: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun ProductHeader(product: Product, languageTag: String) {
    Column {
        RemoteProductImage(
            url = product.displayImageUrl,
            contentDescription = stringResource(R.string.cd_product_image),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = product.localizedName(languageTag) ?: stringResource(R.string.details_no_name),
            style = MaterialTheme.typography.headlineSmall
        )
        if (!product.brand.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.brand,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!product.genericName.isNullOrBlank() && product.genericName != product.name) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.genericName,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = productTypeLabel(product.productType),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserPhoto(path: String) {
    val bitmap = remember(path) { decodeSampledBitmap(path, 720) }
    if (bitmap == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.details_your_photo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.details_your_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun IdentityCard(product: Product, completeness: com.nutriscan.app.model.ProductCompleteness) {
    SectionCard {
        InfoRow(stringResource(R.string.details_barcode), product.barcode, monospace = true)
        RowDivider()
        InfoRow(stringResource(R.string.details_brand), product.brand)
        RowDivider()
        InfoRow(stringResource(R.string.details_quantity), product.quantity)
        RowDivider()
        InfoRow(
            label = stringResource(R.string.details_serving_size),
            value = product.servingSize,
            status = completeness.statusOf(CompletenessGroup.SERVING_SIZE)
        )
        if (product.categories.isNotEmpty()) {
            RowDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.details_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TagFlow(product.categories)
            }
        }
        if (product.labels.isNotEmpty()) {
            RowDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.details_labels),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TagFlow(product.labels)
            }
        }
    }
}

@Composable
private fun NutritionCard(product: Product) {
    SectionCard(title = stringResource(R.string.details_nutrition)) {
        if (!product.isFood) {
            Text(
                text = stringResource(R.string.details_nutrition_not_applicable),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        val basis = basisLabel(product.nutritionBasis)
        if (basis.isNotEmpty()) {
            Text(
                text = basis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        if (product.hasNutrition) {
            product.nutrients.forEachIndexed { index, nutrient ->
                if (index > 0) RowDivider()
                NutrientRow(nutrient)
            }
        } else {
            Text(
                text = stringResource(R.string.details_no_nutrition),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val grade = product.nutritionGrade
        val nova = product.novaGroup
        // Open Food Facts answers "unknown" when it cannot compute a score for the category.
        // Nothing is shown as a grade then, so say so instead of leaving a silent gap.
        if (grade == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.details_nutriscore_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (grade != null || nova != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (grade != null) {
                    ScorePill(
                        label = stringResource(R.string.details_nutriscore),
                        value = grade.uppercase(Locale.ROOT)
                    )
                }
                if (nova != null) {
                    ScorePill(label = stringResource(R.string.details_nova), value = nova.toString())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.details_score_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScorePill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NutrientRow(nutrient: Nutrient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nutrientLabel(nutrient.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (nutrient.origin == NutrientOrigin.DERIVED) {
                Text(
                    text = stringResource(R.string.details_derived),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${formatAmount(nutrient.amount)} ${nutrient.unit}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IngredientsCard(product: Product, languageTag: String) {
    SectionCard(title = stringResource(R.string.details_ingredients)) {
        val ingredients = product.localizedIngredients(languageTag)
        if (ingredients == null) {
            Text(
                text = stringResource(R.string.details_no_ingredients),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(text = ingredients, style = MaterialTheme.typography.bodyMedium)
            product.ingredientCount?.let { count ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.details_ingredient_count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AllergensCard(product: Product, completeness: com.nutriscan.app.model.ProductCompleteness) {
    SectionCard(title = stringResource(R.string.details_allergens)) {
        if (!product.isFood) {
            Text(
                text = stringResource(R.string.details_allergens_not_applicable),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        if (product.allergens.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.details_no_allergens),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FieldStatusChip(status = completeness.statusOf(CompletenessGroup.ALLERGENS))
            }
        } else {
            TagFlow(product.allergens)
        }

        if (product.traces.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.details_traces),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TagFlow(product.traces)
        }
    }
}

@Composable
private fun SourceCard(product: Product, locale: Locale) {
    SectionCard(title = stringResource(R.string.details_source_title)) {
        InfoRow(
            label = stringResource(R.string.details_source_label),
            value = when (product.source) {
                com.nutriscan.app.model.ProductSource.OPEN_FOOD_FACTS -> stringResource(R.string.details_source_off)
                com.nutriscan.app.model.ProductSource.USER_ENTERED -> stringResource(R.string.details_source_user)
            }
        )
        product.lastModifiedEpochSeconds?.let { modified ->
            RowDivider()
            InfoRow(
                label = stringResource(R.string.details_last_modified),
                value = formatDateFromSeconds(modified, locale)
            )
        }
        product.offCompleteness?.let { value ->
            RowDivider()
            InfoRow(
                label = stringResource(R.string.details_off_completeness),
                value = "${Math.round(value * 100)}%"
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.details_source_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun basisLabel(basis: NutritionBasis): String = when (basis) {
    NutritionBasis.PER_100_G -> stringResource(R.string.nutrition_per_100g)
    NutritionBasis.PER_100_ML -> stringResource(R.string.nutrition_per_100ml)
    NutritionBasis.UNKNOWN -> ""
}

@Composable
private fun productTypeLabel(type: ProductType): String = stringResource(
    when (type) {
        ProductType.FOOD -> R.string.product_type_food
        ProductType.BEAUTY -> R.string.product_type_beauty
        ProductType.PET_FOOD -> R.string.product_type_pet_food
        ProductType.OTHER -> R.string.product_type_other
        ProductType.UNKNOWN -> R.string.product_type_unknown
    }
)

@Composable
private fun nutrientLabel(kind: NutrientKind): String = stringResource(
    when (kind) {
        NutrientKind.ENERGY_KCAL -> R.string.nutrient_energy
        NutrientKind.ENERGY_KJ -> R.string.nutrient_energy_kj
        NutrientKind.FAT -> R.string.nutrient_fat
        NutrientKind.SATURATED_FAT -> R.string.nutrient_saturated_fat
        NutrientKind.CARBOHYDRATES -> R.string.nutrient_carbohydrates
        NutrientKind.SUGARS -> R.string.nutrient_sugars
        NutrientKind.ADDED_SUGARS -> R.string.nutrient_added_sugars
        NutrientKind.PROTEIN -> R.string.nutrient_protein
        NutrientKind.SALT -> R.string.nutrient_salt
        NutrientKind.FIBER -> R.string.nutrient_fiber
        NutrientKind.SODIUM -> R.string.nutrient_sodium
    }
)
