package com.nutriscan.app.repository

import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.NutrientKind
import com.nutriscan.app.model.NutritionBasis
import com.nutriscan.app.model.Product
import com.nutriscan.app.network.OffNutrimentsDto
import com.nutriscan.app.network.OffProductDto
import java.util.Locale

/**
 * Converts the Open Food Facts DTO into the display model.
 *
 * Deliberately conservative: a value is only emitted when the API actually returned it.
 * Missing values stay null / absent so the UI can show an explicit "not available" marker.
 */
internal object OffProductMapper {

    private const val ENERGY_KCAL_UNIT = "kcal"
    private const val ENERGY_KJ_UNIT = "kJ"
    private const val GRAM_UNIT = "g"

    fun map(barcode: String, dto: OffProductDto): Product {
        val nutrients = buildNutrients(dto.nutriments)
        return Product(
            barcode = barcode,
            name = dto.productName.clean() ?: dto.genericName.clean(),
            nameEn = dto.productNameEn.clean(),
            nameBn = dto.productNameBn.clean(),
            brand = dto.brands.clean(),
            categories = dto.categories.clean(),
            imageUrl = dto.imageFrontUrl.clean() ?: dto.imageUrl.clean() ?: dto.imageSmallUrl.clean(),
            ingredients = dto.ingredientsText.clean() ?: dto.ingredientsTextEn.clean(),
            ingredientsEn = dto.ingredientsTextEn.clean(),
            ingredientsBn = dto.ingredientsTextBn.clean(),
            allergens = buildAllergens(dto),
            servingSize = dto.servingSize.clean(),
            quantity = dto.quantity.clean(),
            nutritionBasis = basisFor(dto.nutritionDataPer, nutrients),
            nutrients = nutrients
        )
    }

    private fun buildNutrients(dto: OffNutrimentsDto?): List<Nutrient> {
        if (dto == null) return emptyList()

        val nutrients = mutableListOf<Nutrient>()

        val energyKcal = dto.energyKcal100g.valid()
        val energyKj = dto.energyKj100g.valid()
        val energyRaw = dto.energy100g.valid()

        when {
            energyKcal != null -> nutrients += Nutrient(NutrientKind.ENERGY, energyKcal, ENERGY_KCAL_UNIT)
            energyKj != null -> nutrients += Nutrient(NutrientKind.ENERGY, energyKj, ENERGY_KJ_UNIT)
            energyRaw != null -> nutrients += Nutrient(
                NutrientKind.ENERGY,
                energyRaw,
                dto.energyUnit?.clean()?.lowercase(Locale.ROOT) ?: ENERGY_KJ_UNIT
            )
        }

        fun add(kind: NutrientKind, value: Double?) {
            value.valid()?.let { nutrients += Nutrient(kind, it, GRAM_UNIT) }
        }

        add(NutrientKind.FAT, dto.fat100g)
        add(NutrientKind.SATURATED_FAT, dto.saturatedFat100g)
        add(NutrientKind.CARBOHYDRATES, dto.carbohydrates100g)
        add(NutrientKind.SUGARS, dto.sugars100g)
        add(NutrientKind.PROTEIN, dto.proteins100g)
        add(NutrientKind.SALT, dto.salt100g)
        add(NutrientKind.FIBER, dto.fiber100g)
        add(NutrientKind.SODIUM, dto.sodium100g)

        return nutrients
    }

    private fun basisFor(nutritionDataPer: String?, nutrients: List<Nutrient>): NutritionBasis {
        // Only `*_100g` keys are read, so the label is always "per 100 g/ml" - never "per serving".
        if (nutrients.isEmpty()) return NutritionBasis.UNKNOWN
        val per = nutritionDataPer?.lowercase(Locale.ROOT).orEmpty()
        return if (per.contains("ml")) NutritionBasis.PER_100_ML else NutritionBasis.PER_100_G
    }

    private fun buildAllergens(dto: OffProductDto): List<String> {
        val fromTags = dto.allergensTags.orEmpty()
            .map { it.humanizeAllergenTag() }
            .filter { it.isNotBlank() }

        val source = fromTags.ifEmpty {
            dto.allergens.orEmpty()
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        return source.distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun String.humanizeAllergenTag(): String {
        val withoutLanguage = substringAfter(':', this)
        val spaced = withoutLanguage.replace('-', ' ').replace('_', ' ').trim()
        if (spaced.isEmpty()) return spaced
        return spaced.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Double?.valid(): Double? =
        this?.takeIf { it.isFinite() && it >= 0.0 }
}
