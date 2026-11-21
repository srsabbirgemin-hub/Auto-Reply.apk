package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AutomationTab
import com.example.ui.screens.CategoriesHubScreen
import com.example.ui.screens.ConversationDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SandboxScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SupportedAppsScreen
import com.example.ui.theme.SmartAutoReplyTheme
import com.example.ui.theme.StatusEmerald
import com.example.viewmodel.AiChatViewModel
import com.example.viewmodel.AutomationViewModel
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.MessagesViewModel
import com.example.viewmodel.SandboxViewModel
import com.example.viewmodel.SettingsViewModel

object AppRoutes {
    const val HOME = "home"
    const val MESSAGES = "messages"
    const val AI_CHAT = "ai_chat"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"
    const val SUPPORTED_APPS = "supported_apps"
    const val PERMISSIONS = "permissions"
    const val SANDBOX = "sandbox"
    const val CONVERSATION_DETAIL = "conversation_detail/{conversationKey}"

    fun conversationDetail(conversationKey: String): String =
        "conversation_detail/${Uri.encode(conversationKey)}"
}

private enum class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(AppRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    MESSAGES(AppRoutes.MESSAGES, "Messages", Icons.Filled.Forum, Icons.Outlined.Forum),
    AI_CHAT(AppRoutes.AI_CHAT, "AI Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    CATEGORIES(AppRoutes.CATEGORIES, "Categories", Icons.Filled.Category, Icons.Outlined.Category),
    SETTINGS(AppRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun SmartAutoReplyApp(container: AppContainer) {
    val dashboardVm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val messagesVm: MessagesViewModel = viewModel(factory = MessagesViewModel.factory(container))
    val aiChatVm: AiChatViewModel = viewModel(factory = AiChatViewModel.factory(container))
    val automationVm: AutomationViewModel = viewModel(factory = AutomationViewModel.factory(container))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val sandboxVm: SandboxViewModel = viewModel(factory = SandboxViewModel.factory(container))

    val dashboardState by dashboardVm.uiState.collectAsStateWithLifecycle()
    val messagesState by messagesVm.uiState.collectAsStateWithLifecycle()
    val aiChatState by aiChatVm.uiState.collectAsStateWithLifecycle()
    val automationState by automationVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val sandboxState by sandboxVm.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardVm.refreshPermissions()
                settingsVm.refreshPermissionsAndApps()
                automationVm.refreshLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelChrome = currentRoute in TopLevelTab.entries.map { it.route }

    var selectedAutomationTab by rememberSaveable { mutableStateOf(AutomationTab.CATEGORIES) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settingsState.feedbackMessage) {
        val msg = settingsState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        settingsVm.clearFeedback()
    }

    LaunchedEffect(messagesState.statusBanner) {
        val msg = messagesState.statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        messagesVm.clearBanner()
    }

    SmartAutoReplyTheme(themeMode = settingsState.settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showTopLevelChrome && !useNavigationRail) {
                        NavigationBar {
                            TopLevelTab.entries.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopLevelChrome && useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            TopLevelTab.entries.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_${tab.route}")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (showTopLevelChrome) Modifier.statusBarsPadding() else Modifier)
                    ) {
                        // Privacy & transparency banner when automatic replies are actively enabled
                        AnimatedVisibility(visible = showTopLevelChrome && dashboardState.settings.isAutoReplyEnabled) {
                            Surface(
                                color = StatusEmerald.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(StatusEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Automatic AI Replies Enabled • ${dashboardState.enabledAppsCount} apps active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusEmerald
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.HOME,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(AppRoutes.HOME) {
                                HomeScreen(
                                    state = dashboardState,
                                    loadAppIcon = settingsVm::loadAppIcon,
                                    onToggleAutoReply = dashboardVm::setAutoReplyEnabled,
                                    onToggleBusyMode = dashboardVm::setBusyModeEnabled,
                                    onOpenSupportedApps = { navController.navigate(AppRoutes.SUPPORTED_APPS) },
                                    onOpenPermissions = { navController.navigate(AppRoutes.PERMISSIONS) },
                                    onOpenMessages = {
                                        navController.navigate(AppRoutes.MESSAGES) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenCommands = {
                                        selectedAutomationTab = AutomationTab.COMMANDS
                                        navController.navigate(AppRoutes.CATEGORIES) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenSmartActions = {
                                        selectedAutomationTab = AutomationTab.SMART_ACTIONS
                                        navController.navigate(AppRoutes.CATEGORIES) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenSandbox = { navController.navigate(AppRoutes.SANDBOX) },
                                    onOpenAiSettings = {
                                        navController.navigate(AppRoutes.SETTINGS) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenConversation = { key ->
                                        navController.navigate(AppRoutes.conversationDetail(key))
                                    }
                                )
                            }

                            composable(AppRoutes.MESSAGES) {
                                MessagesScreen(
                                    state = messagesState,
                                    loadAppIcon = settingsVm::loadAppIcon,
                                    onSearchChange = messagesVm::setSearchQuery,
                                    onSelectAppFilter = messagesVm::setSelectedAppPackage,
                                    onSelectFilterMode = messagesVm::setFilterMode,
                                    onOpenConversation = { key ->
                                        navController.navigate(AppRoutes.conversationDetail(key))
                                    }
                                )
                            }

                            composable(AppRoutes.AI_CHAT) {
                                AiChatScreen(
                                    state = aiChatState,
                                    onSendMessage = aiChatVm::sendMessage,
                                    onCreateSession = { aiChatVm.createNewSession() },
                                    onSelectSession = aiChatVm::selectSession,
                                    onDeleteSession = aiChatVm::deleteSession,
                                    onUpdateSessionConfig = aiChatVm::updateSessionConfig,
                                    onToggleAutoReplyContext = aiChatVm::toggleUseAsAutoReplyContext,
                                    onClearError = aiChatVm::clearError
                                )
                            }

                            composable(AppRoutes.CATEGORIES) {
                                CategoriesHubScreen(
                                    state = automationState,
                                    selectedTab = selectedAutomationTab,
                                    onSelectTab = { selectedAutomationTab = it },
                                    onToggleCategory = automationVm::toggleCategory,
                                    onSetDefaultCategory = automationVm::setDefaultCategory,
                                    onSaveCategory = automationVm::saveCategory,
                                    onDeleteCategory = automationVm::deleteCategory,
                                    onToggleCommand = automationVm::toggleCommand,
                                    onSaveCommand = automationVm::saveCommand,
                                    onDeleteCommand = automationVm::deleteCommand,
                                    onUpdateSmartAction = automationVm::updateSmartAction,
                                    onEnableLocationConsent = automationVm::enableLocationActionWithConsent,
                                    onDisableLocation = automationVm::disableLocationAction,
                                    onAddCustomAction = automationVm::addCustomSmartAction,
                                    onDeleteSmartAction = automationVm::deleteSmartAction,
                                    onRefreshLocationPermission = automationVm::refreshLocationPermission
                                )
                            }

                            composable(AppRoutes.SETTINGS) {
                                SettingsScreen(
                                    state = settingsState,
                                    onSaveAiConfig = settingsVm::saveAiConfiguration,
                                    onTestConnection = settingsVm::testConnection,
                                    onFetchModels = settingsVm::fetchModels,
                                    onClearApiKey = settingsVm::clearSavedApiKey,
                                    onSaveSafeguards = settingsVm::saveSafeguards,
                                    onSetThemeMode = settingsVm::setThemeMode,
                                    onOpenSupportedApps = { navController.navigate(AppRoutes.SUPPORTED_APPS) },
                                    onOpenPermissions = { navController.navigate(AppRoutes.PERMISSIONS) },
                                    onOpenSandbox = { navController.navigate(AppRoutes.SANDBOX) },
                                    onDeleteAllHistory = settingsVm::deleteAllLocalHistory
                                )
                            }

                            composable(AppRoutes.SUPPORTED_APPS) {
                                SupportedAppsScreen(
                                    apps = settingsState.supportedApps,
                                    isNotificationAccessGranted = settingsState.isNotificationAccessGranted,
                                    loadAppIcon = settingsVm::loadAppIcon,
                                    onToggleApp = settingsVm::toggleSupportedApp,
                                    onRefreshApps = settingsVm::refreshPermissionsAndApps,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.PERMISSIONS) {
                                PermissionsScreen(
                                    isNotificationAccessGranted = settingsState.isNotificationAccessGranted,
                                    isLocationPermissionGranted = settingsState.isLocationPermissionGranted,
                                    isBatteryOptimizationIgnored = settingsState.isBatteryOptimizationIgnored,
                                    onRefreshPermissions = {
                                        dashboardVm.refreshPermissions()
                                        settingsVm.refreshPermissionsAndApps()
                                        automationVm.refreshLocationPermission()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.SANDBOX) {
                                SandboxScreen(
                                    state = sandboxState,
                                    onRunSimulation = sandboxVm::runSimulation,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = AppRoutes.CONVERSATION_DETAIL,
                                arguments = listOf(
                                    navArgument("conversationKey") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val encodedKey = backStackEntry.arguments?.getString("conversationKey").orEmpty()
                                val conversationKey = Uri.decode(encodedKey)
                                val convoLogs = messagesState.allLogs.filter { it.conversationKey == conversationKey }
                                val rule = messagesState.contactRules[conversationKey]
                                val canReply = messagesState.replyableKeys.contains(conversationKey)

                                ConversationDetailScreen(
                                    conversationKey = conversationKey,
                                    logs = convoLogs,
                                    categories = messagesState.categories,
                                    contactRule = rule,
                                    canReplyInline = canReply,
                                    isDrafting = messagesState.isDrafting,
                                    onBack = { navController.popBackStack() },
                                    onDraftAiReply = { sender, appName, incoming, toneOverride, cb ->
                                        messagesVm.draftAiReply(
                                            conversationKey = conversationKey,
                                            senderName = sender,
                                            appDisplayName = appName,
                                            latestIncomingText = incoming,
                                            toneOverride = toneOverride,
                                            onDraftReady = cb
                                        )
                                    },
                                    onSendReply = { pkg, appName, sender, text, sourceId ->
                                        messagesVm.sendManualReply(
                                            conversationKey = conversationKey,
                                            packageName = pkg,
                                            appDisplayName = appName,
                                            senderName = sender,
                                            replyText = text,
                                            sourceMessageId = sourceId
                                        )
                                    },
                                    onSaveContactRule = { sender, pkg, catId, instructions, useMemory, isMuted ->
                                        messagesVm.saveContactRule(
                                            conversationKey = conversationKey,
                                            senderName = sender,
                                            packageName = pkg,
                                            assignedCategoryId = catId,
                                            customInstructions = instructions,
                                            useContextualMemory = useMemory,
                                            isAutoReplyMuted = isMuted
                                        )
                                    },
                                    onDeleteConversation = {
                                        messagesVm.deleteConversation(conversationKey)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
