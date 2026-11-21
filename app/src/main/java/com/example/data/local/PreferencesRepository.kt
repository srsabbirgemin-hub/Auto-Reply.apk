package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_ai_auto_reply_prefs")

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val freeModels: List<String>
) {
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "llama-3.3-70b-versatile",
        keyHint = "gsk_xxxxxxxxxxxxxxxxx",
        freeModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "gemma2-9b-it",
            "mixtral-8x7b-32768"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter (Free Tier)",
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        keyHint = "sk-or-v1-xxxxxxxxxxxx",
        freeModels = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-3-27b-it:free",
            "deepseek/deepseek-chat-v3-0324:free",
            "mistralai/mistral-7b-instruct:free"
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1/",
        defaultModel = "gpt-4o-mini",
        keyHint = "sk-proj-xxxxxxxxxxxx",
        freeModels = listOf(
            "gpt-4o-mini",
            "gpt-4.1-mini",
            "gpt-4.1-nano"
        )
    ),
    TOGETHER(
        displayName = "Together AI",
        defaultBaseUrl = "https://api.together.xyz/v1/",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
        keyHint = "xxxxxxxxxxxxxxxxxxxx",
        freeModels = listOf(
            "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            "meta-llama/Llama-3.2-3B-Instruct-Turbo"
        )
    ),
    GEMINI_COMPAT(
        displayName = "Gemini (OpenAI API)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-2.5-flash",
        keyHint = "AIzaSyxxxxxxxxxxxxxxx",
        freeModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "llama-3.1-8b-instant",
        keyHint = "Enter provider API key",
        freeModels = emptyList()
    )
}

enum class ApiConnectionStatus {
    UNCONFIGURED,
    CONNECTED,
    ERROR
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val isAutoReplyEnabled: Boolean = false,
    val apiProvider: AiProviderType = AiProviderType.GROQ,
    val apiBaseUrl: String = AiProviderType.GROQ.defaultBaseUrl,
    val modelName: String = AiProviderType.GROQ.defaultModel,
    val temperature: Float = 0.7f,
    val maxResponseTokens: Int = 220,
    val hasApiKey: Boolean = false,
    val maskedApiKey: String = "",
    val apiConnectionStatus: ApiConnectionStatus = ApiConnectionStatus.UNCONFIGURED,
    val apiLastLatencyMs: Long = 0L,
    val apiLastError: String = "",
    val globalPersonality: String = "Helpful, warm, concise, and human-like",
    val globalResponseStyle: String = "Natural & Conversational",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val replyDelaySeconds: Int = 0,
    val cooldownSeconds: Int = 60,
    val replyToGroupChats: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartHour: Int = 23,
    val quietHoursEndHour: Int = 7,
    val quietHoursReplyText: String = "I'm currently away during quiet hours and will reply when I'm back.",
    val dataSaverMode: Boolean = false
)

