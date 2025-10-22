package com.walkcraft.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
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
        val granted: Set<String> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    fun refresh() = viewModelScope.launch {
        val granted = client.permissionController.getGrantedPermissions()
        _uiState.value = UiState(
            hasAllPermissions = requiredPermissions.all { it in granted },
            granted = granted
        )
    }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) {
        refresh()
    }
}
