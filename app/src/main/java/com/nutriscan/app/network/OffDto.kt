package com.nutriscan.app.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Response envelope of `GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json`.
 *
 * Verified against live responses (see README "Verified API behaviour"):
 *  - found product: HTTP 200, `status` = "success" or "success_with_warnings", `product` populated;
 *  - unknown barcode: HTTP 404, `status` = "failure", no `product`, `result.id` = "product_not_found";
 *  - barcode that exists in another family database (beauty, pet food, ...): HTTP 200,
 *    `status` = "failure", `result.id` = "product_found_with_a_different_product_type" and
 *    `errors[].field.value` names the family. Requesting `?product_type=all` makes the server
 *    redirect to the right family host instead, which is what the app now does.
 */
data class OffProductResponse(
    @SerializedName("code") val code: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("product") val product: OffProductDto? = null,
    @SerializedName("errors") val errors: List<OffErrorDto>? = null,
    @SerializedName("result") val result: OffResultDto? = null
)

data class OffErrorDto(
    @SerializedName("field") val field: OffFieldDto? = null,
    @SerializedName("message") val message: OffMessageDto? = null
)

data class OffFieldDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String? = null
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
    @SerializedName("product_type") val productType: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("product_name_en") val productNameEn: String? = null,
    @SerializedName("product_name_bn") val productNameBn: String? = null,
    @SerializedName("generic_name") val genericName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName("categories") val categories: String? = null,
    @SerializedName("categories_tags") val categoriesTags: List<String>? = null,
    @SerializedName("labels") val labels: String? = null,
    @SerializedName("labels_tags") val labelsTags: List<String>? = null,
    @SerializedName("quantity") val quantity: String? = null,
    @SerializedName("product_quantity") val productQuantity: Double? = null,
    @SerializedName("serving_size") val servingSize: String? = null,
    @SerializedName("serving_quantity") val servingQuantity: Double? = null,
    @SerializedName("ingredients_text") val ingredientsText: String? = null,
    @SerializedName("ingredients_text_en") val ingredientsTextEn: String? = null,
    @SerializedName("ingredients_text_bn") val ingredientsTextBn: String? = null,
    @SerializedName("ingredients_n") val ingredientsN: Double? = null,
    /** Structured ingredient list. Shape varies per record, so it is parsed defensively. */
    @SerializedName("ingredients") val ingredients: JsonElement? = null,
    @SerializedName("allergens") val allergens: String? = null,
    @SerializedName("allergens_tags") val allergensTags: List<String>? = null,
    @SerializedName("traces") val traces: String? = null,
    @SerializedName("traces_tags") val tracesTags: List<String>? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_front_url") val imageFrontUrl: String? = null,
    @SerializedName("image_small_url") val imageSmallUrl: String? = null,
    @SerializedName("nutrition_data_per") val nutritionDataPer: String? = null,
    @SerializedName("nutrition_grades") val nutritionGrades: String? = null,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String? = null,
    @SerializedName("nutriscore_score") val nutriscoreScore: Double? = null,
    @SerializedName("nova_group") val novaGroup: Double? = null,
    @SerializedName("completeness") val completeness: Double? = null,
    @SerializedName("last_modified_t") val lastModifiedT: Double? = null,
    @SerializedName("states_tags") val statesTags: List<String>? = null,
    @SerializedName("nutriments") val nutriments: OffNutrimentsDto? = null
)

/**
 * The per-100 g/ml values the app displays. Open Food Facts sends a much larger nutriments object;
 * unknown keys are ignored by Gson.
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
    @SerializedName("added-sugars_100g") val addedSugars100g: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("salt_100g") val salt100g: Double? = null,
    @SerializedName("sodium_100g") val sodium100g: Double? = null,
    @SerializedName("fiber_100g") val fiber100g: Double? = null
)