class PreferencesRepository(
    context: Context,
    private val secretCipher: SecretCipher = AndroidKeyStoreSecretCipher()
) {
    private val dataStore = context.applicationContext.appDataStore

    private object Keys {
        val AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val API_PROVIDER = stringPreferencesKey("api_provider")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val API_KEY_CIPHER = stringPreferencesKey("api_key_cipher")
        val API_KEY_IV = stringPreferencesKey("api_key_iv")
        val API_KEY_MASKED = stringPreferencesKey("api_key_masked")
        val API_CONNECTION_STATUS = stringPreferencesKey("api_connection_status")
        val API_LAST_LATENCY_MS = longPreferencesKey("api_last_latency_ms")
        val API_LAST_ERROR = stringPreferencesKey("api_last_error")
        val GLOBAL_PERSONALITY = stringPreferencesKey("global_personality")
        val GLOBAL_RESPONSE_STYLE = stringPreferencesKey("global_response_style")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REPLY_DELAY_SECONDS = intPreferencesKey("reply_delay_seconds")
        val COOLDOWN_SECONDS = intPreferencesKey("cooldown_seconds")
        val REPLY_TO_GROUP_CHATS = booleanPreferencesKey("reply_to_group_chats")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
        val QUIET_HOURS_REPLY = stringPreferencesKey("quiet_hours_reply")
        val DATA_SAVER_MODE = booleanPreferencesKey("data_saver_mode")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val provider = prefs[Keys.API_PROVIDER]?.let {
            runCatching { AiProviderType.valueOf(it) }.getOrNull()
        } ?: AiProviderType.GROQ

        val cipher = prefs[Keys.API_KEY_CIPHER].orEmpty()
        val iv = prefs[Keys.API_KEY_IV].orEmpty()
        val hasKey = cipher.isNotBlank() && iv.isNotBlank()

        AppSettings(
            isAutoReplyEnabled = prefs[Keys.AUTO_REPLY_ENABLED] ?: false,
            apiProvider = provider,
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: provider.defaultBaseUrl,
            modelName = prefs[Keys.MODEL_NAME] ?: provider.defaultModel,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            maxResponseTokens = prefs[Keys.MAX_TOKENS] ?: 220,
            hasApiKey = hasKey,
            maskedApiKey = prefs[Keys.API_KEY_MASKED].orEmpty(),
            apiConnectionStatus = prefs[Keys.API_CONNECTION_STATUS]?.let {
                runCatching { ApiConnectionStatus.valueOf(it) }.getOrNull()
            } ?: ApiConnectionStatus.UNCONFIGURED,
            apiLastLatencyMs = prefs[Keys.API_LAST_LATENCY_MS] ?: 0L,
            apiLastError = prefs[Keys.API_LAST_ERROR].orEmpty(),
            globalPersonality = prefs[Keys.GLOBAL_PERSONALITY]
                ?: "Helpful, warm, concise, and human-like",
            globalResponseStyle = prefs[Keys.GLOBAL_RESPONSE_STYLE]
                ?: "Natural & Conversational",
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            replyDelaySeconds = prefs[Keys.REPLY_DELAY_SECONDS] ?: 0,
            cooldownSeconds = prefs[Keys.COOLDOWN_SECONDS] ?: 60,
            replyToGroupChats = prefs[Keys.REPLY_TO_GROUP_CHATS] ?: false,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietHoursStartHour = prefs[Keys.QUIET_HOURS_START] ?: 23,
            quietHoursEndHour = prefs[Keys.QUIET_HOURS_END] ?: 7,
            quietHoursReplyText = prefs[Keys.QUIET_HOURS_REPLY]
                ?: "I'm currently away during quiet hours and will reply when I'm back.",
            dataSaverMode = prefs[Keys.DATA_SAVER_MODE] ?: false
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_REPLY_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveSafeguards(
        cooldownSeconds: Int,
        replyToGroupChats: Boolean,
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        quietHoursReplyText: String,
        dataSaverMode: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.COOLDOWN_SECONDS] = cooldownSeconds.coerceIn(0, 3600)
            prefs[Keys.REPLY_TO_GROUP_CHATS] = replyToGroupChats
            prefs[Keys.QUIET_HOURS_ENABLED] = quietHoursEnabled
            prefs[Keys.QUIET_HOURS_START] = quietHoursStartHour.coerceIn(0, 23)
            prefs[Keys.QUIET_HOURS_END] = quietHoursEndHour.coerceIn(0, 23)
            prefs[Keys.QUIET_HOURS_REPLY] = quietHoursReplyText.trim().ifBlank {
                "I'm currently away during quiet hours and will reply when I'm back."
            }
            prefs[Keys.DATA_SAVER_MODE] = dataSaverMode
        }
    }

    suspend fun saveAiConfig(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int,
        newPlainApiKey: String?,
        globalPersonality: String,
        globalResponseStyle: String,
        replyDelaySeconds: Int
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.API_PROVIDER] = provider.name
            prefs[Keys.API_BASE_URL] = baseUrl.trim()
            prefs[Keys.MODEL_NAME] = modelName.trim()
            prefs[Keys.TEMPERATURE] = temperature.coerceIn(0f, 1.5f)
            prefs[Keys.MAX_TOKENS] = maxTokens.coerceIn(32, 2048)
            prefs[Keys.GLOBAL_PERSONALITY] = globalPersonality.trim()
            prefs[Keys.GLOBAL_RESPONSE_STYLE] = globalResponseStyle.trim()
            prefs[Keys.REPLY_DELAY_SECONDS] = replyDelaySeconds.coerceIn(0, 15)

            if (!newPlainApiKey.isNullOrBlank()) {
                val encrypted = secretCipher.encrypt(newPlainApiKey.trim())
                if (encrypted != null) {
                    prefs[Keys.API_KEY_CIPHER] = encrypted.cipherTextBase64
                    prefs[Keys.API_KEY_IV] = encrypted.ivBase64
                    prefs[Keys.API_KEY_MASKED] = maskKey(newPlainApiKey.trim())
                }
            }
        }
    }

    suspend fun clearApiKey() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.API_KEY_CIPHER)
            prefs.remove(Keys.API_KEY_IV)
            prefs.remove(Keys.API_KEY_MASKED)
            prefs[Keys.API_CONNECTION_STATUS] = ApiConnectionStatus.UNCONFIGURED.name
            prefs[Keys.API_LAST_ERROR] = ""
            prefs[Keys.API_LAST_LATENCY_MS] = 0L
        }
    }

    suspend fun getDecryptedApiKey(): String? {
        val prefs = dataStore.data.first()
        val cipher = prefs[Keys.API_KEY_CIPHER].orEmpty()
        val iv = prefs[Keys.API_KEY_IV].orEmpty()
        if (cipher.isBlank() || iv.isBlank()) return null
        return secretCipher.decrypt(cipher, iv)
    }

    suspend fun updateConnectionStatus(
        status: ApiConnectionStatus,
        latencyMs: Long = 0L,
        errorMessage: String = ""
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.API_CONNECTION_STATUS] = status.name
            prefs[Keys.API_LAST_LATENCY_MS] = latencyMs
            prefs[Keys.API_LAST_ERROR] = errorMessage
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }
}
