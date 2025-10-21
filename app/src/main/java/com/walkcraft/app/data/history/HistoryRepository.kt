package com.walkcraft.app.data.history

import android.content.Context
import com.walkcraft.app.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository private constructor(
    @Suppress("UNUSED_PARAMETER") appContext: Context
) {
    private val lock = Mutex()

    private val sessionMap = LinkedHashMap<String, Session>()
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    suspend fun insertIgnore(session: Session) {
        lock.withLock {
            if (sessionMap.containsKey(session.id)) return
            sessionMap[session.id] = session
            _sessions.value = sessionMap.values.toList()
        }
    }

    suspend fun clear() {
        lock.withLock {
            sessionMap.clear()
            _sessions.value = emptyList()
        }
    }

    fun observe(): Flow<List<Session>> = sessions
    suspend fun allOnce(): List<Session> = sessions.first()

    companion object {
        @Volatile
        private var INSTANCE: HistoryRepository? = null

        fun from(context: Context): HistoryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
