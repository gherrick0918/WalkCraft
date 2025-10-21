package com.walkcraft.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.data.prefs.UserPrefsRepository
import com.walkcraft.app.health.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HealthUiState(
    val available: Boolean = false,
    val granted: Boolean = false,
    val checking: Boolean = false
)

@HiltViewModel
class DeviceSetupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val hcManager: HealthConnectManager
) : ViewModel() {

    private val userPrefs = UserPrefsRepository.from(context)

    private val _health = MutableStateFlow(HealthUiState())
    val health: StateFlow<HealthUiState> = _health

    fun refreshHealthState() {
        if (!hcManager.isAvailable()) {
            _health.value = HealthUiState(available = false, granted = false, checking = false)
            viewModelScope.launch { userPrefs.setHealthConnectEnabled(false) }
            return
        }
        _health.value = _health.value.copy(available = true, checking = true)
        viewModelScope.launch {
            val granted = runCatching { hcManager.hasAllPermissions() }.getOrDefault(false)
            _health.value = HealthUiState(available = true, granted = granted, checking = false)
            userPrefs.setHealthConnectEnabled(granted)
        }
    }

    /**
     * Suspends and returns an Intent that can be launched to request HC permissions.
     * Caller launches via ActivityResult launcher.
     */
    suspend fun buildHealthPermissionIntent(): Intent =
        hcManager.createRequestPermissionsIntent()
}
