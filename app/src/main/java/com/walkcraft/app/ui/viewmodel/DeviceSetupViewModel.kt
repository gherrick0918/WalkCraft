package com.walkcraft.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.health.HcStatus
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

    private val _health = MutableStateFlow(HealthUi())
    val health: StateFlow<HealthUi> = _health

    fun hcRequiredPermissions() = hc.requiredPermissions
    fun hcRequestContract() = hc.requestPermissionsContract()
    fun hcPlayStoreIntent() = hc.playStoreIntent()

    fun refreshHealth() {
        val st = hc.status()
        when (st) {
            HcStatus.NOT_INSTALLED, HcStatus.UPDATE_REQUIRED, HcStatus.NOT_SUPPORTED -> {
                _health.value = HealthUi(status = st, granted = false, checking = false)
            }
            HcStatus.AVAILABLE -> {
                _health.value = HealthUi(status = st, granted = false, checking = true)
                viewModelScope.launch {
                    val ok = hc.hasAll()
                    _health.value = HealthUi(status = st, granted = ok, checking = false)
                }
            }
        }
    }
}

data class HealthUi(
    val status: HcStatus = HcStatus.NOT_INSTALLED,
    val granted: Boolean = false,
    val checking: Boolean = false
)
