package com.example.seefix.data.local

import android.content.Context
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.domain.model.TroubleshootingSession
import kotlinx.serialization.json.Json

/**
 * Local database persistence for SeeFix supporting SharedPreferences JSON storage
 * and preloaded hackathon demo session history.
 */
class SeeFixDatabase(context: Context? = null) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val prefs = context?.getSharedPreferences("seefix_session_db", Context.MODE_PRIVATE)

    private val sessions = mutableListOf<TroubleshootingSession>()

    init {
        loadFromStorage()
    }

    @Synchronized
    private fun loadFromStorage() {
        sessions.clear()
        val savedJson = prefs?.getString("saved_sessions_json", null)
        if (!savedJson.isNullOrEmpty()) {
            try {
                val loaded = json.decodeFromString<List<TroubleshootingSession>>(savedJson)
                sessions.addAll(loaded)
            } catch (_: Exception) {
                sessions.addAll(DemoScenarios.getPreloadedHistory())
            }
        } else {
            sessions.addAll(DemoScenarios.getPreloadedHistory())
            saveToStorage()
        }
    }

    @Synchronized
    private fun saveToStorage() {
        if (prefs == null) return
        try {
            val encoded = json.encodeToString(sessions.toList())
            prefs.edit().putString("saved_sessions_json", encoded).apply()
        } catch (_: Exception) {
            // Ignore write error
        }
    }

    @Synchronized
    fun getAllSessions(): List<TroubleshootingSession> {
        return sessions.sortedByDescending { it.timestamp }
    }

    @Synchronized
    fun insertSession(session: TroubleshootingSession) {
        sessions.removeAll { it.id == session.id }
        sessions.add(0, session)
        saveToStorage()
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        saveToStorage()
    }

    @Synchronized
    fun clearAll() {
        sessions.clear()
        saveToStorage()
    }
}
