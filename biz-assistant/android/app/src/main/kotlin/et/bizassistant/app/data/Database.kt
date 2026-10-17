package et.bizassistant.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM users WHERE id = 1")
    fun user(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = 1")
    suspend fun userNow(): UserEntity?

    @Upsert
    suspend fun saveUser(user: UserEntity)

    @Query("SELECT * FROM business_profiles WHERE id = 1")
    fun profile(): Flow<BusinessProfileEntity?>

    @Upsert
    suspend fun saveProfile(profile: BusinessProfileEntity)

    @Query("SELECT * FROM language_preferences WHERE id = 1")
    fun language(): Flow<LanguagePreferenceEntity?>

    @Upsert
    suspend fun saveLanguage(pref: LanguagePreferenceEntity)

    @Query("SELECT * FROM usage_quotas WHERE id = 1")
    fun quota(): Flow<UsageQuotaEntity?>

    @Upsert
    suspend fun saveQuota(quota: UsageQuotaEntity)

    @Query("SELECT * FROM subscriptions WHERE id = 1")
    fun subscription(): Flow<SubscriptionEntity?>

    @Upsert
    suspend fun saveSubscription(subscription: SubscriptionEntity)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM business_records ORDER BY date DESC, id DESC")
    fun all(): Flow<List<BusinessRecordEntity>>

    @Query("SELECT * FROM business_records WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<BusinessRecordEntity>

    @Insert
    suspend fun insert(record: BusinessRecordEntity): Long

    @Query("DELETE FROM business_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM business_records WHERE date < :before")
    suspend fun deleteOlderThan(before: String): Int

    @Query("DELETE FROM business_records")
    suspend fun deleteAll()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM generated_documents ORDER BY createdAt DESC")
    fun all(): Flow<List<GeneratedDocumentEntity>>

    @Query("SELECT * FROM generated_documents WHERE id = :id")
    suspend fun get(id: Long): GeneratedDocumentEntity?

    @Insert
    suspend fun insert(doc: GeneratedDocumentEntity): Long

    @Update
    suspend fun update(doc: GeneratedDocumentEntity)

    @Query("DELETE FROM generated_documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM generated_documents")
    suspend fun deleteAll()

    @Query("SELECT * FROM task_templates ORDER BY createdAt DESC")
    fun templates(): Flow<List<TaskTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TaskTemplateEntity): Long

    @Query("DELETE FROM task_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM task_templates")
    suspend fun deleteAllTemplates()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun all(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id")
    suspend fun messages(conversationId: Long): List<MessageEntity>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Database(
    entities = [
        UserEntity::class, BusinessProfileEntity::class, TaskTemplateEntity::class, ConversationEntity::class,
        MessageEntity::class, GeneratedDocumentEntity::class, BusinessRecordEntity::class,
        LanguagePreferenceEntity::class, UsageQuotaEntity::class, SubscriptionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao
    abstract fun records(): RecordDao
    abstract fun documents(): DocumentDao
    abstract fun conversations(): ConversationDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "business-assistant.db").build()
    }
}
