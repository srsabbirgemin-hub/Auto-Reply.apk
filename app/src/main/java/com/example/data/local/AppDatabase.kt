package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class AppTypeConverters {
    @TypeConverter fun fromReplySupportStatus(value: ReplySupportStatus): String = value.name
    @TypeConverter fun toReplySupportStatus(value: String): ReplySupportStatus = ReplySupportStatus.valueOf(value)

    @TypeConverter fun fromCommandMatchType(value: CommandMatchType): String = value.name
    @TypeConverter fun toCommandMatchType(value: String): CommandMatchType = CommandMatchType.valueOf(value)

    @TypeConverter fun fromSmartActionType(value: SmartActionType): String = value.name
    @TypeConverter fun toSmartActionType(value: String): SmartActionType = SmartActionType.valueOf(value)

    @TypeConverter fun fromMessageDirection(value: MessageDirection): String = value.name
    @TypeConverter fun toMessageDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter fun fromReplyStatus(value: ReplyStatus): String = value.name
    @TypeConverter fun toReplyStatus(value: String): ReplyStatus = ReplyStatus.valueOf(value)

    @TypeConverter fun fromAiChatRole(value: AiChatRole): String = value.name
    @TypeConverter fun toAiChatRole(value: String): AiChatRole = AiChatRole.valueOf(value)
}

