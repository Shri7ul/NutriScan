package com.nutriscan.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCompletenessTest {

    private val modifiedSeconds = 1_750_000_000L
    private val justAfter = (modifiedSeconds + 10L * 24 * 60 * 60) * 1000

    private fun product(
        productType: ProductType = ProductType.FOOD,
        name: String? = "Test product",
        ingredients: String? = null,
        nutrients: List<Nutrient> = emptyList(),
        allergens: List<String> = emptyList(),
        traces: List<String> = emptyList(),
        image: String? = null,
        servingSize: String? = null,
        lastModified: Long? = modifiedSeconds,
        states: Set<String> = emptySet(),
        provenance: Map<ProductField, FieldProvenance> = emptyMap()
    ) = Product(
        barcode = "012345000065",
        productType = productType,
        name = name,
        nameEn = name,
        nameBn = null,
        genericName = null,
        brand = null,
        categories = emptyList(),
        labels = emptyList(),
        quantity = null,
        servingSize = servingSize,
        ingredients = ingredients,
        ingredientsEn = ingredients,
        ingredientsBn = null,
        ingredientCount = null,
        allergens = allergens,
        traces = traces,
        imageUrl = image,
        imageFrontUrl = image,
        nutritionBasis = if (nutrients.isEmpty()) NutritionBasis.UNKNOWN else NutritionBasis.PER_100_G,
        nutrients = nutrients,
        nutritionGrade = null,
        nutritionScore = null,
        novaGroup = null,
        lastModifiedEpochSeconds = lastModified,
        offCompleteness = null,
        states = states,
        source = ProductSource.OPEN_FOOD_FACTS,
        provenance = provenance
    )

    @Test
    fun completeRecordScoresHundred() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(
                ingredients = "Oats, sugar",
                nutrients = listOf(Nutrient(NutrientKind.FAT, 10.0, "g")),
                allergens = listOf("Gluten"),
                image = "https://example.invalid/a.jpg",
                servingSize = "25 g",
                states = setOf("ingredients-completed", "nutrition-facts-completed")
            ),
            justAfter
        )

        assertEquals(100, completeness.scorePercent)
        assertTrue(completeness.isComplete)
        assertEquals(8, completeness.availableCount)
        assertEquals(FieldState.AVAILABLE, completeness.statusOf(CompletenessGroup.NUTRITION).state)
    }

    @Test
    fun missingNutritionIsMissingNotZero() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(
                ingredients = "Oats",
                allergens = listOf("Gluten"),
                image = "https://example.invalid/a.jpg",
                servingSize = "25 g",
                states = setOf("ingredients-completed")
            ),
            justAfter
        )

        assertEquals(FieldState.MISSING, completeness.statusOf(CompletenessGroup.NUTRITION).state)
        assertEquals(7, completeness.availableCount)
        assertEquals(88, completeness.scorePercent)
    }

    @Test
    fun nutritionPresentButUnflaggedIsUnverified() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(nutrients = listOf(Nutrient(NutrientKind.FAT, 3.0, "g"))),
            justAfter
        )

        val status = completeness.statusOf(CompletenessGroup.NUTRITION)
        assertEquals(FieldState.UNVERIFIED, status.state)
        assertEquals(FieldNote.SOURCE_INCOMPLETE, status.note)
    }

    @Test
    fun nonFoodGroupsAreNotApplicableAndLeaveTheDenominator() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(productType = ProductType.BEAUTY, name = "Conditioner"),
            justAfter
        )

        assertEquals(FieldState.NOT_APPLICABLE, completeness.statusOf(CompletenessGroup.NUTRITION).state)
        assertEquals(FieldState.NOT_APPLICABLE, completeness.statusOf(CompletenessGroup.INGREDIENTS).state)
        assertEquals(FieldState.NOT_APPLICABLE, completeness.statusOf(CompletenessGroup.ALLERGENS).state)
        // Identification, name, image, serving size and freshness remain applicable.
        assertEquals(5, completeness.applicableCount)
        assertEquals(3, completeness.availableCount)
        assertEquals(60, completeness.scorePercent)
    }

    @Test
    fun oldRecordIsReportedAsUnverified() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(
                ingredients = "Oats",
                nutrients = listOf(Nutrient(NutrientKind.FAT, 10.0, "g")),
                allergens = listOf("Gluten"),
                image = "https://example.invalid/a.jpg",
                servingSize = "25 g",
                states = setOf("ingredients-completed", "nutrition-facts-completed")
            ),
            (modifiedSeconds + 400L * 24 * 60 * 60) * 1000
        )

        val status = completeness.statusOf(CompletenessGroup.DATA_FRESHNESS)
        assertEquals(FieldState.UNVERIFIED, status.state)
        assertEquals(FieldNote.RECORD_OUTDATED, status.note)
        assertEquals(94, completeness.scorePercent)
    }

    @Test
    fun userEnteredValuesAreLabelledAndCounted() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(
                nutrients = listOf(Nutrient(NutrientKind.FAT, 4.0, "g")),
                provenance = mapOf(
                    ProductField.NUTRITION to FieldProvenance(
                        source = ProductSource.USER_ENTERED,
                        timestamp = 1_760_000_000_000,
                        readFromLabel = true
                    )
                )
            ),
            justAfter
        )

        val status = completeness.statusOf(CompletenessGroup.NUTRITION)
        assertEquals(FieldState.USER_ENTERED, status.state)
        assertEquals(FieldNote.READ_FROM_LABEL, status.note)
        // Identification, name, user nutrition and freshness are present; the rest are missing.
        assertEquals(4, completeness.availableCount)
        assertEquals(50, completeness.scorePercent)
    }

    @Test
    fun tracesWithoutAllergensAreUnverified() {
        val completeness = ProductCompletenessEvaluator.evaluate(
            product(traces = listOf("Milk")),
            justAfter
        )

        val status = completeness.statusOf(CompletenessGroup.ALLERGENS)
        assertEquals(FieldState.UNVERIFIED, status.state)
        assertEquals(FieldNote.ONLY_TRACES, status.note)
    }

    @Test
    fun missingLastModifiedIsMissingFreshness() {
        val completeness = ProductCompletenessEvaluator.evaluate(product(lastModified = null), justAfter)
        assertEquals(FieldState.MISSING, completeness.statusOf(CompletenessGroup.DATA_FRESHNESS).state)
        assertFalse(completeness.isComplete)
    }
}
