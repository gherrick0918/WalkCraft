package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.ui.screens.run.RunViewModel

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(): VM {
    val appContext = LocalContext.current.applicationContext
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return when {
                modelClass.isAssignableFrom(RunViewModel::class.java) ->
                    RunViewModel(appContext) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }
        }
    }
    return viewModel(factory = factory)
}
