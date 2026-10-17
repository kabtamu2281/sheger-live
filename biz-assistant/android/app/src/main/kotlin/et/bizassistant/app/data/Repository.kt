package et.bizassistant.app.data

import et.bizassistant.core.AppLanguage
import et.bizassistant.core.BusinessProfile
import et.bizassistant.core.BusinessRecord
import et.bizassistant.core.ConsentState
import et.bizassistant.core.Money
import et.bizassistant.core.PlanId
import et.bizassistant.core.RecordType
import et.bizassistant.core.SearchableDocument
import et.bizassistant.core.UsageQuota
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Repository(private val db: AppDatabase) {
    private val settings = db.settings()
    private val records = db.records()
    private val documents = db.documents()
    private val conversations = db.conversations()

    val user: Flow<UserEntity> = settings.user().map { it ?: UserEntity() }
    val profile: Flow<BusinessProfile?> = settings.profile().map { it?.toCore() }
    val language: Flow<LanguagePreferenceEntity> = settings.language().map { it ?: LanguagePreferenceEntity() }
    val quota: Flow<UsageQuota?> = settings.quota().map { it?.toCore() }
    val subscription: Flow<SubscriptionEntity> = settings.subscription().map { it ?: SubscriptionEntity() }
    val records: Flow<List<BusinessRecord>> = this.records.all().map { list -> list.map { it.toCore() } }
    val documentList: Flow<List<GeneratedDocumentEntity>> = documents.all()
    val templates: Flow<List<TaskTemplateEntity>> = documents.templates()
    val conversationList: Flow<List<ConversationEntity>> = conversations.all()

    suspend fun saveProfile(p: BusinessProfile) = settings.saveProfile(
        BusinessProfileEntity(businessName = p.businessName, ownerName = p.ownerName, phone = p.phone, address = p.address, tin = p.tin, currency = p.currency),
    )

    suspend fun setLanguage(app: AppLanguage, document: AppLanguage = app) =
        settings.saveLanguage(LanguagePreferenceEntity(appLanguage = app.tag, documentLanguage = document.tag))

    suspend fun updateUser(change: (UserEntity) -> UserEntity) =
        settings.saveUser(change(settings.userNow() ?: UserEntity()))

    suspend fun setConsent(granted: Boolean) = updateUser {
        it.copy(aiConsentGranted = granted, aiConsentDecidedAt = System.currentTimeMillis())
    }

    suspend fun saveQuota(q: UsageQuota) = settings.saveQuota(
        UsageQuotaEntity(plan = q.plan.name.lowercase(), used = q.used, monthlyLimit = q.limit, bonusRemaining = q.bonusRemaining, resetsOn = q.resetsOn.toString()),
    )

    suspend fun saveSubscription(plan: PlanId, active: Boolean) =
        settings.saveSubscription(SubscriptionEntity(plan = plan.name.lowercase(), status = if (active) "active" else "none"))

    suspend fun addRecord(r: BusinessRecord): Long = records.insert(
        BusinessRecordEntity(
            type = r.type.name, date = r.date.toString(), description = r.description.trim(), category = r.category.trim(),
            quantity = r.quantity.toPlainString(), unitAmountMinor = r.unitAmount.minor,
            customerOrSupplier = r.customerOrSupplier.trim(), note = r.note.trim(),
        ),
    )

    suspend fun deleteRecord(id: Long) = records.delete(id)

    suspend fun recordsBetween(from: LocalDate, to: LocalDate): List<BusinessRecord> =
        records.between(from.toString(), to.toString()).map { it.toCore() }

    /** Deletes records older than the user's retention setting. Returns how many were removed. */
    suspend fun applyRetention(today: LocalDate = LocalDate.now()): Int {
        val months = (settings.userNow() ?: UserEntity()).recordRetentionMonths
        if (months <= 0) return 0
        return records.deleteOlderThan(today.minusMonths(months.toLong()).toString())
    }

    suspend fun startConversation(taskType: String, language: AppLanguage, title: String): Long =
        conversations.insert(ConversationEntity(taskType = taskType, language = language.tag, title = title))

    suspend fun addMessage(conversationId: Long, role: String, text: String) =
        conversations.insertMessage(MessageEntity(conversationId = conversationId, role = role, text = text))

    suspend fun messages(conversationId: Long) = conversations.messages(conversationId)

    suspend fun deleteConversation(id: Long) = conversations.delete(id)

    suspend fun saveDocument(doc: GeneratedDocumentEntity): Long =
        if (doc.id == 0L) documents.insert(doc)
        else { documents.update(doc.copy(updatedAt = System.currentTimeMillis())); doc.id }

    suspend fun document(id: Long) = documents.get(id)

    suspend fun deleteDocument(id: Long) = documents.delete(id)

    suspend fun saveTemplate(t: TaskTemplateEntity) = documents.insertTemplate(t)

    suspend fun deleteTemplate(id: Long) = documents.deleteTemplate(id)

    suspend fun deleteAllConversations() = conversations.deleteAll()

    suspend fun deleteAllDocuments() {
        documents.deleteAll()
        documents.deleteAllTemplates()
    }

    suspend fun deleteAllRecords() = records.deleteAll()

    /** Wipes every table: records, documents, conversations, profile, preferences and cached quota. */
    suspend fun deleteEverything() = db.clearAllTables()

    companion object {
        fun GeneratedDocumentEntity.toSearchable() = SearchableDocument(id, title, body, kind, createdAt)
    }
}

private fun BusinessProfileEntity.toCore() = BusinessProfile(businessName, ownerName, phone, address, tin, currency)

private fun BusinessRecordEntity.toCore() = BusinessRecord(
    id = id, type = RecordType.valueOf(type), date = LocalDate.parse(date), description = description, category = category,
    quantity = BigDecimal(quantity), unitAmount = Money(unitAmountMinor), customerOrSupplier = customerOrSupplier, note = note,
)

private fun UsageQuotaEntity.toCore() = UsageQuota(
    plan = runCatching { PlanId.valueOf(plan.uppercase()) }.getOrDefault(PlanId.FREE),
    used = used, limit = monthlyLimit, bonusRemaining = bonusRemaining, resetsOn = LocalDate.parse(resetsOn),
)

fun UserEntity.consent() = ConsentState(aiConsentGranted, aiConsentDecidedAt)
