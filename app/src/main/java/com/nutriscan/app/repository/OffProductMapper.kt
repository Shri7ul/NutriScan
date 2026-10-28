package com.nutriscan.app.repository

import com.google.gson.JsonElement
import com.nutriscan.app.model.FieldProvenance
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutrientOrigin
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductField
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.ProductType
import com.nutriscan.app.network.OffNutrimentsDto
import com.nutriscan.app.network.OffProductDto
import java.util.Locale

/**
 * Converts the Open Food Facts DTO into the display model.
 *
 * Deliberately conservative:
 *  - a value is only emitted when the API actually returned it; null never becomes zero;
 *  - every computed value is flagged [NutrientOrigin.DERIVED] so the UI can say so;
 *  - total sugars and added sugars stay separate, and salt and sodium are never conflated.
 */
internal object OffProductMapper {

    private const val KCAL_PER_KJ = 1.0 / 4.184
    private const val KJ_PER_KCAL = 4.184

    /** Salt (g) = sodium (g) x 2.5. The factor used on EU and WHO nutrition labelling. */
    private const val SALT_PER_SODIUM = 2.5

    private const val GRAM_UNIT = "g"
    private const val KCAL_UNIT = "kcal"
    private const val KJ_UNIT = "kJ"

    private const val MAX_TAGS = 8

    private val GRADE_LETTERS = setOf("a", "b", "c", "d", "e")

    /**
     * Contributors sometimes type a placeholder into a tag field, and the import turns it into a
     * real tag: the live record for one Nutella multipack carries `categories_tags: ["en:null"]`.
     * Such values mean "no value" and must not be displayed as a category or a label.
     */
    private val SENTINEL_VALUES = setOf("null", "undefined", "n/a", "na", "unknown", "none", "-", "--")

    fun map(barcode: String, dto: OffProductDto): Product {
        val productType = ProductType.fromApi(dto.productType)
        val lastModified = dto.lastModifiedT?.takeIf { it > 0 }?.toLong()
        val nutrients = buildNutrients(dto.nutriments)
        val categories = tagsOrList(dto.categoriesTags, dto.categories)
        val labels = tagsOrList(dto.labelsTags, dto.labels)
        val allergens = tagsOrList(dto.allergensTags, dto.allergens)
        val traces = tagsOrList(dto.tracesTags, dto.traces)

        val name = dto.productName.clean()
            ?: dto.productNameEn.clean()
            ?: dto.genericName.clean()
        val brand = dto.brands.clean()?.replace(Regex("\\s*,\\s*"), ", ")
        val ingredients = dto.ingredientsText.clean() ?: dto.ingredientsTextEn.clean()
        val image = dto.imageFrontUrl.clean() ?: dto.imageSmallUrl.clean() ?: dto.imageUrl.clean()

        return Product(
            barcode = barcode,
            productType = productType,
            name = name,
            nameEn = dto.productNameEn.clean(),
            nameBn = dto.productNameBn.clean(),
            genericName = dto.genericName.clean(),
            brand = brand,
            categories = categories,
            labels = labels,
            quantity = dto.quantity.clean(),
            servingSize = dto.servingSize.clean(),
            ingredients = ingredients,
            ingredientsEn = dto.ingredientsTextEn.clean(),
            ingredientsBn = dto.ingredientsTextBn.clean(),
            ingredientCount = dto.ingredientsN?.toInt() ?: structuredIngredientCount(dto.ingredients),
            allergens = allergens,
            traces = traces,
            imageUrl = dto.imageUrl.clean(),
            imageFrontUrl = dto.imageFrontUrl.clean(),
            nutritionBasis = basisFor(dto.nutritionDataPer, nutrients),
            nutrients = nutrients,
            nutritionGrade = gradeOf(dto.nutriscoreGrade ?: dto.nutritionGrades),
            nutritionScore = dto.nutriscoreScore?.toInt(),
            novaGroup = dto.novaGroup?.toInt()?.takeIf { it in 1..4 },
            lastModifiedEpochSeconds = lastModified,
            offCompleteness = dto.completeness?.takeIf { it in 0.0..1.0 },
            states = statesOf(dto.statesTags),
            source = ProductSource.OPEN_FOOD_FACTS,
            provenance = provenanceOf(
                name = name,
                brand = brand,
                ingredients = ingredients,
                nutrients = nutrients,
                allergens = allergens,
                image = image,
                servingSize = dto.servingSize.clean(),
                quantity = dto.quantity.clean(),
                categories = categories,
                labels = labels,
                timestamp = lastModified
            )
        )
    }

