package com.nutriscan.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.ui.components.LoadingState
import com.nutriscan.app.ui.components.MessageState
import com.nutriscan.app.ui.components.RemoteProductImage
import com.nutriscan.app.ui.rememberAppContainer
import com.nutriscan.app.ui.theme.MonospaceStyle
import com.nutriscan.app.util.AppLanguage
import com.nutriscan.app.util.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    barcode: String,
    onBack: () -> Unit,
    onRescan: () -> Unit
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
                onRetry = { viewModel.load(forceRefresh = true) },
                onRescan = onRescan,
                modifier = contentModifier
            )

            is DetailsUiState.Success -> ProductContent(
                product = current.product,
                languageTag = languageTag,
                onRescan = onRescan,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: ProductError,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notFound = error is ProductError.NotFound

    val title = when (error) {
        is ProductError.NotFound -> stringResource(R.string.error_not_found_title)
        ProductError.Offline -> stringResource(R.string.error_offline_title)
        ProductError.Timeout -> stringResource(R.string.error_timeout_title)
        ProductError.RateLimited -> stringResource(R.string.error_rate_limited_title)
        is ProductError.Server -> stringResource(R.string.error_server_title)
        ProductError.MalformedResponse -> stringResource(R.string.error_malformed_title)
        ProductError.Unknown -> stringResource(R.string.error_unknown_title)
    }

    val body = when (error) {
        is ProductError.NotFound -> stringResource(R.string.error_not_found_body, error.barcode)
        ProductError.Offline -> stringResource(R.string.error_offline_body)
        ProductError.Timeout -> stringResource(R.string.error_timeout_body)
        ProductError.RateLimited -> stringResource(R.string.error_rate_limited_body)
        is ProductError.Server -> stringResource(R.string.error_server_body, error.statusCode)
        ProductError.MalformedResponse -> stringResource(R.string.error_malformed_body)
        ProductError.Unknown -> stringResource(R.string.error_unknown_body)
    }

    MessageState(
        title = title,
        body = body,
        isError = true,
        modifier = modifier,
        primaryLabel = if (notFound) stringResource(R.string.action_rescan) else stringResource(R.string.action_retry),
        onPrimary = if (notFound) onRescan else onRetry,
        secondaryLabel = if (notFound) null else stringResource(R.string.action_rescan),
        onSecondary = if (notFound) null else onRescan
    )
}

@Composable
private fun ProductContent(
    product: Product,
    languageTag: String,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ProductHeader(product = product, languageTag = languageTag) }
        item { ProductFactsCard(product) }
        item { NutritionCard(product) }
        item { IngredientsCard(product = product, languageTag = languageTag) }
        item { AllergensCard(product) }
        item {
            Text(
                text = stringResource(R.string.details_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
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
        }
    }
}

@Composable
private fun ProductHeader(product: Product, languageTag: String) {
    Column {
        RemoteProductImage(
            url = product.imageUrl,
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
    }
}

@Composable
private fun ProductFactsCard(product: Product) {
    SectionCard {
        InfoRow(stringResource(R.string.details_barcode), product.barcode, monospace = true)
        RowDivider()
        InfoRow(stringResource(R.string.details_brand), product.brand)
        RowDivider()
        InfoRow(stringResource(R.string.details_categories), product.categories)
        RowDivider()
        InfoRow(stringResource(R.string.details_serving_size), product.servingSize)
        RowDivider()
        InfoRow(stringResource(R.string.details_quantity), product.quantity)
    }
}

@Composable
private fun NutritionCard(product: Product) {
    SectionCard(title = stringResource(R.string.details_nutrition)) {
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
        Text(
            text = nutrientLabel(nutrient.kind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
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
            Text(
                text = ingredients,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergensCard(product: Product) {
    SectionCard(title = stringResource(R.string.details_allergens)) {
        if (product.allergens.isEmpty()) {
            Text(
                text = stringResource(R.string.details_no_allergens),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                product.allergens.forEach { allergen ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = allergen,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun InfoRow(label: String, value: String?, monospace: Boolean = false) {
    Row(
        modifier = Modifier
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
        Text(
            text = value ?: stringResource(R.string.details_missing_value),
            style = if (monospace && value != null) {
                MonospaceStyle.copy(fontSize = 14.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun basisLabel(basis: NutritionBasis): String = when (basis) {
    NutritionBasis.PER_100_G -> stringResource(R.string.nutrition_per_100g)
    NutritionBasis.PER_100_ML -> stringResource(R.string.nutrition_per_100ml)
    NutritionBasis.UNKNOWN -> ""
}

@Composable
private fun nutrientLabel(kind: NutrientKind): String = stringResource(
    when (kind) {
        NutrientKind.ENERGY -> R.string.nutrient_energy
        NutrientKind.FAT -> R.string.nutrient_fat
        NutrientKind.SATURATED_FAT -> R.string.nutrient_saturated_fat
        NutrientKind.CARBOHYDRATES -> R.string.nutrient_carbohydrates
        NutrientKind.SUGARS -> R.string.nutrient_sugars
        NutrientKind.PROTEIN -> R.string.nutrient_protein
        NutrientKind.SALT -> R.string.nutrient_salt
        NutrientKind.FIBER -> R.string.nutrient_fiber
        NutrientKind.SODIUM -> R.string.nutrient_sodium
    }
)
