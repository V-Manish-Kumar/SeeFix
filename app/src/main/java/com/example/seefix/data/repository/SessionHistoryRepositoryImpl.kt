package com.example.seefix.data.repository

import com.example.seefix.data.local.SeeFixDatabase
import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.repository.SessionHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionHistoryRepositoryImpl(
    private val database: SeeFixDatabase = SeeFixDatabase()
) : SessionHistoryRepository {

    private val _historyFlow = MutableStateFlow<List<TroubleshootingSession>>(database.getAllSessions())
    private val _seeFixSessionsFlow = MutableStateFlow<List<SeeFixSession>>(
        database.getAllSessions().map { SeeFixSession.fromTroubleshootingSession(it) }
    )

    override fun getHistory(): Flow<List<TroubleshootingSession>> = _historyFlow.asStateFlow()

    override fun getSeeFixSessions(): Flow<List<SeeFixSession>> = _seeFixSessionsFlow.asStateFlow()

    override suspend fun saveSession(session: TroubleshootingSession) {
        database.insertSession(session)
        val allSessions = database.getAllSessions()
        _historyFlow.value = allSessions
        _seeFixSessionsFlow.value = allSessions.map { SeeFixSession.fromTroubleshootingSession(it) }
    }

    override suspend fun saveSession(session: SeeFixSession) {
        val troubleSession = session.toTroubleshootingSession()
        database.insertSession(troubleSession)
        val currentList = _seeFixSessionsFlow.value.toMutableList()
        currentList.removeAll { it.sessionId == session.sessionId }
        currentList.add(0, session)
        _seeFixSessionsFlow.value = currentList
        _historyFlow.value = database.getAllSessions()
    }

    override suspend fun getSeeFixSession(sessionId: String): SeeFixSession? {
        return _seeFixSessionsFlow.value.firstOrNull { it.sessionId == sessionId }
    }

    override suspend fun deleteSession(sessionId: String) {
        database.deleteSession(sessionId)
        val allSessions = database.getAllSessions()
        _historyFlow.value = allSessions
        _seeFixSessionsFlow.value = _seeFixSessionsFlow.value.filter { it.sessionId != sessionId }
    }

    override suspend fun clearHistory() {
        database.clearAll()
        _historyFlow.value = database.getAllSessions()
        _seeFixSessionsFlow.value = emptyList()
    }
}
