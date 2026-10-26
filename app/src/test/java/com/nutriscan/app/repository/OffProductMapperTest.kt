package com.nutriscan.app.repository

import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.network.NetworkModule
import com.nutriscan.app.network.OffProductResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing is verified against a payload captured from the live v3 endpoint
 * (GET https://world.openfoodfacts.org/api/v3/product/737628064502.json).
 */
class OffProductMapperTest {

    private val gson = NetworkModule.createGson()

    @Test
    fun `parses a real open food facts v3 payload`() {
        val response = gson.fromJson(fixture("off_product_success.json"), OffProductResponse::class.java)
        val dto = requireNotNull(response.product)

        // The live API answered with status "success_with_warnings", not plain "success".
        assertEquals("success_with_warnings", response.status)
        assertEquals("0737628064502", response.code)

        val product = OffProductMapper.map("737628064502", dto)

        assertEquals("737628064502", product.barcode)
        assertTrue(product.name!!.startsWith("Thai peanut noodle kit"))
        assertEquals("Simply Asia, Thai Kitchen", product.brand)
        assertEquals("Cereals and their products, Noodles, Rice Noodles", product.categories)
        assertEquals("0.333 PACKAGE (52 g)", product.servingSize)
        assertEquals("155 g", product.quantity)
        assertTrue(product.imageUrl!!.startsWith("https://images.openfoodfacts.org/"))
        assertTrue(product.ingredients!!.startsWith("Rice Noodles"))

        assertEquals(listOf("Peanuts", "Sesame seeds", "Soybeans"), product.allergens)
        assertEquals(NutritionBasis.PER_100_G, product.nutritionBasis)
    }

    @Test
    fun `maps nutrition values per 100 g exactly as returned`() {
        val product = product(fixture("off_product_success.json"))

        fun amount(kind: NutrientKind) = product.nutrients.first { it.kind == kind }.amount

        assertEquals(385.0, amount(NutrientKind.ENERGY), 0.001)
        assertEquals(7.69, amount(NutrientKind.FAT), 0.001)
        assertEquals(1.92, amount(NutrientKind.SATURATED_FAT), 0.001)
        assertEquals(71.15, amount(NutrientKind.CARBOHYDRATES), 0.001)
        assertEquals(13.46, amount(NutrientKind.SUGARS), 0.001)
        assertEquals(9.62, amount(NutrientKind.PROTEIN), 0.001)
        assertEquals(0.72, amount(NutrientKind.SALT), 0.001)
        assertEquals(1.9, amount(NutrientKind.FIBER), 0.001)
        assertEquals("kcal", product.nutrients.first { it.kind == NutrientKind.ENERGY }.unit)
        assertEquals("g", product.nutrients.first { it.kind == NutrientKind.FAT }.unit)
    }

    @Test
    fun `accepts numeric strings and comma decimals from community data`() {
        val json = """
            {
              "status": "success",
              "product": {
                "code": "1234567890123",
                "product_name": "Test product",
                "nutriments": {
                  "energy-kcal_100g": "250",
                  "fat_100g": "3,5",
                  "proteins_100g": ""
                }
              }
            }
        """.trimIndent()

        val product = product(json)

        assertEquals(250.0, product.nutrients.first { it.kind == NutrientKind.ENERGY }.amount, 0.001)
        assertEquals(3.5, product.nutrients.first { it.kind == NutrientKind.FAT }.amount, 0.001)
        assertTrue(product.nutrients.none { it.kind == NutrientKind.PROTEIN })
    }

    @Test
    fun `keeps missing data missing instead of inventing values`() {
        val json = """
            {"status": "success", "product": {"code": "1234567890123", "product_name": "Bare product"}}
        """.trimIndent()

        val product = product(json)

        assertEquals("Bare product", product.name)
        assertNull(product.brand)
        assertNull(product.ingredients)
        assertNull(product.imageUrl)
        assertNull(product.servingSize)
        assertTrue(product.allergens.isEmpty())
        assertTrue(product.nutrients.isEmpty())
        assertEquals(NutritionBasis.UNKNOWN, product.nutritionBasis)
    }

    @Test
    fun `falls back to the allergens text when tags are absent`() {
        val json = """
            {
              "status": "success",
              "product": {
                "code": "1234567890123",
                "product_name": "Fallback product",
                "allergens": "peanuts, soybeans"
              }
            }
        """.trimIndent()

        assertEquals(listOf("peanuts", "soybeans"), product(json).allergens)
    }

    @Test
    fun `labels 100 ml products correctly`() {
        val json = """
            {
              "status": "success",
              "product": {
                "code": "1234567890123",
                "product_name": "Drink",
                "nutrition_data_per": "100ml",
                "nutriments": {"energy-kcal_100g": 42}
              }
            }
        """.trimIndent()

        assertEquals(NutritionBasis.PER_100_ML, product(json).nutritionBasis)
    }

    private fun product(json: String): com.nutriscan.app.model.Product {
        val response = gson.fromJson(json, OffProductResponse::class.java)
        return OffProductMapper.map("1234567890123", requireNotNull(response.product))
    }

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing test fixture $name"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
