package com.nutriscan.app.model

/**
 * Data typed by the user for a product that no source knows about.
 *
 * Kept in its own store, never uploaded anywhere, and merged into the product screen only as a
 * *fallback* for fields the external source does not provide.
 */
data class UserProductDraft(
    val barcode: String,
    val name: String? = null,
    val brand: String? = null,
    val quantity: String? = null,
    val servingSize: String? = null,
    val ingredients: String? = null,
    val allergens: List<String> = emptyList(),
    val nutrients: List<Nutrient> = emptyList(),
    val basis: NutritionBasis = NutritionBasis.PER_100_G,
    /** True when the user confirmed the values were copied from the package label. */
    val readFromLabel: Boolean = true,
    val photoPath: String? = null,
    val updatedAt: Long = 0L
) {
    val hasContent: Boolean
        get() = !name.isNullOrBlank() ||
            !brand.isNullOrBlank() ||
            !ingredients.isNullOrBlank() ||
            nutrients.isNotEmpty() ||
            allergens.isNotEmpty()

    fun toProduct(): Product {
        val provenance = buildMap {
            put(ProductField.IDENTIFICATION, entry())
            if (!name.isNullOrBlank()) put(ProductField.NAME, entry())
            if (!brand.isNullOrBlank()) put(ProductField.BRAND, entry())
            if (!quantity.isNullOrBlank()) put(ProductField.QUANTITY, entry())
            if (!servingSize.isNullOrBlank()) put(ProductField.SERVING_SIZE, entry())
            if (!ingredients.isNullOrBlank()) put(ProductField.INGREDIENTS, entry())
            if (allergens.isNotEmpty()) put(ProductField.ALLERGENS, entry())
            if (nutrients.isNotEmpty()) put(ProductField.NUTRITION, entry())
            if (photoPath != null) put(ProductField.IMAGE, entry())
        }
        return Product(
            barcode = barcode,
            productType = ProductType.FOOD,
            name = name?.ifBlank { null },
            nameEn = name?.ifBlank { null },
            nameBn = null,
            genericName = null,
            brand = brand?.ifBlank { null },
            categories = emptyList(),
            labels = emptyList(),
            quantity = quantity?.ifBlank { null },
            servingSize = servingSize?.ifBlank { null },
            ingredients = ingredients?.ifBlank { null },
            ingredientsEn = ingredients?.ifBlank { null },
            ingredientsBn = null,
            ingredientCount = null,
            allergens = allergens,
            traces = emptyList(),
            imageUrl = null,
            imageFrontUrl = null,
            nutritionBasis = if (nutrients.isEmpty()) NutritionBasis.UNKNOWN else basis,
            nutrients = nutrients,
            nutritionGrade = null,
            nutritionScore = null,
            novaGroup = null,
            lastModifiedEpochSeconds = null,
            offCompleteness = null,
            states = emptySet(),
            source = ProductSource.USER_ENTERED,
            provenance = provenance,
            userPhotoPath = photoPath
        )
    }

    private fun entry() = FieldProvenance(
        source = ProductSource.USER_ENTERED,
        timestamp = updatedAt.takeIf { it > 0 },
        readFromLabel = readFromLabel
    )
}
