package com.example.seefix.ai.providers

import android.content.Context
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIService
import com.example.seefix.ai.local.LocalGemmaAIService
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

class AIProviderRegistry(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val services = ConcurrentHashMap<AIProviderType, AIService>()

    @Volatile
    private var activeCloudProviderType: AIProviderType = AIProviderType.GOOGLE_GEMINI

    fun registerProvider(service: AIService) {
        services[service.providerConfig.providerType] = service
    }

    fun unregisterProvider(type: AIProviderType) {
        services.remove(type)
    }

    fun getProvider(type: AIProviderType): AIService? {
        return services[type]
    }

    fun getAllProviders(): Map<AIProviderType, AIService> {
        return services.toMap()
    }

    fun setActiveCloudProviderType(type: AIProviderType) {
        activeCloudProviderType = type
    }

    fun getActiveCloudProviderType(): AIProviderType {
        return activeCloudProviderType
    }

    fun getActiveCloudProvider(): AIService? {
        return services[activeCloudProviderType]
    }

    fun clear() {
        services.clear()
    }

    fun createProvider(config: AIProviderConfig, context: Context? = null): AIService {
        return when (config.providerType) {
            AIProviderType.GOOGLE_GEMINI -> GeminiAIService(config, okHttpClient)
            AIProviderType.OPENAI,
            AIProviderType.OPENROUTER,
            AIProviderType.OLLAMA,
            AIProviderType.CUSTOM -> OpenAICompatibleAIService(config, okHttpClient)
            AIProviderType.ANTHROPIC -> AnthropicAIService(config, okHttpClient)
            AIProviderType.LOCAL_GEMMA -> {
                requireNotNull(context) { "Context is required to create LocalGemmaAIService" }
                LocalGemmaAIService(context, providerConfig = config)
            }
        }
    }
}
