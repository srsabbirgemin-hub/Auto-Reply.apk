package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.PreferencesRepository
import com.example.data.network.AiApiClient
import com.example.domain.AppPackageInspector
import com.example.domain.AutoReplyEngine
import com.example.domain.LocationHelper
import com.example.domain.NotificationDeduplicator
import com.example.domain.SmartActionEvaluator
import com.example.service.ActiveReplyActionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.getInstance(application)
    val preferencesRepository = PreferencesRepository(application)
    val aiApiClient = AiApiClient()
    val appPackageInspector = AppPackageInspector(application)
    val locationHelper = LocationHelper(application)
    val deduplicator = NotificationDeduplicator()
    val replyRegistry = ActiveReplyActionRegistry()
    val smartActionEvaluator = SmartActionEvaluator(
        locationResolver = { locationHelper.getApproximateLocationSummary() }
    )

    val autoReplyEngine = AutoReplyEngine(
        context = application,
        supportedAppDao = database.supportedAppDao(),
        categoryDao = database.categoryDao(),
        commandDao = database.commandDao(),
        smartActionDao = database.smartActionDao(),
        messageLogDao = database.messageLogDao(),
        contactRuleDao = database.contactRuleDao(),
        aiChatDao = database.aiChatDao(),
        preferencesRepository = preferencesRepository,
        aiApiClient = aiApiClient,
        deduplicator = deduplicator,
        smartActionEvaluator = smartActionEvaluator,
        locationHelper = locationHelper,
        appPackageInspector = appPackageInspector,
        replyRegistry = replyRegistry
    )
}

class SmartAutoReplyApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val installed = container.appPackageInspector.getInstalledKnownPackages()
            DatabaseSeeder.seedDefaultsIfNeeded(container.database, installed)
            container.appPackageInspector.syncInstalledApps(container.database.supportedAppDao())
        }
    }
}
