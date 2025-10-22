package com.walkcraft.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.health.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DeviceSetupViewModel @Inject constructor(
    app: Application,
    private val hc: HealthConnectManager
) : AndroidViewModel(app) {

    data class HealthUiState(
        val available: Boolean = false,
        val granted: Boolean = false,
        val checking: Boolean = false
    )

    private val _health = MutableStateFlow(HealthUiState())
    val health: StateFlow<HealthUiState> = _health

    fun refreshHealth() {
        if (!hc.isAvailable()) {
            _health.value = HealthUiState(available = false, granted = false, checking = false)
            return
        }
        _health.value = HealthUiState(available = true, granted = false, checking = true)
        viewModelScope.launch {
            val ok = hc.hasAllPermissions()
            _health.value = HealthUiState(available = true, granted = ok, checking = false)
        }
    }

    fun hcPermissionContract() = hc.requestPermissionsContract()
    fun hcRequiredPermissions(): Set<String> = hc.requiredPermissions
}
