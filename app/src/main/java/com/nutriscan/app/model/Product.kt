package com.nutriscan.app.model

/**
 * A product as displayed by the app. Every field is nullable or empty when Open Food Facts
 * does not provide it; nothing is ever filled in with a guessed value.
 */
data class Product(
    val barcode: String,
    val name: String?,
    val nameEn: String?,
    val nameBn: String?,
    val brand: String?,
    val categories: String?,
    val imageUrl: String?,
    val ingredients: String?,
    val ingredientsEn: String?,
    val ingredientsBn: String?,
    val allergens: List<String>,
    val servingSize: String?,
    val quantity: String?,
    val nutritionBasis: NutritionBasis,
    val nutrients: List<Nutrient>
) {
    fun localizedName(languageTag: String): String? = when {
        languageTag.startsWith("bn") -> nameBn?.ifBlank { null } ?: name
        languageTag.startsWith("en") -> nameEn?.ifBlank { null } ?: name
        else -> name
    }

    fun localizedIngredients(languageTag: String): String? = when {
        languageTag.startsWith("bn") -> ingredientsBn?.ifBlank { null } ?: ingredients
        languageTag.startsWith("en") -> ingredientsEn?.ifBlank { null } ?: ingredients
        else -> ingredients
    }

    val hasNutrition: Boolean get() = nutrients.isNotEmpty()
}

enum class NutritionBasis { PER_100_G, PER_100_ML, UNKNOWN }

data class Nutrient(
    val kind: NutrientKind,
    val amount: Double,
    val unit: String
)

enum class NutrientKind { ENERGY, FAT, SATURATED_FAT, CARBOHYDRATES, SUGARS, PROTEIN, SALT, FIBER, SODIUM }
