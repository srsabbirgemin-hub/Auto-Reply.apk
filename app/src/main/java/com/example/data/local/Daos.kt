package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportedAppDao {
    @Query("SELECT * FROM supported_apps ORDER BY isInstalled DESC, displayName ASC")
    fun observeAll(): Flow<List<SupportedAppEntity>>

    @Query("SELECT * FROM supported_apps")
    suspend fun getAllOnce(): List<SupportedAppEntity>

    @Query("SELECT * FROM supported_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): SupportedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: SupportedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(apps: List<SupportedAppEntity>)

    @Query("UPDATE supported_apps SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE supported_apps SET replySupportStatus = :status, lastDetectedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateReplySupport(packageName: String, status: ReplySupportStatus, timestamp: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isCustom ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    @Query("SELECT * FROM categories WHERE isEnabled = 1 ORDER BY isCustom ASC, id ASC")
    suspend fun getEnabledOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultCategory(): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands ORDER BY priorityOverAi DESC, id ASC")
    fun observeAll(): Flow<List<CommandEntity>>

    @Query("SELECT COUNT(*) FROM commands")
    suspend fun getCount(): Int

    @Query("SELECT * FROM commands WHERE isEnabled = 1 ORDER BY priorityOverAi DESC, id ASC")
    suspend fun getEnabledOnce(): List<CommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: CommandEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(commands: List<CommandEntity>)

    @Update
    suspend fun update(command: CommandEntity)

    @Delete
    suspend fun delete(command: CommandEntity)
}

@Dao
interface SmartActionDao {
    @Query("SELECT * FROM smart_actions ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SmartActionEntity>>

    @Query("SELECT COUNT(*) FROM smart_actions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM smart_actions WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabledOnce(): List<SmartActionEntity>

    @Query("SELECT * FROM smart_actions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmartActionEntity?

    @Query("SELECT * FROM smart_actions WHERE actionType = :type LIMIT 1")
    suspend fun getByType(type: SmartActionType): SmartActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: SmartActionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(actions: List<SmartActionEntity>)

    @Update
    suspend fun update(action: SmartActionEntity)

    @Delete
    suspend fun delete(action: SmartActionEntity)
}

@Dao
interface MessageLogDao {
    @Query("SELECT * FROM message_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs WHERE conversationKey = :conversationKey ORDER BY timestamp ASC")
    fun observeConversation(conversationKey: String): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs WHERE conversationKey = :conversationKey ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentForConversation(conversationKey: String, limit: Int): List<MessageLogEntity>

    @Query("SELECT * FROM message_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageLogEntity?

    @Query("SELECT COUNT(*) FROM message_logs WHERE direction = 'INCOMING'")
    fun observeTotalReceivedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM message_logs WHERE direction = 'OUTGOING' AND replyStatus IN ('SENT', 'COMMAND_SENT', 'SMART_ACTION_SENT', 'QUIET_HOURS_SENT', 'MANUAL_SENT')")
    fun observeTotalRepliesCount(): Flow<Int>

    @Query("SELECT MAX(timestamp) FROM message_logs WHERE conversationKey = :conversationKey AND direction = 'OUTGOING'")
    suspend fun getLastOutgoingTimestamp(conversationKey: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM message_logs WHERE dedupHash = :hash AND timestamp >= :sinceTimestamp)")
    suspend fun hasRecentDedupHash(hash: String, sinceTimestamp: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MessageLogEntity): Long

    @Update
    suspend fun update(log: MessageLogEntity)

    @Query("DELETE FROM message_logs WHERE conversationKey = :conversationKey")
    suspend fun deleteConversation(conversationKey: String)

    @Query("DELETE FROM message_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM message_logs")
    suspend fun deleteAll()
}

@Dao
interface ContactRuleDao {
    @Query("SELECT * FROM contact_rules ORDER BY lastUpdatedAt DESC")
    fun observeAll(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules WHERE conversationKey = :conversationKey LIMIT 1")
    fun observeForContact(conversationKey: String): Flow<ContactRuleEntity?>

    @Query("SELECT * FROM contact_rules WHERE conversationKey = :conversationKey LIMIT 1")
    suspend fun getForContact(conversationKey: String): ContactRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ContactRuleEntity)

    @Query("DELETE FROM contact_rules")
    suspend fun deleteAll()
}

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE useAsAutoReplyContext = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveContextSession(): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: Long, limit: Int): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: AiChatSessionEntity)

    @Query("UPDATE ai_chat_sessions SET useAsAutoReplyContext = (id = :sessionId)")
    suspend fun setExclusiveContextSession(sessionId: Long)

    @Query("UPDATE ai_chat_sessions SET useAsAutoReplyContext = 0")
    suspend fun clearAllContextSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM ai_chat_sessions")
    suspend fun deleteAllSessions()
}
