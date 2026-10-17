package et.bizassistant.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import et.bizassistant.app.BizApp
import et.bizassistant.app.data.ConversationEntity
import et.bizassistant.app.data.GeneratedDocumentEntity
import et.bizassistant.app.data.TaskTemplateEntity
import et.bizassistant.app.data.UserEntity
import et.bizassistant.app.data.consent
import et.bizassistant.core.AppLanguage
import et.bizassistant.core.AssistantEngine
import et.bizassistant.core.BusinessProfile
import et.bizassistant.core.BusinessRecord
import et.bizassistant.core.DocKind
import et.bizassistant.core.DocumentRenderer
import et.bizassistant.core.DraftSource
import et.bizassistant.core.FieldSpec
import et.bizassistant.core.Fields
import et.bizassistant.core.PeriodSummary
import et.bizassistant.core.SalesDocument
import et.bizassistant.core.Str
import et.bizassistant.core.SummaryCalculator
import et.bizassistant.core.TaskOutcome
import et.bizassistant.core.TaskType
import et.bizassistant.core.UsageQuota
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Loading : Screen
    data object Welcome : Screen
    data object ProfileSetup : Screen
    data class Consent(val fromSettings: Boolean) : Screen
    data object Home : Screen
    data object Records : Screen
    data class RecordForm(val sale: Boolean) : Screen
    data class DocBuilder(val kind: DocKind) : Screen
    data class Task(val type: TaskType) : Screen
    data object Editor : Screen
    data object Library : Screen
    data object History : Screen
    data object Settings : Screen
    data object Plans : Screen
}

