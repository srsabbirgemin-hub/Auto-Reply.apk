package com.example.viewmodel

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AiProviderType
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.AppSettings
import com.example.data.local.SupportedAppEntity
import com.example.data.local.ThemeMode
import com.example.data.network.AiApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val supportedApps: List<SupportedAppEntity> = emptyList(),
    val discoveredModels: List<String> = emptyList(),
    val isTestingConnection: Boolean = false,
    val isFetchingModels: Boolean = false,
    val feedbackMessage: String? = null,
    val isNotificationAccessGranted: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val discoveredModels = MutableStateFlow<List<String>>(emptyList())
    private val isTesting = MutableStateFlow(false)
    private val isFetchingModels = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val perms = MutableStateFlow(readPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        container.preferencesRepository.settingsFlow,
        container.database.supportedAppDao().observeAll(),
        combine(discoveredModels, isTesting, isFetchingModels) { m, t, f -> Triple(m, t, f) },
        feedback,
        perms
    ) { settings, apps, (models, testing, fetching), msg, permTriple ->
        SettingsUiState(
            settings = settings,
            supportedApps = apps,
            discoveredModels = models,
            isTestingConnection = testing,
            isFetchingModels = fetching,
            feedbackMessage = msg,
            isNotificationAccessGranted = permTriple.first,
            isLocationPermissionGranted = permTriple.second,
            isBatteryOptimizationIgnored = permTriple.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshPermissionsAndApps() {
        perms.value = readPermissions()
        viewModelScope.launch {
            container.appPackageInspector.syncInstalledApps(container.database.supportedAppDao())
        }
    }

    fun loadAppIcon(packageName: String): Drawable? =
        container.appPackageInspector.loadAppIcon(packageName)

    fun toggleSupportedApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            container.database.supportedAppDao().setEnabled(packageName, enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.preferencesRepository.setThemeMode(mode)
        }
    }

    fun saveSafeguards(
        cooldownSeconds: Int,
        replyToGroupChats: Boolean,
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        quietHoursReplyText: String,
        dataSaverMode: Boolean
    ) {
        viewModelScope.launch {
            container.preferencesRepository.saveSafeguards(
                cooldownSeconds = cooldownSeconds,
                replyToGroupChats = replyToGroupChats,
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStartHour = quietHoursStartHour,
                quietHoursEndHour = quietHoursEndHour,
                quietHoursReplyText = quietHoursReplyText,
                dataSaverMode = dataSaverMode
            )
            feedback.value = "Safeguards, Data Saver & Quiet Hours updated."
        }
    }

    fun saveAiConfiguration(
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
        viewModelScope.launch {
            container.preferencesRepository.saveAiConfig(
                provider = provider,
                baseUrl = baseUrl,
                modelName = modelName,
                temperature = temperature,
                maxTokens = maxTokens,
                newPlainApiKey = newPlainApiKey,
                globalPersonality = globalPersonality,
                globalResponseStyle = globalResponseStyle,
                replyDelaySeconds = replyDelaySeconds
            )
            feedback.value = "Settings saved locally with Keystore encryption."
        }
    }

    fun clearSavedApiKey() {
        viewModelScope.launch {
            container.preferencesRepository.clearApiKey()
            discoveredModels.value = emptyList()
            feedback.value = "Stored API key removed."
        }
    }

    fun testConnection(
        provider: AiProviderType,
        baseUrl: String,
        modelName: String,
        unsavedPlainApiKey: String?,
        temperature: Float,
        maxTokens: Int,
        globalPersonality: String,
        globalResponseStyle: String,
        replyDelaySeconds: Int
    ) {
        viewModelScope.launch {
            isTesting.value = true
            try {
                container.preferencesRepository.saveAiConfig(
                    provider = provider,
                    baseUrl = baseUrl,
                    modelName = modelName,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    newPlainApiKey = unsavedPlainApiKey,
                    globalPersonality = globalPersonality,
                    globalResponseStyle = globalResponseStyle,
                    replyDelaySeconds = replyDelaySeconds
                )

                val effectiveKey = unsavedPlainApiKey?.takeIf { it.isNotBlank() }
                    ?: container.preferencesRepository.getDecryptedApiKey().orEmpty()

                if (effectiveKey.isBlank()) {
                    container.preferencesRepository.updateConnectionStatus(
                        ApiConnectionStatus.UNCONFIGURED,
                        0L,
                        "No API key provided."
                    )
                    feedback.value = "Enter an API key first."
                    return@launch
                }

                when (val res = container.aiApiClient.testConnection(baseUrl, effectiveKey, modelName)) {
                    is AiApiResult.Success -> {
                        container.preferencesRepository.updateConnectionStatus(
                            ApiConnectionStatus.CONNECTED,
                            res.latencyMs,
                            ""
                        )
                        feedback.value = "Connection verified in ${res.latencyMs} ms."
                    }
                    is AiApiResult.Error -> {
                        container.preferencesRepository.updateConnectionStatus(
                            ApiConnectionStatus.ERROR,
                            0L,
                            res.message
                        )
                        feedback.value = "Connection failed: ${res.message}"
                    }
                }
            } finally {
                isTesting.value = false
            }
        }
    }

    fun fetchModels(baseUrl: String, unsavedPlainApiKey: String?) {
        viewModelScope.launch {
            isFetchingModels.value = true
            try {
                val effectiveKey = unsavedPlainApiKey?.takeIf { it.isNotBlank() }
                    ?: container.preferencesRepository.getDecryptedApiKey().orEmpty()
                if (effectiveKey.isBlank()) {
                    feedback.value = "Enter an API key to fetch available models."
                    return@launch
                }

                when (val res = container.aiApiClient.fetchAvailableModels(baseUrl, effectiveKey)) {
                    is AiApiResult.Success -> {
                        discoveredModels.value = res.data
                        feedback.value = if (res.data.isEmpty()) {
                            "No models returned by provider."
                        } else {
                            "Loaded ${res.data.size} models from provider."
                        }
                    }
                    is AiApiResult.Error -> {
                        feedback.value = "Failed to fetch models: ${res.message}"
                    }
                }
            } finally {
                isFetchingModels.value = false
            }
        }
    }

    fun deleteAllLocalHistory() {
        viewModelScope.launch {
            container.database.messageLogDao().deleteAll()
            container.database.contactRuleDao().deleteAll()
            container.database.aiChatDao().deleteAllMessages()
            container.database.aiChatDao().deleteAllSessions()
            container.replyRegistry.clear()
            feedback.value = "All local message logs, contact rules, and AI chat history deleted."
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    private fun readPermissions(): Triple<Boolean, Boolean, Boolean> {
        return Triple(
            container.appPackageInspector.isNotificationListenerEnabled(),
            container.locationHelper.hasCoarseLocationPermission(),
            container.appPackageInspector.isIgnoringBatteryOptimizations()
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(container) as T
                }
            }
    }
}
