package et.bizassistant.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The single local user of this device. Holds consent and privacy choices. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long = 1,
    val onboardingDone: Boolean = false,
    val aiConsentGranted: Boolean = false,
    val aiConsentDecidedAt: Long? = null,
    /** 0 = keep until deleted by the user. */
    val recordRetentionMonths: Int = 12,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey val id: Long = 1,
    val businessName: String,
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val tin: String = "",
    val currency: String = "ETB",
)

/** A reusable manual template the user saved, e.g. their standard thank-you message. */
@Entity(tableName = "task_templates")
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskType: String,
    val language: String,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskType: String,
    val language: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** "user", "assistant" or "system" (fallback notices). */
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "generated_documents", indices = [Index("createdAt")])
data class GeneratedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long? = null,
    /** TaskType name, e.g. INVOICE or CUSTOMER_MESSAGE. */
    val kind: String,
    val title: String,
    val body: String,
    val language: String,
    /** DraftSource name: AI, TEMPLATE or CALCULATED. */
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "business_records", indices = [Index("date")])
data class BusinessRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RecordType name: SALE or EXPENSE. */
    val type: String,
    /** ISO date, yyyy-MM-dd. */
    val date: String,
    val description: String,
    val category: String = "",
    /** Decimal quantity stored as text to avoid floating point. */
    val quantity: String = "1",
    val unitAmountMinor: Long,
    val customerOrSupplier: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "language_preferences")
data class LanguagePreferenceEntity(
    @PrimaryKey val id: Long = 1,
    val appLanguage: String = "en",
    /** Default language for generated documents; can differ from the app language. */
    val documentLanguage: String = "en",
)

/** Last allowance reported by the backend, cached for offline display. */
@Entity(tableName = "usage_quotas")
data class UsageQuotaEntity(
    @PrimaryKey val id: Long = 1,
    val plan: String,
    val used: Int,
    val monthlyLimit: Int,
    val bonusRemaining: Int,
    val resetsOn: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** Subscription state as confirmed by the backend. Never set from the client alone. */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: Long = 1,
    val plan: String = "free",
    /** "none", "active" or "expired". */
    val status: String = "none",
    val renewsOn: String? = null,
)