    private fun buildNutrients(dto: OffNutrimentsDto?): List<Nutrient> {
        if (dto == null) return emptyList()
        val nutrients = mutableListOf<Nutrient>()

        val kcal = dto.energyKcal100g.valid()
        val kj = dto.energyKj100g.valid()
        val raw = dto.energy100g.valid()
        val rawUnit = dto.energyUnit?.clean()?.lowercase(Locale.ROOT)

        // Energy: report kcal when the record has it, otherwise convert from kJ (or from the
        // legacy `energy_100g` + `energy_unit` pair). Conversion is flagged as derived.
        when {
            kcal != null -> nutrients += Nutrient(NutrientKind.ENERGY_KCAL, kcal, KCAL_UNIT)
            kj != null -> nutrients += Nutrient(
                NutrientKind.ENERGY_KCAL, kj * KCAL_PER_KJ, KCAL_UNIT, NutrientOrigin.DERIVED
            )

            raw != null && rawUnit == "kcal" -> nutrients += Nutrient(NutrientKind.ENERGY_KCAL, raw, KCAL_UNIT)
            raw != null && rawUnit != null -> nutrients += Nutrient(
                NutrientKind.ENERGY_KCAL, raw * KCAL_PER_KJ, KCAL_UNIT, NutrientOrigin.DERIVED
            )
        }
        when {
            kj != null -> nutrients += Nutrient(NutrientKind.ENERGY_KJ, kj, KJ_UNIT)
            kcal != null -> nutrients += Nutrient(
                NutrientKind.ENERGY_KJ, kcal * KJ_PER_KCAL, KJ_UNIT, NutrientOrigin.DERIVED
            )

            raw != null && rawUnit == "kcal" -> nutrients += Nutrient(
                NutrientKind.ENERGY_KJ, raw * KJ_PER_KCAL, KJ_UNIT, NutrientOrigin.DERIVED
            )

            raw != null && rawUnit != null -> nutrients += Nutrient(NutrientKind.ENERGY_KJ, raw, KJ_UNIT)
        }

        fun reported(kind: NutrientKind, value: Double?) {
            value.valid()?.let { nutrients += Nutrient(kind, it, GRAM_UNIT) }
        }

        reported(NutrientKind.FAT, dto.fat100g)
        reported(NutrientKind.SATURATED_FAT, dto.saturatedFat100g)
        reported(NutrientKind.CARBOHYDRATES, dto.carbohydrates100g)
        reported(NutrientKind.SUGARS, dto.sugars100g)
        reported(NutrientKind.ADDED_SUGARS, dto.addedSugars100g)
        reported(NutrientKind.PROTEIN, dto.proteins100g)
        reported(NutrientKind.FIBER, dto.fiber100g)

        val salt = dto.salt100g.valid()
        val sodium = dto.sodium100g.valid()
        when {
            salt != null -> nutrients += Nutrient(NutrientKind.SALT, salt, GRAM_UNIT)
            sodium != null -> nutrients += Nutrient(
                NutrientKind.SALT, sodium * SALT_PER_SODIUM, GRAM_UNIT, NutrientOrigin.DERIVED
            )
        }
        when {
            sodium != null -> nutrients += Nutrient(NutrientKind.SODIUM, sodium, GRAM_UNIT)
            salt != null -> nutrients += Nutrient(
                NutrientKind.SODIUM, salt / SALT_PER_SODIUM, GRAM_UNIT, NutrientOrigin.DERIVED
            )
        }

        return nutrients
    }

    private fun basisFor(nutritionDataPer: String?, nutrients: List<Nutrient>): NutritionBasis {
        // Only `*_100g` keys are read, so the label is always "per 100 g/ml" - never "per serving".
        if (nutrients.isEmpty()) return NutritionBasis.UNKNOWN
        val per = nutritionDataPer?.lowercase(Locale.ROOT).orEmpty()
        return if (per.contains("ml")) NutritionBasis.PER_100_ML else NutritionBasis.PER_100_G
    }

    private fun gradeOf(raw: String?): String? {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
        // Beauty records answer "unknown"; only a real Nutri-Score letter is a grade.
        return value.takeIf { it in GRADE_LETTERS }
    }

    private fun statesOf(tags: List<String>?): Set<String> =
        tags.orEmpty().map { it.substringAfter(':').trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Prefers taxonomy tags (language-neutral) and falls back to the free-text field. */
    private fun tagsOrList(tags: List<String>?, text: String?): List<String> {
        val fromTags = tags.orEmpty()
            .map { it.humanizeTag() }
            .filter { it.isMeaningful() }
        val source = fromTags.ifEmpty {
            text.orEmpty()
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isMeaningful() }
        }
        return source.distinctBy { it.lowercase(Locale.ROOT) }.take(MAX_TAGS)
    }

    private fun String.isMeaningful(): Boolean =
        isNotEmpty() && lowercase(Locale.ROOT) !in SENTINEL_VALUES

    private fun structuredIngredientCount(element: JsonElement?): Int? {
        val array = element?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return array.count { it.isJsonObject }
    }

    private fun String.humanizeTag(): String {
        val withoutLanguage = substringAfter(':', this)
        val spaced = withoutLanguage.replace('-', ' ').replace('_', ' ').trim()
        if (spaced.isEmpty()) return spaced
        return spaced.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
        }
    }

    private fun provenanceOf(
        name: String?,
        brand: String?,
        ingredients: String?,
        nutrients: List<Nutrient>,
        allergens: List<String>,
        image: String?,
        servingSize: String?,
        quantity: String?,
        categories: List<String>,
        labels: List<String>,
        timestamp: Long?
    ): Map<ProductField, FieldProvenance> {
        val entry = FieldProvenance(ProductSource.OPEN_FOOD_FACTS, timestamp)
        return buildMap {
            put(ProductField.IDENTIFICATION, entry)
            if (name != null) put(ProductField.NAME, entry)
            if (brand != null) put(ProductField.BRAND, entry)
            if (ingredients != null) put(ProductField.INGREDIENTS, entry)
            if (nutrients.isNotEmpty()) put(ProductField.NUTRITION, entry)
            if (allergens.isNotEmpty()) put(ProductField.ALLERGENS, entry)
            if (image != null) put(ProductField.IMAGE, entry)
            if (servingSize != null) put(ProductField.SERVING_SIZE, entry)
            if (quantity != null) put(ProductField.QUANTITY, entry)
            if (categories.isNotEmpty()) put(ProductField.CATEGORIES, entry)
            if (labels.isNotEmpty()) put(ProductField.LABELS, entry)
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Double?.valid(): Double? = this?.takeIf { it.isFinite() && it >= 0.0 }
}
