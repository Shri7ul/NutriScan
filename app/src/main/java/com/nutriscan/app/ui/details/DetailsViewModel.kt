package com.nutriscan.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.app.model.CacheInfo
import com.nutriscan.app.model.LookupResult
import com.nutriscan.app.model.ProductCompleteness
import com.nutriscan.app.model.ProductCompletenessEvaluator
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductField
import com.nutriscan.app.model.UserProductDraft
import com.nutriscan.app.repository.ProductLookupRepository
import com.nutriscan.app.repository.ProductMerger
import com.nutriscan.app.repository.RecentScansRepository
import com.nutriscan.app.repository.UserProductRepository
import com.nutriscan.app.util.BarcodeValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState

    data class Content(
        val product: com.nutriscan.app.model.Product,
        val completeness: ProductCompleteness,
        val cache: CacheInfo,
        val conflicts: List<ProductField> = emptyList(),
        val hasUserEntry: Boolean = false,
        /**
         * True when the screen shows nothing but the user's own entry, because no database holds
         * the barcode. The data is then not a cached remote record and must not be described as one.
         */
        val userEntryOnly: Boolean = false,
        /** Non-null when the network failed but a local copy could still be shown. */
        val loadError: ProductError? = null
    ) : DetailsUiState

    data class NotFound(val barcode: String, val triedCodes: List<String>) : DetailsUiState

    data class Error(val error: ProductError, val barcode: String) : DetailsUiState
}

/**
 * Loads one product and combines the external record with any local user entry for the same
 * barcode. Cancelling the screen cancels the HTTP request (the repository tracks waiters).
 */
class DetailsViewModel(
    barcode: String,
    private val productRepository: ProductLookupRepository,
    private val userProductRepository: UserProductRepository,
    private val recentScansRepository: RecentScansRepository,
    private val languageTag: String,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val barcode: String = BarcodeValidator.sanitize(barcode) ?: barcode.trim()

    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = DetailsUiState.Loading
            val draft = userProductRepository.find(barcode)

            when (val result = productRepository.lookup(barcode, forceRefresh)) {
                is LookupResult.Found -> showFound(result, draft)
                is LookupResult.NotFound -> showNotFound(result, draft)
                is LookupResult.Failed -> showFailure(result.error, draft)
            }
        }
    }

    private fun showFound(result: LookupResult.Found, draft: UserProductDraft?) {
        val merged = ProductMerger.merge(result.product, draft)
        recentScansRepository.record(merged.product, languageTag)
        _state.value = DetailsUiState.Content(
            product = merged.product,
            completeness = ProductCompletenessEvaluator.evaluate(merged.product, clock()),
            cache = result.cache,
            conflicts = merged.conflicts,
            hasUserEntry = draft != null
        )
    }

    private fun showNotFound(result: LookupResult.NotFound, draft: UserProductDraft?) {
        if (draft != null && draft.hasContent) {
            // The user's own entry is the only data available; it is never presented as database data.
            val product = draft.toProduct()
            _state.value = DetailsUiState.Content(
                product = product,
                completeness = ProductCompletenessEvaluator.evaluate(product, clock()),
                cache = CacheInfo.LIVE,
                hasUserEntry = true,
                userEntryOnly = true
            )
            return
        }
        _state.value = DetailsUiState.NotFound(result.barcode, result.triedCodes)
    }

    private fun showFailure(error: ProductError, draft: UserProductDraft?) {
        if (draft != null && draft.hasContent) {
            val product = draft.toProduct()
            _state.value = DetailsUiState.Content(
                product = product,
                completeness = ProductCompletenessEvaluator.evaluate(product, clock()),
                cache = CacheInfo.LIVE,
                hasUserEntry = true,
                userEntryOnly = true,
                loadError = error
            )
            return
        }
        _state.value = DetailsUiState.Error(error, barcode)
    }
}
