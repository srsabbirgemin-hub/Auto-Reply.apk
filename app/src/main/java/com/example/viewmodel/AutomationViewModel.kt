package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.CategoryEntity
import com.example.data.local.CommandEntity
import com.example.data.local.CommandMatchType
import com.example.data.local.SmartActionEntity
import com.example.data.local.SmartActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val commands: List<CommandEntity> = emptyList(),
    val smartActions: List<SmartActionEntity> = emptyList(),
    val hasLocationPermission: Boolean = false
)

class AutomationViewModel(private val container: AppContainer) : ViewModel() {

    private val locationPermFlow = MutableStateFlow(container.locationHelper.hasCoarseLocationPermission())

    val uiState: StateFlow<AutomationUiState> = combine(
        container.database.categoryDao().observeAll(),
        container.database.commandDao().observeAll(),
        container.database.smartActionDao().observeAll(),
        locationPermFlow
    ) { categories, commands, actions, hasLocation ->
        AutomationUiState(
            categories = categories,
            commands = commands,
            smartActions = actions,
            hasLocationPermission = hasLocation
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AutomationUiState()
    )

    fun refreshLocationPermission() {
        locationPermFlow.value = container.locationHelper.hasCoarseLocationPermission()
    }

    // Categories
    fun toggleCategory(category: CategoryEntity, enabled: Boolean) {
        viewModelScope.launch {
            container.database.categoryDao().update(category.copy(isEnabled = enabled))
        }
    }

    fun setDefaultCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val all = uiState.value.categories
            all.forEach { item ->
                val shouldBeDefault = item.id == category.id
                if (item.isDefault != shouldBeDefault) {
                    container.database.categoryDao().update(item.copy(isDefault = shouldBeDefault))
                }
            }
        }
    }

    fun saveCategory(
        existing: CategoryEntity?,
        name: String,
        instructions: String,
        replyStyle: String,
        greeting: String,
        responseLanguage: String,
        colorHex: Long
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                container.database.categoryDao().insert(
                    CategoryEntity(
                        name = name.trim(),
                        instructions = instructions.trim(),
                        replyStyle = replyStyle.trim().ifBlank { "Balanced & Natural" },
                        isEnabled = true,
                        greeting = greeting.trim(),
                        responseLanguage = responseLanguage.trim().ifBlank { "Auto-detect" },
                        isDefault = false,
                        isCustom = true,
                        colorHex = colorHex
                    )
                )
            } else {
                container.database.categoryDao().update(
                    existing.copy(
                        name = name.trim(),
                        instructions = instructions.trim(),
                        replyStyle = replyStyle.trim(),
                        greeting = greeting.trim(),
                        responseLanguage = responseLanguage.trim().ifBlank { "Auto-detect" },
                        colorHex = colorHex
                    )
                )
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (!category.isCustom) return
        viewModelScope.launch {
            container.database.categoryDao().delete(category)
        }
    }

    // Commands
    fun toggleCommand(command: CommandEntity, enabled: Boolean) {
        viewModelScope.launch {
            container.database.commandDao().update(command.copy(isEnabled = enabled))
        }
    }

    fun saveCommand(
        existing: CommandEntity?,
        triggerText: String,
        responseText: String,
        matchType: CommandMatchType,
        priorityOverAi: Boolean
    ) {
        if (triggerText.isBlank() || responseText.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                container.database.commandDao().insert(
                    CommandEntity(
                        triggerText = triggerText.trim(),
                        responseText = responseText.trim(),
                        matchType = matchType,
                        isEnabled = true,
                        priorityOverAi = priorityOverAi
                    )
                )
            } else {
                container.database.commandDao().update(
                    existing.copy(
                        triggerText = triggerText.trim(),
                        responseText = responseText.trim(),
                        matchType = matchType,
                        priorityOverAi = priorityOverAi
                    )
                )
            }
        }
    }

    fun deleteCommand(command: CommandEntity) {
        viewModelScope.launch {
            container.database.commandDao().delete(command)
        }
    }

    // Smart Actions
    fun updateSmartAction(action: SmartActionEntity) {
        viewModelScope.launch {
            container.database.smartActionDao().update(action)
        }
    }

    fun enableLocationActionWithConsent(
        action: SmartActionEntity,
        requiresConfirmation: Boolean
    ) {
        viewModelScope.launch {
            container.database.smartActionDao().update(
                action.copy(
                    isEnabled = true,
                    isConsentGranted = true,
                    requiresConfirmation = requiresConfirmation
                )
            )
        }
    }

    fun disableLocationAction(action: SmartActionEntity) {
        viewModelScope.launch {
            container.database.smartActionDao().update(
                action.copy(
                    isEnabled = false,
                    isConsentGranted = false
                )
            )
        }
    }

    fun addCustomSmartAction(
        title: String,
        description: String,
        triggerKeywords: String,
        responseTemplate: String,
        requiresConfirmation: Boolean
    ) {
        if (title.isBlank() || triggerKeywords.isBlank() || responseTemplate.isBlank()) return
        viewModelScope.launch {
            container.database.smartActionDao().insert(
                SmartActionEntity(
                    actionType = SmartActionType.CUSTOM,
                    title = title.trim(),
                    description = description.trim().ifBlank { "User-defined smart action" },
                    triggerKeywords = triggerKeywords.trim(),
                    responseTemplate = responseTemplate.trim(),
                    isEnabled = true,
                    requiresConfirmation = requiresConfirmation,
                    isConsentGranted = true,
                    sortOrder = 10
                )
            )
        }
    }

    fun deleteSmartAction(action: SmartActionEntity) {
        if (action.actionType != SmartActionType.CUSTOM) return
        viewModelScope.launch {
            container.database.smartActionDao().delete(action)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AutomationViewModel(container) as T
                }
            }
    }
}
