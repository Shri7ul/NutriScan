package com.nutriscan.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.app.model.ProductError
import com.nutriscan.app.model.ProductSummary
import com.nutriscan.app.model.SearchResult
import com.nutriscan.app.repository.ProductLookupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val items: List<ProductSummary>, val total: Int) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val error: ProductError) : SearchUiState
}

/**
 * Search is explicitly user triggered, never as-you-type: Open Food Facts rate limits search to
 * 10 requests/minute and asks clients not to issue a request per keystroke.
 */
class SearchViewModel(
    private val repository: ProductLookupRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun submit(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _state.value = SearchUiState.Empty
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = when (val result = repository.search(query)) {
                is SearchResult.Success -> SearchUiState.Results(result.items, result.total)
                SearchResult.Empty -> SearchUiState.Empty
                is SearchResult.Failure -> SearchUiState.Error(result.error)
            }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = SearchUiState.Idle
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