@Database(
    entities = [
        SupportedAppEntity::class,
        CategoryEntity::class,
        CommandEntity::class,
        SmartActionEntity::class,
        MessageLogEntity::class,
        ContactRuleEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun supportedAppDao(): SupportedAppDao
    abstract fun categoryDao(): CategoryDao
    abstract fun commandDao(): CommandDao
    abstract fun smartActionDao(): SmartActionDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun contactRuleDao(): ContactRuleDao
    abstract fun aiChatDao(): AiChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_ai_auto_reply.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object DatabaseSeeder {
    suspend fun seedDefaultsIfNeeded(db: AppDatabase, installedPackages: Set<String> = emptySet()) {
        val categoryDao = db.categoryDao()
        if (categoryDao.getCount() == 0) {
            categoryDao.insertAllIgnore(defaultCategories())
        }

        val commandDao = db.commandDao()
        if (commandDao.getCount() == 0) {
            commandDao.insertAllIgnore(defaultCommands())
        }

        val smartActionDao = db.smartActionDao()
        if (smartActionDao.getCount() == 0) {
            smartActionDao.insertAllIgnore(defaultSmartActions())
        }

        val appDao = db.supportedAppDao()
        if (appDao.getAllOnce().isEmpty()) {
            appDao.insertAllIgnore(defaultSupportedApps(installedPackages))
        }
    }

    fun defaultCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(
            id = 1,
            name = "General Conversation",
            instructions = "Reply naturally, politely, and briefly to general messages.",
            replyStyle = "Balanced & Natural",
            isEnabled = true,
            greeting = "",
            responseLanguage = "Auto-detect",
            isDefault = true,
            isCustom = false,
            colorHex = 0xFF06B6D4
        ),
        CategoryEntity(
            id = 2,
            name = "Friends",
            instructions = "Be warm, conversational, and relaxed without overdoing emojis.",
            replyStyle = "Casual & Friendly",
            isEnabled = true,
            greeting = "Hey!",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFF10B981
        ),
        CategoryEntity(
            id = 3,
            name = "Family",
            instructions = "Respond affectionately, clearly, and respectfully.",
            replyStyle = "Warm & Caring",
            isEnabled = true,
            greeting = "",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFFF59E0B
        ),
        CategoryEntity(
            id = 4,
            name = "Business",
            instructions = "Keep responses crisp, courteous, and professional. Never commit to meetings or prices automatically.",
            replyStyle = "Professional & Concise",
            isEnabled = true,
            greeting = "Hello,",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFF3B82F6
        ),
        CategoryEntity(
            id = 5,
            name = "Customer Support",
            instructions = "Acknowledge the inquiry politely, gather helpful details, and explain that a human representative will follow up.",
            replyStyle = "Helpful & Structured",
            isEnabled = true,
            greeting = "Thanks for reaching out!",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFF8B5CF6
        ),
        CategoryEntity(
            id = 6,
            name = "Study",
            instructions = "Stay focused, clear, and helpful for academic or study group discussions.",
            replyStyle = "Clear & Academic",
            isEnabled = true,
            greeting = "",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFFEC4899
        ),
        CategoryEntity(
            id = 7,
            name = "Gaming",
            instructions = "Keep replies fast, upbeat, and short for gaming teammates.",
            replyStyle = "Short & Upbeat",
            isEnabled = true,
            greeting = "",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFFEF4444
        ),
        CategoryEntity(
            id = 8,
            name = "Personal",
            instructions = "Reply thoughtfully while keeping personal boundaries and privacy intact.",
            replyStyle = "Thoughtful & Private",
            isEnabled = true,
            greeting = "",
            responseLanguage = "Auto-detect",
            isDefault = false,
            isCustom = false,
            colorHex = 0xFF14B8A6
        )
    )

    fun defaultCommands(): List<CommandEntity> = listOf(
        CommandEntity(
            id = 1,
            triggerText = "/hello",
            responseText = "Hello! How are you?",
            matchType = CommandMatchType.EXACT,
            isEnabled = true,
            priorityOverAi = true
        ),
        CommandEntity(
            id = 2,
            triggerText = "/location",
            responseText = "I will check and share my location shortly.",
            matchType = CommandMatchType.EXACT,
            isEnabled = true,
            priorityOverAi = true
        ),
        CommandEntity(
            id = 3,
            triggerText = "/busy",
            responseText = "I am currently busy. I will reply later.",
            matchType = CommandMatchType.EXACT,
            isEnabled = true,
            priorityOverAi = true
        )
    )

    fun defaultSmartActions(): List<SmartActionEntity> = listOf(
        SmartActionEntity(
            id = 1,
            actionType = SmartActionType.LOCATION,
            title = "Location Action",
            description = "Offer to share your approximate current location when someone asks where you are. Requires explicit consent and permission.",
            triggerKeywords = "where are you,your location,send location,where r u,location?",
            responseTemplate = "I am currently around {location}.",
            isEnabled = false,
            requiresConfirmation = true,
            isConsentGranted = false,
            sortOrder = 1
        ),
        SmartActionEntity(
            id = 2,
            actionType = SmartActionType.TIME,
            title = "Time Action",
            description = "Automatically reply with your current local time when asked.",
            triggerKeywords = "what time is it,current time,what's the time,time now",
            responseTemplate = "My current local time is {time}.",
            isEnabled = false,
            requiresConfirmation = false,
            isConsentGranted = true,
            sortOrder = 2
        ),
        SmartActionEntity(
            id = 3,
            actionType = SmartActionType.DATE,
            title = "Date Action",
            description = "Automatically reply with today's date when asked.",
            triggerKeywords = "what is today's date,what's the date,today's date,what day is it",
            responseTemplate = "Today's date is {date}.",
            isEnabled = false,
            requiresConfirmation = false,
            isConsentGranted = true,
            sortOrder = 3
        ),
        SmartActionEntity(
            id = 4,
            actionType = SmartActionType.BUSY_MODE,
            title = "Busy Mode",
            description = "Automatically inform incoming contacts that you are currently busy.",
            triggerKeywords = "*",
            responseTemplate = "I'm currently busy and away from my phone. I'll get back to you as soon as I can.",
            isEnabled = false,
            requiresConfirmation = false,
            isConsentGranted = true,
            sortOrder = 4
        )
    )

    fun defaultSupportedApps(installedPackages: Set<String>): List<SupportedAppEntity> = listOf(
        SupportedAppEntity(
            packageName = "com.whatsapp",
            displayName = "WhatsApp",
            isEnabled = true,
            isInstalled = installedPackages.contains("com.whatsapp"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Messaging"
        ),
        SupportedAppEntity(
            packageName = "com.whatsapp.w4b",
            displayName = "WhatsApp Business",
            isEnabled = false,
            isInstalled = installedPackages.contains("com.whatsapp.w4b"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Business"
        ),
        SupportedAppEntity(
            packageName = "com.facebook.orca",
            displayName = "Messenger",
            isEnabled = true,
            isInstalled = installedPackages.contains("com.facebook.orca"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Messaging"
        ),
        SupportedAppEntity(
            packageName = "com.imo.android.imoim",
            displayName = "IMO",
            isEnabled = false,
            isInstalled = installedPackages.contains("com.imo.android.imoim"),
            replySupportStatus = ReplySupportStatus.UNKNOWN_UNTIL_MESSAGE,
            categoryHint = "Video & Chat"
        ),
        SupportedAppEntity(
            packageName = "org.telegram.messenger",
            displayName = "Telegram",
            isEnabled = true,
            isInstalled = installedPackages.contains("org.telegram.messenger"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Messaging"
        ),
        SupportedAppEntity(
            packageName = "com.instagram.android",
            displayName = "Instagram",
            isEnabled = false,
            isInstalled = installedPackages.contains("com.instagram.android"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Social Direct"
        ),
        SupportedAppEntity(
            packageName = "com.google.android.apps.messaging",
            displayName = "Messages (SMS)",
            isEnabled = true,
            isInstalled = installedPackages.contains("com.google.android.apps.messaging"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "SMS / RCS"
        ),
        SupportedAppEntity(
            packageName = "org.thoughtcrime.securesms",
            displayName = "Signal",
            isEnabled = false,
            isInstalled = installedPackages.contains("org.thoughtcrime.securesms"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Encrypted Chat"
        ),
        SupportedAppEntity(
            packageName = "com.discord",
            displayName = "Discord",
            isEnabled = false,
            isInstalled = installedPackages.contains("com.discord"),
            replySupportStatus = ReplySupportStatus.SUPPORTED,
            categoryHint = "Communities"
        )
    )
}
