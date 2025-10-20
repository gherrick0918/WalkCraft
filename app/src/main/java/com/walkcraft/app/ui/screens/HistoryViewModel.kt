package com.walkcraft.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = com.walkcraft.app.data.history.HistoryRepository.from(app)
    val sessions: StateFlow<List<com.walkcraft.app.domain.model.Session>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun exportCsv(): String = com.walkcraft.app.data.history.HistoryCsv.build(repo.allOnce())
}
