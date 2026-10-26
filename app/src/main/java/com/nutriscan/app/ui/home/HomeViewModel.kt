package com.nutriscan.app.ui.home

import androidx.lifecycle.ViewModel
import com.nutriscan.app.model.RecentScan
import com.nutriscan.app.repository.RecentScansRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    private val recentScansRepository: RecentScansRepository
) : ViewModel() {

    val recentScans: StateFlow<List<RecentScan>> = recentScansRepository.scans

    fun clearHistory() {
        recentScansRepository.clear()
    }
}
