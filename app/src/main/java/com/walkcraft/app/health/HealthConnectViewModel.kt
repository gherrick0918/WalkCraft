package com.walkcraft.app.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
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
    private val _ui = MutableStateFlow(HealthConnectUiState())
    val ui: StateFlow<HealthConnectUiState> = _ui

    fun refreshAvailability() {
        val ctx = getApplication<Application>()
        val status = HealthConnectManager.sdkStatus(ctx)
        _ui.value = _ui.value.copy(sdkStatus = status)
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val status = HealthConnectManager.sdkStatus(ctx)
            if (status == HealthConnectClient.SDK_UNAVAILABLE) {
                _ui.value = _ui.value.copy(sdkStatus = status, hasAllPermissions = false)
                return@launch
            }
            _ui.value = _ui.value.copy(checking = true, sdkStatus = status)
            val client = HealthConnectManager.client(ctx)
            val granted = client.permissionController.getGrantedPermissions()
            _ui.value = _ui.value.copy(
                hasAllPermissions = granted.containsAll(HealthConnectManager.PERMISSIONS),
                checking = false
            )
        }
    }

    fun onPermissionsResult(granted: Set<HealthPermission>) {
        _ui.value = _ui.value.copy(
            lastGranted = granted,
            hasAllPermissions = granted.containsAll(HealthConnectManager.PERMISSIONS)
        )
    }
}
