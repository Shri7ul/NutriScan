package com.nutriscan.app.network

import com.google.gson.annotations.SerializedName

/**
 * Response envelope of `GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json`.
 *
 * Verified against live responses:
 *  - found product: `status` = "success" (or "success_with_warnings"), `product` populated
 *  - unknown barcode: HTTP 404, `status` = "failure", `product` absent,
 *    `result.id` = "product_not_found"
 */
data class OffProductResponse(
    @SerializedName("code") val code: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("product") val product: OffProductDto? = null,
    @SerializedName("errors") val errors: List<OffErrorDto>? = null,
    @SerializedName("result") val result: OffResultDto? = null
)

data class OffErrorDto(
    @SerializedName("message") val message: OffMessageDto? = null
)

data class OffMessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)

data class OffResultDto(
    @SerializedName("id") val id: String? = null
)

data class OffProductDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("product_name_en") val productNameEn: String? = null,
    @SerializedName("product_name_bn") val productNameBn: String? = null,
    @SerializedName("generic_name") val genericName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName("categories") val categories: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_front_url") val imageFrontUrl: String? = null,
    @SerializedName("image_small_url") val imageSmallUrl: String? = null,
    @SerializedName("ingredients_text") val ingredientsText: String? = null,
    @SerializedName("ingredients_text_en") val ingredientsTextEn: String? = null,
    @SerializedName("ingredients_text_bn") val ingredientsTextBn: String? = null,
    @SerializedName("allergens") val allergens: String? = null,
    @SerializedName("allergens_tags") val allergensTags: List<String>? = null,
    @SerializedName("serving_size") val servingSize: String? = null,
    @SerializedName("quantity") val quantity: String? = null,
    @SerializedName("nutrition_data_per") val nutritionDataPer: String? = null,
    @SerializedName("nutriments") val nutriments: OffNutrimentsDto? = null
)

/**
 * Only the per-100 g/ml values the milestone displays. Open Food Facts sends a much larger
 * nutriments object; unknown fields are ignored by Gson.
 */
data class OffNutrimentsDto(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerializedName("energy-kj_100g") val energyKj100g: Double? = null,
    @SerializedName("energy_100g") val energy100g: Double? = null,
    @SerializedName("energy_unit") val energyUnit: String? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null,
    @SerializedName("saturated-fat_100g") val saturatedFat100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerializedName("sugars_100g") val sugars100g: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("salt_100g") val salt100g: Double? = null,
    @SerializedName("sodium_100g") val sodium100g: Double? = null,
    @SerializedName("fiber_100g") val fiber100g: Double? = null
)
