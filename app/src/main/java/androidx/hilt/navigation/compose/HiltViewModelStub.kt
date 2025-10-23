package androidx.hilt.navigation.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.health.HealthConnectViewModel
import com.walkcraft.app.ui.screens.run.RunViewModel

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(): VM {
    val appContext = LocalContext.current.applicationContext
    val application = appContext as? Application
        ?: throw IllegalStateException("Application context is required")
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return when {
                modelClass.isAssignableFrom(RunViewModel::class.java) ->
                    RunViewModel(appContext) as T
                modelClass.isAssignableFrom(HealthConnectViewModel::class.java) ->
                    HealthConnectViewModel(application) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }
        }
    }
    return viewModel(factory = factory)
}
