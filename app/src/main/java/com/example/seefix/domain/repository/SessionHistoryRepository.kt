package com.example.seefix.domain.repository

import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.TroubleshootingSession
import kotlinx.coroutines.flow.Flow

interface SessionHistoryRepository {
    fun getHistory(): Flow<List<TroubleshootingSession>>
    fun getSeeFixSessions(): Flow<List<SeeFixSession>>
    suspend fun saveSession(session: TroubleshootingSession)
    suspend fun saveSession(session: SeeFixSession)
    suspend fun getSeeFixSession(sessionId: String): SeeFixSession?
    suspend fun deleteSession(sessionId: String)
    suspend fun clearHistory()
}
