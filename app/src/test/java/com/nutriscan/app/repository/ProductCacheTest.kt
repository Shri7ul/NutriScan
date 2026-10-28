package com.nutriscan.app.repository

import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProductCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 1_760_000_000_000L

    private fun newCache(file: File = File(temp.root, "cache.json")) = ProductCache(
        file = file,
        gson = NetworkModule.createGson(),
        clock = { now },
        maxEntries = 3,
        negativeTtlMillis = 10 * 60 * 1000
    )

    @Test
    fun survivesANewInstance() {
        val file = File(temp.root, "cache.json")
        newCache(file).write(product("012345000065"), fetchedAt = now)

        val reopened = newCache(file)
        val entry = reopened.read("012345000065")

        assertNotNull(entry)
        assertEquals("Product 012345000065", entry!!.product.name)
        assertEquals(now, entry.fetchedAt)
    }

    @Test
    fun negativeEntriesExpire() {
        val cache = newCache()
        cache.markMissing("012345000065")

        assertTrue(cache.isKnownMissing("012345000065"))

        now += 11 * 60 * 1000
        assertTrue(!cache.isKnownMissing("012345000065"))
    }

    @Test
    fun writingAProductClearsItsNegativeEntry() {
        val cache = newCache()
        cache.markMissing("012345000065")
        cache.write(product("012345000065"))

        assertTrue(!cache.isKnownMissing("012345000065"))
    }

    @Test
    fun oldestEntriesArePruned() {
        val cache = newCache()
        cache.write(product("111111111111"), fetchedAt = now)
        cache.write(product("222222222222"), fetchedAt = now + 1)
        cache.write(product("333333333333"), fetchedAt = now + 2)
        cache.write(product("444444444444"), fetchedAt = now + 3)

        assertEquals(3, cache.productCount())
        assertNull(cache.read("111111111111"))
        assertNotNull(cache.read("444444444444"))
    }

    @Test
    fun corruptFileIsIgnoredInsteadOfCrashing() {
        val file = File(temp.root, "cache.json")
        file.writeText("{ this is not json")

        val cache = newCache(file)
        assertNull(cache.read("012345000065"))
        cache.write(product("012345000065"))
        assertNotNull(cache.read("012345000065"))
    }

    @Test
    fun cacheFromAnOlderSchemaIsDiscarded() {
        val file = File(temp.root, "cache.json")
        file.writeText("""{"version":1,"products":{"012345000065":{"fetchedAt":1}}}""")

        assertNull(newCache(file).read("012345000065"))
    }

    private fun product(barcode: String) = Product(
        barcode = barcode,
        productType = ProductType.FOOD,
        name = "Product $barcode",
        nameEn = "Product $barcode",
        nameBn = null,
        genericName = null,
        brand = null,
        categories = emptyList(),
        labels = emptyList(),
        quantity = null,
        servingSize = null,
        ingredients = null,
        ingredientsEn = null,
        ingredientsBn = null,
        ingredientCount = null,
        allergens = emptyList(),
        traces = emptyList(),
        imageUrl = null,
        imageFrontUrl = null,
        nutritionBasis = NutritionBasis.PER_100_G,
        nutrients = listOf(Nutrient(NutrientKind.FAT, 1.0, "g")),
        nutritionGrade = null,
        nutritionScore = null,
        novaGroup = null,
        lastModifiedEpochSeconds = null,
        offCompleteness = null,
        states = emptySet(),
        source = ProductSource.OPEN_FOOD_FACTS
    )
}
