package com.example.seefix.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.router.AIMode

open class AIPreferenceManager(
    context: Context? = null,
    inMemoryPrefs: SharedPreferences? = null
) {

    private val prefs: SharedPreferences? = inMemoryPrefs ?: context?.let { createEncryptedSharedPreferences(it) }
    private val memoryMap = mutableMapOf<String, Any?>()

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREFS_NAME = "seefix_ai_preferences"
        private const val KEY_AI_MODE = "ai_mode"
        private const val KEY_ACTIVE_CLOUD_PROVIDER = "active_cloud_provider"

        private const val PREFIX_API_KEY = "api_key_"
        private const val PREFIX_BASE_URL = "base_url_"
        private const val PREFIX_MODEL = "model_"
        private const val PREFIX_TIMEOUT = "timeout_"
        private const val PREFIX_STREAMING = "streaming_"
    }

    open fun getAIMode(): AIMode {
        val modeStr = prefs?.getString(KEY_AI_MODE, AIMode.AUTO.name)
            ?: (memoryMap[KEY_AI_MODE] as? String)
            ?: AIMode.AUTO.name
        return try {
            AIMode.valueOf(modeStr)
        } catch (_: Exception) {
            AIMode.AUTO
        }
    }

    open fun setAIMode(mode: AIMode) {
        if (prefs != null) {
            prefs.edit().putString(KEY_AI_MODE, mode.name).apply()
        } else {
            memoryMap[KEY_AI_MODE] = mode.name
        }
    }

    open fun getActiveCloudProviderType(): AIProviderType {
        val providerStr = prefs?.getString(KEY_ACTIVE_CLOUD_PROVIDER, AIProviderType.GOOGLE_GEMINI.name)
            ?: (memoryMap[KEY_ACTIVE_CLOUD_PROVIDER] as? String)
            ?: AIProviderType.GOOGLE_GEMINI.name
        return try {
            AIProviderType.valueOf(providerStr)
        } catch (_: Exception) {
            AIProviderType.GOOGLE_GEMINI
        }
    }

    open fun setActiveCloudProviderType(type: AIProviderType) {
        if (prefs != null) {
            prefs.edit().putString(KEY_ACTIVE_CLOUD_PROVIDER, type.name).apply()
        } else {
            memoryMap[KEY_ACTIVE_CLOUD_PROVIDER] = type.name
        }
    }

    open fun getProviderConfig(type: AIProviderType): AIProviderConfig {
        val defaultBaseUrl = getDefaultBaseUrl(type)
        val defaultModel = getDefaultModel(type)

        val baseUrl = prefs?.getString(PREFIX_BASE_URL + type.name, defaultBaseUrl)
            ?: (memoryMap[PREFIX_BASE_URL + type.name] as? String)
            ?: defaultBaseUrl

        val apiKey = prefs?.getString(PREFIX_API_KEY + type.name, "")
            ?: (memoryMap[PREFIX_API_KEY + type.name] as? String)
            ?: ""

        val model = prefs?.getString(PREFIX_MODEL + type.name, defaultModel)
            ?: (memoryMap[PREFIX_MODEL + type.name] as? String)
            ?: defaultModel

        val timeout = prefs?.getLong(PREFIX_TIMEOUT + type.name, 30L)
            ?: (memoryMap[PREFIX_TIMEOUT + type.name] as? Long)
            ?: 30L

        val streaming = prefs?.getBoolean(PREFIX_STREAMING + type.name, true)
            ?: (memoryMap[PREFIX_STREAMING + type.name] as? Boolean)
            ?: true

        return AIProviderConfig(
            providerType = type,
            name = getProviderDisplayName(type),
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            timeoutSeconds = timeout,
            streamingEnabled = streaming
        )
    }

    open fun saveProviderConfig(config: AIProviderConfig) {
        val type = config.providerType
        if (prefs != null) {
            prefs.edit()
                .putString(PREFIX_BASE_URL + type.name, config.baseUrl)
                .putString(PREFIX_API_KEY + type.name, config.apiKey)
                .putString(PREFIX_MODEL + type.name, config.model)
                .putLong(PREFIX_TIMEOUT + type.name, config.timeoutSeconds)
                .putBoolean(PREFIX_STREAMING + type.name, config.streamingEnabled)
                .apply()
        } else {
            memoryMap[PREFIX_BASE_URL + type.name] = config.baseUrl
            memoryMap[PREFIX_API_KEY + type.name] = config.apiKey
            memoryMap[PREFIX_MODEL + type.name] = config.model
            memoryMap[PREFIX_TIMEOUT + type.name] = config.timeoutSeconds
            memoryMap[PREFIX_STREAMING + type.name] = config.streamingEnabled
        }
    }

    open fun getApiKey(type: AIProviderType): String {
        return prefs?.getString(PREFIX_API_KEY + type.name, "")
            ?: (memoryMap[PREFIX_API_KEY + type.name] as? String)
            ?: ""
    }

    open fun setApiKey(type: AIProviderType, apiKey: String) {
        if (prefs != null) {
            prefs.edit().putString(PREFIX_API_KEY + type.name, apiKey).apply()
        } else {
            memoryMap[PREFIX_API_KEY + type.name] = apiKey
        }
    }

    open fun getMaskedApiKey(type: AIProviderType): String {
        val key = getApiKey(type)
        return maskApiKey(key)
    }

    open fun getMaskedApiKey(apiKey: String): String {
        return maskApiKey(apiKey)
    }

    private fun maskApiKey(key: String): String {
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••" + key.takeLast(2)
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix••••$suffix"
    }

    private fun getDefaultBaseUrl(type: AIProviderType): String {
        return when (type) {
            AIProviderType.LOCAL_GEMMA -> ""
            AIProviderType.GOOGLE_GEMINI -> "https://generativelanguage.googleapis.com"
            AIProviderType.OPENAI -> "https://api.openai.com"
            AIProviderType.OPENROUTER -> "https://openrouter.ai/api"
            AIProviderType.ANTHROPIC -> "https://api.anthropic.com"
            AIProviderType.OLLAMA -> "http://localhost:11434"
            AIProviderType.CUSTOM -> ""
        }
    }

    private fun getDefaultModel(type: AIProviderType): String {
        return when (type) {
            AIProviderType.LOCAL_GEMMA -> "gemma-2b-it-gpu-int4.bin"
            AIProviderType.GOOGLE_GEMINI -> "gemini-1.5-flash"
            AIProviderType.OPENAI -> "gpt-4o-mini"
            AIProviderType.OPENROUTER -> "google/gemini-flash-1.5"
            AIProviderType.ANTHROPIC -> "claude-3-5-haiku-20241022"
            AIProviderType.OLLAMA -> "gemma2"
            AIProviderType.CUSTOM -> ""
        }
    }

    private fun getProviderDisplayName(type: AIProviderType): String {
        return when (type) {
            AIProviderType.LOCAL_GEMMA -> "Local Gemma On-Device"
            AIProviderType.GOOGLE_GEMINI -> "Google Gemini Cloud"
            AIProviderType.OPENAI -> "OpenAI"
            AIProviderType.OPENROUTER -> "OpenRouter"
            AIProviderType.ANTHROPIC -> "Anthropic Claude"
            AIProviderType.OLLAMA -> "Ollama Local API"
            AIProviderType.CUSTOM -> "Custom OpenAI-Compatible API"
        }
    }
}