/** The document currently open in the editor. Every result – AI, template or calculated – is editable. */
data class EditorState(
    val docId: Long = 0,
    val conversationId: Long? = null,
    val kind: String,
    val title: String,
    val body: String,
    val source: DraftSource,
    val language: AppLanguage,
    val notice: String? = null,
    val saved: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as BizApp).repository
    private val backend = (app as BizApp).backend

    val user: StateFlow<UserEntity> = repo.user.stateIn(viewModelScope, SharingStarted.Eagerly, UserEntity())
    val language: StateFlow<AppLanguage> = repo.language.map { AppLanguage.fromTag(it.appLanguage) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.ENGLISH)
    val profile: StateFlow<BusinessProfile?> = repo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val quota: StateFlow<UsageQuota?> = repo.quota.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val records: StateFlow<List<BusinessRecord>> = repo.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents: StateFlow<List<GeneratedDocumentEntity>> = repo.documentList.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val templates: StateFlow<List<TaskTemplateEntity>> = repo.templates.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = repo.conversationList.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var stack by mutableStateOf(listOf<Screen>(Screen.Loading))
        private set
    val screen: Screen get() = stack.last()

    var editor by mutableStateOf<EditorState?>(null)
        private set
    var missingFields by mutableStateOf<List<FieldSpec>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var online by mutableStateOf(true)
        private set
    /** One-off message for a snackbar. */
    var message by mutableStateOf<String?>(null)

    private val engine = AssistantEngine(
        ai = backend,
        consent = { user.value.consent() },
        quota = { quota.value },
    )

    init {
        viewModelScope.launch {
            val u = repo.user.first()
            val p = repo.profile.first()
            stack = listOf(
                when {
                    u.onboardingDone && p != null -> Screen.Home
                    else -> Screen.Welcome
                },
            )
            repo.applyRetention()
            refreshUsage()
        }
    }

    // Navigation -----------------------------------------------------------------------------

    fun navigate(to: Screen) {
        missingFields = emptyList()
        stack = stack + to
    }

    fun replaceAll(to: Screen) {
        stack = listOf(to)
    }

    /** Returns false when there is nothing to go back to (the activity should finish). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    private fun t(s: Str) = s.get(language.value)

    // Onboarding & settings --------------------------------------------------------------------

    fun chooseLanguage(lang: AppLanguage) = viewModelScope.launch { repo.setLanguage(lang) }

    fun saveProfile(p: BusinessProfile, thenOnboarding: Boolean) = viewModelScope.launch {
        repo.saveProfile(p)
        if (thenOnboarding) navigate(Screen.Consent(fromSettings = false)) else { message = t(Str.SAVED); back() }
    }

    fun decideConsent(granted: Boolean, fromSettings: Boolean) = viewModelScope.launch {
        repo.setConsent(granted)
        if (fromSettings) back() else {
            repo.updateUser { it.copy(onboardingDone = true) }
            replaceAll(Screen.Home)
        }
    }

    fun setAiConsent(granted: Boolean) = viewModelScope.launch { repo.setConsent(granted) }

    fun setRetention(months: Int) = viewModelScope.launch {
        repo.updateUser { it.copy(recordRetentionMonths = months) }
        repo.applyRetention()
    }

    fun refreshUsage() = viewModelScope.launch {
        val q = withContext(Dispatchers.IO) { backend.fetchUsage() }
        online = q != null
        if (q != null) repo.saveQuota(q)
    }

    fun upgrade(planOrBundle: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { backend.startCheckout(planOrBundle) }
        // A plan is only shown as active once the backend confirms payment; nothing is assumed here.
        message = if (ok) t(Str.SAVED) else t(Str.PAYMENTS_NOT_CONFIGURED)
        if (ok) refreshUsage()
    }

    fun deleteAllConversations() = viewModelScope.launch { repo.deleteAllConversations(); message = t(Str.DELETED) }
    fun deleteAllDocuments() = viewModelScope.launch { repo.deleteAllDocuments(); message = t(Str.DELETED) }
    fun deleteAllRecords() = viewModelScope.launch { repo.deleteAllRecords(); message = t(Str.DELETED) }

    fun deleteEverything() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            backend.deleteServerData()
            repo.deleteEverything()
        }
        editor = null
        replaceAll(Screen.Welcome)
    }

    // Records ----------------------------------------------------------------------------------

    fun addRecord(record: BusinessRecord) = viewModelScope.launch {
        repo.addRecord(record)
        message = t(Str.SAVED)
        back()
    }

    fun deleteRecord(id: Long) = viewModelScope.launch { repo.deleteRecord(id) }

    // Deterministic documents ------------------------------------------------------------------

    fun nextDocumentNumber(kind: DocKind): String {
        val prefix = if (kind == DocKind.INVOICE) "INV" else "QUO"
        val count = documents.value.count { it.kind == if (kind == DocKind.INVOICE) TaskType.INVOICE.name else TaskType.QUOTATION.name }
        return "$prefix-${LocalDate.now().year}-${(count + 1).toString().padStart(4, '0')}"
    }

    fun openSalesDocument(doc: SalesDocument) {
        val p = profile.value ?: return
        val lang = language.value
        val type = if (doc.kind == DocKind.INVOICE) TaskType.INVOICE else TaskType.QUOTATION
        editor = EditorState(
            kind = type.name,
            title = "${type.title.get(lang)} ${doc.number} – ${doc.customerName}",
            body = DocumentRenderer.render(doc, p, lang),
            source = DraftSource.CALCULATED,
            language = lang,
        )
        navigate(Screen.Editor)
    }

    fun openSummary(daily: Boolean, day: LocalDate = LocalDate.now()) = viewModelScope.launch {
        val p = profile.value ?: return@launch
        val lang = language.value
        val type = if (daily) TaskType.DAILY_SUMMARY else TaskType.MONTHLY_SUMMARY
        val summary = if (daily) SummaryCalculator.daily(records.value, day) else SummaryCalculator.monthly(records.value, YearMonth.from(day))
        editor = EditorState(
            kind = type.name,
            title = "${type.title.get(lang)} ${summary.label}",
            body = DocumentRenderer.renderSummary(summary, p, lang, type.title),
            source = DraftSource.CALCULATED,
            language = lang,
        )
        navigate(Screen.Editor)
    }

    // AI-assisted tasks ------------------------------------------------------------------------

    private fun summaryFor(period: String): PeriodSummary {
        val today = LocalDate.now()
        return when (period) {
            "today" -> SummaryCalculator.daily(records.value, today)
            "last_month" -> SummaryCalculator.monthly(records.value, YearMonth.from(today).minusMonths(1))
            else -> SummaryCalculator.monthly(records.value, YearMonth.from(today))
        }
    }

    /** Runs a task; the caller has already shown the send-confirmation dialog when AI is enabled. */
    fun runTask(type: TaskType, inputs: Map<String, String>, outputLanguage: AppLanguage) {
        val p = profile.value ?: return
        missingFields = type.missingFields(inputs)
        if (missingFields.isNotEmpty()) return
        busy = true
        viewModelScope.launch {
            val summary = if (type == TaskType.REPORT) summaryFor(inputs[Fields.REPORT_PERIOD].orEmpty()) else null
            val outcome = withContext(Dispatchers.IO) { engine.run(type, inputs, p, outputLanguage, summary) }
            busy = false
            when (outcome) {
                is TaskOutcome.NeedsInput -> missingFields = outcome.missing
                is TaskOutcome.Draft -> {
                    outcome.quota?.let { repo.saveQuota(it) }
                    online = outcome.notice != Str.FALLBACK_AI_UNAVAILABLE
                    val lang = language.value
                    val notice = outcome.notice?.format(lang, *outcome.noticeArgs.toTypedArray())
                    val title = type.title.get(lang) + (inputs[Fields.CUSTOMER_NAME] ?: inputs[Fields.PRODUCT_NAME] ?: inputs[Fields.SUBJECT])?.let { " – $it" }.orEmpty()
                    val convId = repo.startConversation(type.name, outputLanguage, title)
                    repo.addMessage(convId, "user", type.fields.mapNotNull { f -> inputs[f.key]?.takeIf { it.isNotBlank() }?.let { "${f.label.get(lang)}: $it" } }.joinToString("\n"))
                    notice?.let { repo.addMessage(convId, "system", it) }
                    repo.addMessage(convId, "assistant", outcome.text)
                    editor = EditorState(
                        conversationId = convId, kind = type.name, title = title, body = outcome.text,
                        source = outcome.source, language = outputLanguage, notice = notice,
                    )
                    navigate(Screen.Editor)
                }
            }
        }
    }

    // Editor & library -------------------------------------------------------------------------

    fun updateEditor(title: String = editor?.title.orEmpty(), body: String = editor?.body.orEmpty()) {
        editor = editor?.copy(title = title, body = body, saved = false)
    }

    fun saveEditor() = viewModelScope.launch {
        val e = editor ?: return@launch
        val id = repo.saveDocument(
            GeneratedDocumentEntity(
                id = e.docId, conversationId = e.conversationId, kind = e.kind, title = e.title.ifBlank { e.kind },
                body = e.body, language = e.language.tag, source = e.source.name,
            ),
        )
        editor = e.copy(docId = id, saved = true)
        message = t(Str.SAVED)
    }

    fun saveEditorAsTemplate() = viewModelScope.launch {
        val e = editor ?: return@launch
        repo.saveTemplate(TaskTemplateEntity(taskType = e.kind, language = e.language.tag, title = e.title, body = e.body))
        message = t(Str.SAVED)
    }

    fun openDocument(id: Long) = viewModelScope.launch {
        val d = repo.document(id) ?: return@launch
        editor = EditorState(
            docId = d.id, conversationId = d.conversationId, kind = d.kind, title = d.title, body = d.body,
            source = runCatching { DraftSource.valueOf(d.source) }.getOrDefault(DraftSource.TEMPLATE),
            language = AppLanguage.fromTag(d.language), saved = true,
        )
        navigate(Screen.Editor)
    }

    fun openTemplate(t: TaskTemplateEntity) {
        editor = EditorState(kind = t.taskType, title = t.title, body = t.body, source = DraftSource.TEMPLATE, language = AppLanguage.fromTag(t.language))
        navigate(Screen.Editor)
    }

    fun deleteDocument(id: Long) = viewModelScope.launch {
        repo.deleteDocument(id)
        if (editor?.docId == id) { editor = null; back() }
        message = t(Str.DELETED)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch { repo.deleteTemplate(id) }

    fun deleteConversation(id: Long) = viewModelScope.launch { repo.deleteConversation(id); message = t(Str.DELETED) }

    suspend fun conversationText(id: Long): String =
        repo.messages(id).joinToString("\n\n") { "[${it.role}]\n${it.text}" }
}
