package com.nutriscan.app.repository

import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutrientOrigin
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.ProductField
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.network.NetworkModule
import com.nutriscan.app.network.OffProductResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are synthetic records shaped like the documented v3 schema; they do not describe real
 * products.
 */
class OffProductMapperTest {

    private val gson = NetworkModule.createGson()

    private fun map(fixture: String) =
        OffProductMapper.map("012345000065", load(fixture).product!!)

    private fun load(fixture: String): OffProductResponse {
        val text = javaClass.classLoader!!.getResourceAsStream(fixture)!!.bufferedReader().readText()
        return gson.fromJson(text, OffProductResponse::class.java)
    }

    private fun List<Nutrient>.of(kind: NutrientKind): Nutrient =
        first { it.kind == kind }

    private fun List<Nutrient>.amountOrNull(kind: NutrientKind): Double? =
        firstOrNull { it.kind == kind }?.amount

    @Test
    fun mapsEveryFieldOfACompleteRecord() {
        val product = map("product_complete.json")

        assertEquals(ProductType.FOOD, product.productType)
        assertEquals("Sunrise Oat Biscuits", product.name)
        assertEquals("Sunrise Foods, Example Mills", product.brand)
        assertEquals("Oat biscuits", product.genericName)
        assertEquals(listOf("Snacks", "Biscuits"), product.categories)
        assertEquals(listOf("Vegetarian"), product.labels)
        assertEquals("250 g", product.quantity)
        assertEquals("2 biscuits (25 g)", product.servingSize)
        assertEquals("Wholegrain oats, sugar, palm oil, salt", product.ingredientsEn)
        assertEquals(4, product.ingredientCount)
        assertEquals(listOf("Gluten"), product.allergens)
        assertEquals(listOf("Milk"), product.traces)
        assertEquals("https://example.invalid/images/front.jpg", product.imageFrontUrl)
        assertEquals(NutritionBasis.PER_100_G, product.nutritionBasis)
        assertEquals("c", product.nutritionGrade)
        assertEquals(12, product.nutritionScore)
        assertEquals(3, product.novaGroup)
        assertEquals(1750000000L, product.lastModifiedEpochSeconds)
        assertEquals(0.85, product.offCompleteness!!, 0.0001)
        assertEquals(ProductSource.OPEN_FOOD_FACTS, product.source)
        assertTrue(product.states.contains("nutrition-facts-completed"))
        assertTrue(product.provenance.containsKey(ProductField.NUTRITION))
    }

    @Test
    fun parsesNumericStringsWithoutFailingTheWholeRecord() {
        val sugars = map("product_complete.json").nutrients.of(NutrientKind.SUGARS)
        assertEquals(21.4, sugars.amount, 0.0001)
        assertEquals(NutrientOrigin.REPORTED, sugars.origin)
    }

    @Test
    fun keepsTotalAndAddedSugarsApart() {
        val product = map("product_complete.json")
        assertEquals(21.4, product.nutrients.of(NutrientKind.SUGARS).amount, 0.0001)
        assertEquals(18.0, product.nutrients.of(NutrientKind.ADDED_SUGARS).amount, 0.0001)
    }

    @Test
    fun derivesSodiumFromSaltAndFlagsIt() {
        val sodium = map("product_complete.json").nutrients.of(NutrientKind.SODIUM)
        assertEquals(0.32, sodium.amount, 0.0001)
        assertEquals(NutrientOrigin.DERIVED, sodium.origin)
    }

    @Test
    fun derivesSaltFromSodiumAndFlagsIt() {
        val product = map("product_nutrition_only.json")
        val salt = product.nutrients.of(NutrientKind.SALT)
        assertEquals(1.0, salt.amount, 0.0001)
        assertEquals(NutrientOrigin.DERIVED, salt.origin)
        assertEquals(NutrientOrigin.REPORTED, product.nutrients.of(NutrientKind.SODIUM).origin)
    }

    @Test
    fun derivesKcalFromKjAndFlagsIt() {
        val product = map("product_nutrition_only.json")
        val kcal = product.nutrients.of(NutrientKind.ENERGY_KCAL)
        assertEquals(1600 / 4.184, kcal.amount, 0.01)
        assertEquals(NutrientOrigin.DERIVED, kcal.origin)
        assertEquals(1600.0, product.nutrients.of(NutrientKind.ENERGY_KJ).amount, 0.001)
    }

    @Test
    fun missingSectionsStayEmptyRatherThanZero() {
        val product = map("product_name_only.json")
        assertTrue(product.nutrients.isEmpty())
        assertFalse(product.hasNutrition)
        assertNull(product.ingredients)
        assertNull(product.servingSize)
        assertNull(product.displayImageUrl)
        assertTrue(product.allergens.isEmpty())
        assertNull(product.nutrients.amountOrNull(NutrientKind.FAT))
    }

    @Test
    fun ingredientsWithoutNutritionStillMap() {
        val product = map("product_ingredients_only.json")
        assertEquals("Brown rice, sea salt", product.ingredientsEn)
        assertTrue(product.nutrients.isEmpty())
        assertEquals(NutritionBasis.UNKNOWN, product.nutritionBasis)
    }

    @Test
    fun nutritionWithoutIngredientsStillMaps() {
        val product = map("product_nutrition_only.json")
        assertNull(product.ingredients)
        assertTrue(product.hasNutrition)
        assertTrue(product.allergens.isEmpty())
    }

    @Test
    fun foodRecordWithoutAComputableGradeReportsNoGrade() {
        // Open Food Facts answers "unknown" when it cannot score the category (observed live on a
        // cocoa spread). "unknown" must never reach the UI as if it were a Nutri-Score letter, and
        // the NOVA group must survive on its own.
        val product = map("product_unknown_grade.json")
        assertEquals(ProductType.FOOD, product.productType)
        assertNull(product.nutritionGrade)
        assertEquals(3, product.novaGroup)
        assertTrue(product.hasNutrition)
    }

    @Test
    fun placeholderTagsAreNotShownAsCategoriesOrLabels() {
        // Observed live: categories_tags carries ["en:null"] because a contributor typed the word
        // into the field. That is a missing value, not a category named "Null".
        val product = map("product_sentinel_tags.json")
        assertTrue(product.categories.isEmpty())
        assertEquals(listOf("Vegetarian"), product.labels)
        assertTrue(product.traces.isEmpty())
    }

    @Test
    fun nonFoodRecordIsTypedAndHasNoGrade() {
        val product = map("product_beauty.json")
        assertEquals(ProductType.BEAUTY, product.productType)
        assertFalse(product.isFood)
        // The API answers "unknown" for beauty records; that is not a Nutri-Score letter.
        assertNull(product.nutritionGrade)
        assertTrue(product.nutrients.isEmpty())
    }

    @Test
    fun emptyTracesTextIsReplacedByTags() {
        val product = map("product_complete.json")
        assertEquals(listOf("Milk"), product.traces)
    }

    @Test
    fun responseEnvelopeIsReadable() {
        val response = load("product_not_found.json")
        assertEquals("failure", response.status)
        assertEquals("product_not_found", response.result?.id)
        assertNull(response.product)
        assertNotNull(response.errors)
    }
}
