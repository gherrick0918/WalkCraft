package com.walkcraft.app.ui.screens.run

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walkcraft.app.domain.engine.EngineState
import com.walkcraft.app.service.WorkoutService
import com.walkcraft.app.service.WorkoutService.Companion.ACTION_PAUSE
import com.walkcraft.app.service.WorkoutService.Companion.ACTION_RESUME
import com.walkcraft.app.service.WorkoutService.Companion.ACTION_SKIP
import com.walkcraft.app.service.WorkoutService.Companion.ACTION_STOP
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RunViewModel @Inject constructor(
    @ApplicationContext private val app: Context
) : ViewModel() {

    private val _ui = MutableStateFlow<RunUiState>(RunUiState.Idle)
    val ui: StateFlow<RunUiState> = _ui.asStateFlow()

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle(null))
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private var statesJob: Job? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? WorkoutService.LocalBinder ?: return
            val service = local.service()
            statesJob?.cancel()
            statesJob = viewModelScope.launch {
                service.states().collect { st ->
                    _engineState.value = st
                    _ui.value = RunUiState.from(st)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            statesJob?.cancel()
            statesJob = null
            bound = false
            _ui.value = RunUiState.Idle
            _engineState.value = EngineState.Idle(null)
        }
    }

    fun bind() {
        if (bound) return
        val intent = Intent(app, WorkoutService::class.java)
        bound = app.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            _ui.value = RunUiState.Idle
            _engineState.value = EngineState.Idle(null)
        }
    }

    fun unbind() {
        if (!bound) return
        runCatching { app.unbindService(conn) }
        statesJob?.cancel()
        statesJob = null
        bound = false
    }

    override fun onCleared() {
        unbind()
        super.onCleared()
    }

    fun pause() = sendAction(ACTION_PAUSE)
    fun resume() = sendAction(ACTION_RESUME)
    fun skip() = sendAction(ACTION_SKIP)
    fun stop() = sendAction(ACTION_STOP)

    private fun sendAction(action: String) {
        app.startService(Intent(app, WorkoutService::class.java).setAction(action))
    }
}
