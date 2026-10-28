package com.nutriscan.app.model

/**
 * Field-level completeness.
 *
 * The percentage is **not** a black box: it is the mean of eight named information groups, each
 * weighted equally (1/8). A group scores 1.0 when it is [FieldState.AVAILABLE] or
 * [FieldState.USER_ENTERED], 0.5 when it is [FieldState.UNVERIFIED] (data present but the source
 * itself flags it as incomplete, or the record is old), and 0.0 when it is [FieldState.MISSING].
 * [FieldState.NOT_APPLICABLE] groups are removed from the denominator, so a non-food product is not
 * punished for having no nutrition table.
 */
object ProductCompletenessEvaluator {

    /** A record whose last community edit is older than this is reported as possibly outdated. */
    private const val STALE_RECORD_SECONDS = 365L * 24 * 60 * 60

    private const val STATE_NUTRITION_COMPLETED = "nutrition-facts-completed"
    private const val STATE_INGREDIENTS_COMPLETED = "ingredients-completed"

    fun evaluate(product: Product, nowEpochMillis: Long = System.currentTimeMillis()): ProductCompleteness {
        val groups = CompletenessGroup.entries.map { group -> statusOf(group, product, nowEpochMillis) }

        val applicable = groups.filter { it.status.state != FieldState.NOT_APPLICABLE }
        val points = applicable.sumOf { it.status.state.weight }
        val score = if (applicable.isEmpty()) 0 else Math.round(100 * points / applicable.size).toInt()

        return ProductCompleteness(
            groups = groups,
            scorePercent = score,
            availableCount = groups.count {
                it.status.state == FieldState.AVAILABLE || it.status.state == FieldState.USER_ENTERED
            },
            applicableCount = applicable.size
        )
    }

    private fun statusOf(
        group: CompletenessGroup,
        product: Product,
        nowEpochMillis: Long
    ): CompletenessGroupStatus {
        val field = group.field
        val provenance = field?.let { product.provenance[it] }

        // Anything the user typed is reported as such, and never counted as source-verified.
        if (provenance?.source == ProductSource.USER_ENTERED) {
            val hasValue = group.hasValue(product)
            if (hasValue) {
                val note = if (provenance.readFromLabel == true) FieldNote.READ_FROM_LABEL else FieldNote.TYPED_BY_USER
                return CompletenessGroupStatus(group, FieldStatus(FieldState.USER_ENTERED, note))
            }
        }

        val status = when (group) {
            CompletenessGroup.IDENTIFICATION ->
                if (product.barcode.length in 8..14) FieldStatus(FieldState.AVAILABLE) else FieldStatus(FieldState.MISSING)

            CompletenessGroup.NAME_AND_BRAND ->
                if (!product.name.isNullOrBlank()) FieldStatus(FieldState.AVAILABLE) else FieldStatus(FieldState.MISSING)

            CompletenessGroup.INGREDIENTS -> when {
                !product.isFood -> FieldStatus(FieldState.NOT_APPLICABLE)
                product.hasIngredients && STATE_INGREDIENTS_COMPLETED in product.states -> FieldStatus(FieldState.AVAILABLE)
                product.hasIngredients -> FieldStatus(FieldState.UNVERIFIED, FieldNote.SOURCE_INCOMPLETE)
                else -> FieldStatus(FieldState.MISSING)
            }

            CompletenessGroup.NUTRITION -> when {
                !product.isFood -> FieldStatus(FieldState.NOT_APPLICABLE)
                product.hasNutrition && STATE_NUTRITION_COMPLETED in product.states -> FieldStatus(FieldState.AVAILABLE)
                product.hasNutrition -> FieldStatus(FieldState.UNVERIFIED, FieldNote.SOURCE_INCOMPLETE)
                else -> FieldStatus(FieldState.MISSING)
            }

            CompletenessGroup.ALLERGENS -> when {
                !product.isFood -> FieldStatus(FieldState.NOT_APPLICABLE)
                product.allergens.isNotEmpty() -> FieldStatus(FieldState.AVAILABLE)
                product.traces.isNotEmpty() -> FieldStatus(FieldState.UNVERIFIED, FieldNote.ONLY_TRACES)
                else -> FieldStatus(FieldState.MISSING)
            }

            CompletenessGroup.IMAGE ->
                if (product.displayImageUrl != null) FieldStatus(FieldState.AVAILABLE) else FieldStatus(FieldState.MISSING)

            CompletenessGroup.SERVING_SIZE ->
                if (!product.servingSize.isNullOrBlank()) FieldStatus(FieldState.AVAILABLE) else FieldStatus(FieldState.MISSING)

            CompletenessGroup.DATA_FRESHNESS -> {
                val modified = product.lastModifiedEpochSeconds
                when {
                    modified == null -> FieldStatus(FieldState.MISSING)
                    nowEpochMillis / 1000 - modified > STALE_RECORD_SECONDS ->
                        FieldStatus(FieldState.UNVERIFIED, FieldNote.RECORD_OUTDATED)

                    else -> FieldStatus(FieldState.AVAILABLE)
                }
            }
        }
        return CompletenessGroupStatus(group, status)
    }

    private fun CompletenessGroup.hasValue(product: Product): Boolean = when (this) {
        CompletenessGroup.IDENTIFICATION -> product.barcode.isNotBlank()
        CompletenessGroup.NAME_AND_BRAND -> !product.name.isNullOrBlank() || !product.brand.isNullOrBlank()
        CompletenessGroup.INGREDIENTS -> product.hasIngredients
        CompletenessGroup.NUTRITION -> product.hasNutrition
        CompletenessGroup.ALLERGENS -> product.allergens.isNotEmpty()
        CompletenessGroup.IMAGE -> product.userPhotoPath != null || product.displayImageUrl != null
        CompletenessGroup.SERVING_SIZE -> !product.servingSize.isNullOrBlank()
        CompletenessGroup.DATA_FRESHNESS -> product.lastModifiedEpochSeconds != null
    }
}

enum class CompletenessGroup(val field: ProductField?) {
    IDENTIFICATION(ProductField.IDENTIFICATION),
    NAME_AND_BRAND(ProductField.NAME),
    INGREDIENTS(ProductField.INGREDIENTS),
    NUTRITION(ProductField.NUTRITION),
    ALLERGENS(ProductField.ALLERGENS),
    IMAGE(ProductField.IMAGE),
    SERVING_SIZE(ProductField.SERVING_SIZE),
    DATA_FRESHNESS(null)
}

enum class FieldState(val weight: Double) {
    AVAILABLE(1.0),
    USER_ENTERED(1.0),
    UNVERIFIED(0.5),
    MISSING(0.0),
    NOT_APPLICABLE(0.0)
}

/** Machine-readable qualifier so the UI can localise the explanation. */
enum class FieldNote {
    READ_FROM_LABEL,
    TYPED_BY_USER,
    SOURCE_INCOMPLETE,
    ONLY_TRACES,
    RECORD_OUTDATED
}

data class FieldStatus(val state: FieldState, val note: FieldNote? = null)

data class CompletenessGroupStatus(val group: CompletenessGroup, val status: FieldStatus)

data class ProductCompleteness(
    val groups: List<CompletenessGroupStatus>,
    val scorePercent: Int,
    val availableCount: Int,
    val applicableCount: Int
) {
    val isComplete: Boolean get() = availableCount == applicableCount

    fun statusOf(group: CompletenessGroup): FieldStatus =
        groups.first { it.group == group }.status
}
