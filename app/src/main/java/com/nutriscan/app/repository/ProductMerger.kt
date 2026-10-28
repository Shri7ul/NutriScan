package com.nutriscan.app.repository

import com.nutriscan.app.model.FieldProvenance
import com.nutriscan.app.model.Nutrient
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductField
import com.nutriscan.app.model.ProductSource
import com.nutriscan.app.model.UserProductDraft

/**
 * Combines an externally sourced record with what the user typed for the same barcode.
 *
 * Rules (deliberately one-directional, so nothing verified is ever overwritten):
 *  1. the barcode must match exactly - a different code is a different product;
 *  2. user values only fill fields the external record does not provide;
 *  3. when both sides have a value and they disagree, the external value is kept and the field is
 *     reported as a conflict so the UI can mention it instead of silently discarding user work;
 *  4. provenance is recorded per field, so the details screen can label every value.
 */
object ProductMerger {

    data class Result(
        val product: Product,
        val conflicts: List<ProductField> = emptyList()
    )

    fun merge(external: Product, draft: UserProductDraft?): Result {
        if (draft == null || draft.barcode != external.barcode || !draft.hasContent) {
            return Result(external)
        }

        val local = draft.toProduct()
        val provenance = external.provenance.toMutableMap()
        val conflicts = mutableListOf<ProductField>()

        fun <T> pick(
            field: ProductField,
            externalValue: T?,
            localValue: T?,
            hasLocal: Boolean = localValue != null,
            differs: (T, T) -> Boolean = { a, b -> a != b }
        ): T? {
            if (externalValue != null) {
                if (hasLocal && localValue != null && differs(externalValue, localValue)) conflicts += field
                return externalValue
            }
            if (hasLocal && localValue != null) {
                provenance[field] = userProvenance(draft)
                return localValue
            }
            return null
        }

        val mergedNutrients = mergeNutrients(external.nutrients, local.nutrients)
        if (mergedNutrients.added.any { it !in external.nutrients }) {
            provenance[ProductField.NUTRITION] = userProvenance(draft)
        }
        if (mergedNutrients.conflict) conflicts += ProductField.NUTRITION

        val merged = external.copy(
            name = pick(ProductField.NAME, external.name, local.name, differs = ::textDiffers),
            brand = pick(ProductField.BRAND, external.brand, local.brand, differs = ::textDiffers),
            quantity = pick(ProductField.QUANTITY, external.quantity, local.quantity, differs = ::textDiffers),
            servingSize = pick(ProductField.SERVING_SIZE, external.servingSize, local.servingSize, differs = ::textDiffers),
            ingredients = pick(
                ProductField.INGREDIENTS,
                external.ingredients,
                local.ingredients,
                differs = ::textDiffers
            ),
            ingredientsEn = pick(
                ProductField.INGREDIENTS,
                external.ingredientsEn,
                local.ingredientsEn,
                differs = ::textDiffers
            ),
            allergens = if (external.allergens.isEmpty()) {
                if (local.allergens.isNotEmpty()) provenance[ProductField.ALLERGENS] = userProvenance(draft)
                local.allergens
            } else {
                if (local.allergens.isNotEmpty() && local.allergens != external.allergens) {
                    conflicts += ProductField.ALLERGENS
                }
                external.allergens
            },
            nutrients = mergedNutrients.added,
            nutritionBasis = if (external.nutrients.isEmpty()) local.nutritionBasis else external.nutritionBasis,
            userPhotoPath = external.userPhotoPath ?: local.userPhotoPath,
            provenance = provenance
        )
        return Result(merged, conflicts.distinct())
    }

    private fun mergeNutrients(external: List<Nutrient>, local: List<Nutrient>): NutrientMerge {
        if (local.isEmpty()) return NutrientMerge(external, false)
        val result = external.toMutableList()
        var conflict = false
        for (nutrient in local) {
            val existing = external.firstOrNull { it.kind == nutrient.kind }
            when {
                existing == null -> result += nutrient
                kotlin.math.abs(existing.amount - nutrient.amount) > 0.01 -> conflict = true
            }
        }
        return NutrientMerge(result, conflict)
    }

    private fun userProvenance(draft: UserProductDraft) = FieldProvenance(
        source = ProductSource.USER_ENTERED,
        timestamp = draft.updatedAt.takeIf { it > 0 },
        readFromLabel = draft.readFromLabel
    )

    private fun textDiffers(a: String, b: String): Boolean =
        !a.trim().equals(b.trim(), ignoreCase = true)

    private data class NutrientMerge(val added: List<Nutrient>, val conflict: Boolean)
}
