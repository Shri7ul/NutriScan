package com.nutriscan.app.model

/** A search hit. Only identifiers and display fields are kept; full data comes from a lookup. */
data class ProductSummary(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val imageUrl: String?,
    val quantity: String?
)

sealed interface SearchResult {
    data class Success(val items: List<ProductSummary>, val total: Int) : SearchResult
    data object Empty : SearchResult
    data class Failure(val error: ProductError) : SearchResult
}
