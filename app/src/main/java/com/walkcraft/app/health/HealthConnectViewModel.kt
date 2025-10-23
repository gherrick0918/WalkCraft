package com.walkcraft.app.health

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HealthConnectUiState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val hasAllPermissions: Boolean = false,
    val checking: Boolean = false,
    val lastGranted: Set<HealthPermission> = emptySet()
)

class HealthConnectViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = HealthConnectManager(app)

    private val _ui = MutableStateFlow(HealthConnectUiState())
    val ui: StateFlow<HealthConnectUiState> = _ui

    fun refreshAvailability() {
        _ui.value = _ui.value.copy(sdkStatus = manager.sdkStatus())
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val status = manager.sdkStatus()
            if (status != HealthConnectClient.SDK_AVAILABLE) {
                _ui.value = _ui.value.copy(
                    sdkStatus = status,
                    hasAllPermissions = false,
                    checking = false
                )
                return@launch
            }
            _ui.value = _ui.value.copy(checking = true, sdkStatus = status)
            val hasAllPermissions = manager.hasAllPermissions()
            _ui.value = _ui.value.copy(
                hasAllPermissions = hasAllPermissions,
                checking = false
            )
        }
    }

    fun onPermissionsResult(granted: Set<HealthPermission>) {
        _ui.value = _ui.value.copy(
            lastGranted = granted,
            hasAllPermissions = granted.containsAll(manager.requiredPermissions)
        )
    }

    val requiredPermissions: Set<HealthPermission>
        get() = manager.requiredPermissions
}
