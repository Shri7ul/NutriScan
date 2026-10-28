package com.nutriscan.app.repository

import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductField
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.model.UserProductDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMergerTest {

    private fun external(
        barcode: String = "012345000065",
        name: String? = null,
        ingredients: String? = null,
        nutrients: List<Nutrient> = emptyList()
    ) = Product(
        barcode = barcode,
        productType = ProductType.FOOD,
        name = name,
        nameEn = name,
        nameBn = null,
        genericName = null,
        brand = "External Brand",
        categories = emptyList(),
        labels = emptyList(),
        quantity = null,
        servingSize = null,
        ingredients = ingredients,
        ingredientsEn = ingredients,
        ingredientsBn = null,
        ingredientCount = null,
        allergens = emptyList(),
        traces = emptyList(),
        imageUrl = null,
        imageFrontUrl = null,
        nutritionBasis = if (nutrients.isEmpty()) NutritionBasis.UNKNOWN else NutritionBasis.PER_100_G,
        nutrients = nutrients,
        nutritionGrade = null,
        nutritionScore = null,
        novaGroup = null,
        lastModifiedEpochSeconds = 1_750_000_000L,
        offCompleteness = null,
        states = emptySet(),
        source = ProductSource.OPEN_FOOD_FACTS
    )

    private fun draft(
        barcode: String = "012345000065",
        name: String? = "My product",
        ingredients: String? = null,
        nutrients: List<Nutrient> = emptyList()
    ) = UserProductDraft(
        barcode = barcode,
        name = name,
        ingredients = ingredients,
        nutrients = nutrients,
        updatedAt = 1_760_000_000_000
    )

    @Test
    fun userValuesFillFieldsTheSourceDoesNotHave() {
        val result = ProductMerger.merge(external(), draft())

        assertEquals("My product", result.product.name)
        assertEquals(ProductSource.USER_ENTERED, result.product.provenance[ProductField.NAME]?.source)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun externalValueWinsAndTheConflictIsReported() {
        val result = ProductMerger.merge(external(name = "Database name"), draft(name = "My name"))

        assertEquals("Database name", result.product.name)
        // The field is not re-attributed to the user when the database already had a value.
        assertNotEquals(ProductSource.USER_ENTERED, result.product.provenance[ProductField.NAME]?.source)
        assertEquals(listOf(ProductField.NAME), result.conflicts)
    }

    @Test
    fun aDifferentBarcodeIsNeverMerged() {
        val result = ProductMerger.merge(external(name = "Database name"), draft(barcode = "999999999999"))

        assertEquals("Database name", result.product.name)
        assertNull(result.product.provenance[ProductField.NAME])
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun nutrientsAreOnlyAddedWhereMissing() {
        val result = ProductMerger.merge(
            external(nutrients = listOf(Nutrient(NutrientKind.FAT, 10.0, "g"))),
            draft(
                nutrients = listOf(
                    Nutrient(NutrientKind.FAT, 12.0, "g"),
                    Nutrient(NutrientKind.PROTEIN, 5.0, "g")
                )
            )
        )

        val fat = result.product.nutrients.first { it.kind == NutrientKind.FAT }
        val protein = result.product.nutrients.first { it.kind == NutrientKind.PROTEIN }
        assertEquals(10.0, fat.amount, 0.0001)
        assertEquals(5.0, protein.amount, 0.0001)
        assertTrue(result.conflicts.contains(ProductField.NUTRITION))
    }

    @Test
    fun emptyDraftChangesNothing() {
        val source = external(name = "Database name")
        val result = ProductMerger.merge(source, null)
        assertEquals(source, result.product)
        assertFalse(result.conflicts.isNotEmpty())
    }

    @Test
    fun draftOnlyProductIsMarkedAsUserEntered() {
        val product = draft(name = "Mine", ingredients = "Oats").toProduct()
        assertEquals(ProductSource.USER_ENTERED, product.source)
        assertEquals(ProductSource.USER_ENTERED, product.provenance[ProductField.INGREDIENTS]?.source)
        assertEquals(ProductType.FOOD, product.productType)
    }
}
