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
    application: Application,
    private val hc: HealthConnectManager
) : AndroidViewModel(application) {

    data class HealthUi(
        val available: Boolean = false,
        val granted: Boolean = false,
        val checking: Boolean = false
    )

    private val _health = MutableStateFlow(HealthUi())
    val health: StateFlow<HealthUi> = _health

    fun refreshHealth() {
        if (!hc.isInstalled()) {
            _health.value = HealthUi(available = false, granted = false, checking = false)
            return
        }
        _health.value = HealthUi(available = true, granted = false, checking = true)
        viewModelScope.launch {
            val ok = hc.hasAll()
            _health.value = HealthUi(available = true, granted = ok, checking = false)
        }
    }

    fun hcPermissionContract() = hc.requestContract()
    fun hcRequired() = hc.required
}
