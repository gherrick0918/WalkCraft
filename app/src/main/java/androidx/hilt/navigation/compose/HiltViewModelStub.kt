package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.health.HealthConnectManager
import com.walkcraft.app.ui.screens.run.RunViewModel
import com.walkcraft.app.ui.viewmodel.DeviceSetupViewModel

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(): VM {
    val appContext = LocalContext.current.applicationContext
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(RunViewModel::class.java) -> {
                    @Suppress("UNCHECKED_CAST")
                    RunViewModel(appContext) as T
                }
                modelClass.isAssignableFrom(DeviceSetupViewModel::class.java) -> {
                    @Suppress("UNCHECKED_CAST")
                    DeviceSetupViewModel(
                        appContext,
                        HealthConnectManager(appContext)
                    ) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }
        }
    }
    return viewModel(factory = factory)
}
