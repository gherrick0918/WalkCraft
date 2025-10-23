package com.walkcraft.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val client: HealthConnectClient
) : ViewModel() {

    data class UiState(
        val hasAllPermissions: Boolean = false,
        val granted: Set<HealthPermission> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** Single source of truth for the required set. */
    val requiredPermissions: Set<HealthPermission> = HealthConnectManager.requiredPermissions

    fun refresh() = viewModelScope.launch {
        val granted = client.permissionController.getGrantedPermissions()
        _uiState.value = UiState(
            hasAllPermissions = granted.containsAll(requiredPermissions),
            granted = granted
        )
    }

    /** Called by the UI after the sheet returns. */
    fun onPermissionsResult(granted: Set<HealthPermission>) {
        _uiState.value = UiState(
            hasAllPermissions = granted.containsAll(requiredPermissions),
            granted = granted
        )
    }
}
