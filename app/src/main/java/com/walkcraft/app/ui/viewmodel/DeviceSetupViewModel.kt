package com.walkcraft.app.ui.viewmodel

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
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

    data class HealthUi(
        val installed: Boolean = false,
        val granted: Boolean = false,
        val checking: Boolean = false
    )

    private val _health = MutableStateFlow(HealthUi())
    val health: StateFlow<HealthUi> = _health

    fun refreshHealth() {
        val status = hc.sdkStatus()
        // Available/installed if status != SDK_UNAVAILABLE (we treat UPDATE_REQUIRED as “installed” for now)
        val isInstalled = status != HealthConnectClient.SDK_UNAVAILABLE
        if (!isInstalled) {
            _health.value = HealthUi(installed = false, granted = false, checking = false)
            return
        }
        _health.value = HealthUi(installed = true, granted = false, checking = true)
        viewModelScope.launch {
            val ok = hc.hasAllPermissions()
            _health.value = HealthUi(installed = true, granted = ok, checking = false)
        }
    }
}
