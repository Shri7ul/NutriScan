package com.nutriscan.app.model

/**
 * A product as displayed by the app.
 *
 * Every optional field stays null / empty when no source provided it. Nothing is ever defaulted
 * to zero or to a guessed value, so the UI can tell "no data" apart from "value is zero".
 */
data class Product(
    val barcode: String,
    val productType: ProductType,
    val name: String?,
    val nameEn: String?,
    val nameBn: String?,
    val genericName: String?,
    val brand: String?,
    val categories: List<String>,
    val labels: List<String>,
    val quantity: String?,
    val servingSize: String?,
    val ingredients: String?,
    val ingredientsEn: String?,
    val ingredientsBn: String?,
    val ingredientCount: Int?,
    val allergens: List<String>,
    val traces: List<String>,
    val imageUrl: String?,
    val imageFrontUrl: String?,
    val nutritionBasis: NutritionBasis,
    val nutrients: List<Nutrient>,
    val nutritionGrade: String?,
    val nutritionScore: Int?,
    val novaGroup: Int?,
    val lastModifiedEpochSeconds: Long?,
    val offCompleteness: Double?,
    val states: Set<String>,
    val source: ProductSource,
    val provenance: Map<ProductField, FieldProvenance> = emptyMap(),
    val userPhotoPath: String? = null
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

    /** Front-of-package image is preferred, then the generic image. */
    val displayImageUrl: String? get() = imageFrontUrl?.ifBlank { null } ?: imageUrl?.ifBlank { null }

    val hasNutrition: Boolean get() = nutrients.isNotEmpty()

    val hasIngredients: Boolean get() = !localizedIngredients("en").isNullOrBlank()

    val hasAllergenInformation: Boolean get() = allergens.isNotEmpty() || traces.isNotEmpty()

    /** Nutrition and allergens only make sense for edible products. */
    val isFood: Boolean get() = productType == ProductType.FOOD || productType == ProductType.UNKNOWN
}

enum class ProductType {
    FOOD,
    BEAUTY,
    PET_FOOD,
    OTHER,
    UNKNOWN;

    companion object {
        /** Open Food Facts reports the family in `product_type` ("food", "beauty", "petfood", ...). */
        fun fromApi(value: String?): ProductType = when (value?.trim()?.lowercase()) {
            "food" -> FOOD
            "beauty" -> BEAUTY
            "petfood", "pet_food", "pet-food" -> PET_FOOD
            "product", "products" -> OTHER
            else -> UNKNOWN
        }
    }
}

enum class ProductSource {
    OPEN_FOOD_FACTS,
    USER_ENTERED;

    /** Higher wins when two sources disagree: a maintained database record beats a hand-typed one. */
    val trust: Int get() = when (this) {
        OPEN_FOOD_FACTS -> 2
        USER_ENTERED -> 1
    }
}

/** Fields the app can report provenance for. Used by the merge rules and the details screen. */
enum class ProductField {
    IDENTIFICATION,
    NAME,
    BRAND,
    INGREDIENTS,
    NUTRITION,
    ALLERGENS,
    IMAGE,
    SERVING_SIZE,
    QUANTITY,
    CATEGORIES,
    LABELS
}

data class FieldProvenance(
    val source: ProductSource,
    /** Epoch millis for user entries, epoch seconds for Open Food Facts' own last edit. */
    val timestamp: Long?,
    /** Only meaningful for [ProductSource.USER_ENTERED]: were the values copied off the label? */
    val readFromLabel: Boolean? = null
)

enum class NutritionBasis { PER_100_G, PER_100_ML, UNKNOWN }

enum class NutrientOrigin {
    /** Value came straight from the source record. */
    REPORTED,

    /** Value was computed from another reported value using a documented conversion. */
    DERIVED
}

data class Nutrient(
    val kind: NutrientKind,
    val amount: Double,
    val unit: String,
    val origin: NutrientOrigin = NutrientOrigin.REPORTED
)

enum class NutrientKind {
    ENERGY_KCAL,
    ENERGY_KJ,
    FAT,
    SATURATED_FAT,
    CARBOHYDRATES,
    /** Total sugars. Never merged with added sugars. */
    SUGARS,
    /** Added sugars only, reported separately by Open Food Facts as `added-sugars_100g`. */
    ADDED_SUGARS,
    PROTEIN,
    SALT,
    SODIUM,
    FIBER
}
