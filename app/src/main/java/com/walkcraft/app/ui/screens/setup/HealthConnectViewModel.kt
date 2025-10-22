package com.walkcraft.app.ui.screens.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.data.prefs.HealthPrefs
import com.walkcraft.app.health.HealthConnectManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HcUiState(
    val sdkStatus: Int,
    val granted: Boolean
)

class HealthConnectViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()

    val uiState: StateFlow<HcUiState> =
        HealthPrefs.grantedFlow(context)
            .map { granted ->
                HcUiState(
                    sdkStatus = HealthConnectManager.sdkStatus(context),
                    granted = granted
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HcUiState(HealthConnectManager.sdkStatus(context), false)
            )

    fun markGranted(granted: Boolean) {
        viewModelScope.launch { HealthPrefs.setGranted(context, granted) }
    }

    fun refreshStatus() {
        // trigger recomposition by touching state
        viewModelScope.launch {
            markGranted(uiState.value.granted) // keep same value; sdkStatus will be recomputed on next collection
        }
    }
}
