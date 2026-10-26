package com.nutriscan.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.app.model.Product
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductResult
import com.nutriscan.app.repository.ProductRepository
import com.nutriscan.app.repository.RecentScansRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val product: Product) : DetailsUiState
    data class Error(val error: ProductError) : DetailsUiState
}

class DetailsViewModel(
    private val barcode: String,
    private val productRepository: ProductRepository,
    private val recentScansRepository: RecentScansRepository,
    private val languageTag: String
) : ViewModel() {

    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _state.value = DetailsUiState.Loading
            when (val result = productRepository.getProduct(barcode, forceRefresh)) {
                is ProductResult.Success -> {
                    recentScansRepository.record(result.product, languageTag)
                    _state.value = DetailsUiState.Success(result.product)
                }

                is ProductResult.Failure -> _state.value = DetailsUiState.Error(result.error)
            }
        }
    }
}
