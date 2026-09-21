package com.example.seefix.data.repository

import com.example.seefix.data.local.SeeFixDatabase
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.repository.TroubleshootingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TroubleshootingRepositoryImpl(
    private val database: SeeFixDatabase = SeeFixDatabase()
) : TroubleshootingRepository {

    private val _activeSession = MutableStateFlow<TroubleshootingSession?>(null)
    private val _history = MutableStateFlow<List<TroubleshootingSession>>(database.getAllSessions())

    override fun getActiveSession(): Flow<TroubleshootingSession?> = _activeSession.asStateFlow()

    override fun getSessionHistory(): Flow<List<TroubleshootingSession>> = _history.asStateFlow()

    override suspend fun createSession(
        device: HardwareDevice?,
        problemDescription: String
    ): TroubleshootingSession {
        val query = (device?.name ?: "") + " " + problemDescription
        val baseScenario = DemoScenarios.getScenarioForAppliance(query)

        val newSession = baseScenario.copy(
            id = "sess_" + UUID.randomUUID().toString().take(8),
            device = device ?: baseScenario.device,
            detectedProblem = if (problemDescription.isNotEmpty()) problemDescription else baseScenario.detectedProblem,
            timestamp = System.currentTimeMillis()
        )

        saveSession(newSession)
        _activeSession.value = newSession
        return newSession
    }

    override suspend fun saveSession(session: TroubleshootingSession) {
        database.insertSession(session)
        _activeSession.value = session
        _history.value = database.getAllSessions()
    }

    override suspend fun updateActiveSession(update: (TroubleshootingSession) -> TroubleshootingSession) {
        val current = _activeSession.value ?: return
        val updated = update(current)
        saveSession(updated)
    }

    override suspend fun clearActiveSession() {
        _activeSession.value = null
    }
}
