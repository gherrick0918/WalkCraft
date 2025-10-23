package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkcraft.app.ui.screens.run.RunViewModel

@Composable
fun hiltViewModel(): RunViewModel {
    val appContext = LocalContext.current.applicationContext
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RunViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RunViewModel(appContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
    return viewModel(factory = factory)
}
