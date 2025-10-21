package com.walkcraft.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.data.export.CsvExporter
import com.walkcraft.app.data.history.HistoryRepository
import com.walkcraft.app.domain.model.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HistoryRepository.from(app)

    val sessions: StateFlow<List<Session>> = repo.observe()
        .map { it.sortedByDescending { s -> s.endedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun exportCsv(): String = CsvExporter.allSessionsToCsv(repo.getAllSessions())

    suspend fun clearAll() = repo.clear()
}
