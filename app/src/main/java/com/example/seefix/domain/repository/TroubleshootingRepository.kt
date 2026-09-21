package com.example.seefix.domain.repository

import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.TroubleshootingSession
import kotlinx.coroutines.flow.Flow

interface TroubleshootingRepository {
    fun getActiveSession(): Flow<TroubleshootingSession?>
    fun getSessionHistory(): Flow<List<TroubleshootingSession>>
    suspend fun createSession(device: HardwareDevice?, problemDescription: String): TroubleshootingSession
    suspend fun saveSession(session: TroubleshootingSession)
    suspend fun updateActiveSession(update: (TroubleshootingSession) -> TroubleshootingSession)
    suspend fun clearActiveSession()
}
