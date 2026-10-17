package et.bizassistant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.core.BusinessRecord
import et.bizassistant.core.DocKind
import et.bizassistant.core.Money
import et.bizassistant.core.PlanCatalog
import et.bizassistant.core.QUICK_ACTIONS
import et.bizassistant.core.RecordType
import et.bizassistant.core.Str
import et.bizassistant.core.TaskType
import et.bizassistant.core.parseQuantity
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable
fun HomeScreen(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    ScrollColumn {
        Text(profile?.businessName ?: t(Str.APP_NAME), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Usage allowance, always visible on the home screen.
        Card(Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Plans) }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t(Str.USAGE_TITLE), style = MaterialTheme.typography.titleMedium)
                val q = quota
                if (q == null) {
                    Text(t(Str.USAGE_UNKNOWN))
                } else {
                    Text(tf(Str.PLAN_LABEL, PlanCatalog.plan(q.plan).name))
                    Text(tf(Str.USAGE_LINE, q.used, q.limit))
                    LinearProgressIndicator(
                        progress = { if (q.limit == 0) 1f else (q.used.toFloat() / q.limit).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (q.bonusRemaining > 0) Text(tf(Str.BUNDLE_EXTRA, q.bonusRemaining))
                    Text(tf(Str.USAGE_RESETS, q.resetsOn.toString()), style = MaterialTheme.typography.bodySmall)
                }
                Text(t(if (user.aiConsentGranted) Str.AI_ENABLED else Str.AI_DISABLED), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!vm.online) NoticeCard(t(Str.OFFLINE_MODE), warning = true)

        SectionLabel(t(Str.WHAT_TO_DO))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.navigate(Screen.RecordForm(sale = true)) }, Modifier.weight(1f)) { Text(t(Str.RECORD_SALE)) }
            OutlinedButton(onClick = { vm.navigate(Screen.RecordForm(sale = false)) }, Modifier.weight(1f)) { Text(t(Str.RECORD_EXPENSE)) }
        }
        QUICK_ACTIONS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { task -> QuickAction(task, Modifier.weight(1f)) { openTask(vm, task) } }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

fun openTask(vm: AppViewModel, task: TaskType) = when (task) {
    TaskType.INVOICE -> vm.navigate(Screen.DocBuilder(DocKind.INVOICE))
    TaskType.QUOTATION -> vm.navigate(Screen.DocBuilder(DocKind.QUOTATION))
    TaskType.DAILY_SUMMARY -> { vm.openSummary(daily = true); Unit }
    TaskType.MONTHLY_SUMMARY -> { vm.openSummary(daily = false); Unit }
    else -> vm.navigate(Screen.Task(task))
}

@Composable
private fun QuickAction(task: TaskType, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (task.usesAi) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(t(task.title), Modifier.padding(16.dp).fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun RecordsScreen(vm: AppViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val currency = profile?.currency ?: Money.DEFAULT_CURRENCY
    var pendingDelete by remember { mutableStateOf<BusinessRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.RECORDS))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.navigate(Screen.RecordForm(true)) }, Modifier.weight(1f)) { Text(t(Str.RECORD_SALE)) }
            OutlinedButton(onClick = { vm.navigate(Screen.RecordForm(false)) }, Modifier.weight(1f)) { Text(t(Str.RECORD_EXPENSE)) }
        }
        if (records.isEmpty()) {
            Text(t(Str.NO_RECORDS), Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t(if (r.type == RecordType.SALE) Str.SALE else Str.EXPENSE)} · ${r.date}", style = MaterialTheme.typography.labelMedium)
                            Text(r.description, style = MaterialTheme.typography.titleSmall)
                            Text("${r.quantity.stripTrailingZeros().toPlainString()} × ${r.unitAmount.formatPlain()} = ${r.amount.format(currency)}")
                        }
                        IconButton(onClick = { pendingDelete = r }) { Icon(Icons.Default.Delete, t(Str.DELETE)) }
                    }
                }
            }
        }
    }
    pendingDelete?.let { r ->
        ConfirmDialog(t(Str.DELETE_CONFIRM), r.description, t(Str.DELETE), { vm.deleteRecord(r.id) }, { pendingDelete = null }, destructive = true)
    }
}

/** Guided sale/expense entry: each field is validated before anything is saved. */
@Composable
fun RecordFormScreen(vm: AppViewModel, sale: Boolean) {
    val lang by vm.language.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var amount by rememberSaveable { mutableStateOf("") }
    var party by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val parsedDate = try { LocalDate.parse(date.trim()) } catch (e: DateTimeParseException) { null }
    val parsedQty = parseQuantity(quantity)
    val parsedAmount = Money.parse(amount)
    val total = if (parsedQty != null && parsedAmount != null) Money.fromDecimal(parsedQty.multiply(parsedAmount.toDecimal())) else null

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(if (sale) Str.RECORD_SALE else Str.RECORD_EXPENSE), onBack = { vm.back() })
        ScrollColumn {
            Field(t(Str.DATE), date, { date = it }, error = if (showErrors && parsedDate == null) t(Str.INVALID_DATE) else null)
            Field(
                t(Str.DESCRIPTION), description, { description = it },
                error = if (showErrors && description.isBlank()) t(Str.REQUIRED_FIELD) else null,
                trailing = { VoiceButton(lang, { description = it }, { vm.message = Str.VOICE_UNAVAILABLE.get(lang) }) },
            )
            Field(t(Str.QUANTITY), quantity, { quantity = it }, numeric = true, error = if (showErrors && parsedQty == null) t(Str.INVALID_QUANTITY) else null)
            Field(t(Str.UNIT_AMOUNT), amount, { amount = it }, numeric = true, error = if (showErrors && parsedAmount == null) t(Str.INVALID_AMOUNT) else null)
            total?.let { Text("${t(Str.DOC_TOTAL)}: ${it.format()}", style = MaterialTheme.typography.titleMedium) }
            Field(t(Str.CATEGORY_OPTIONAL), category, { category = it })
            Field(t(Str.CUSTOMER_OR_SUPPLIER_OPTIONAL), party, { party = it })
            Field(t(Str.NOTE_OPTIONAL), note, { note = it }, multiline = true)
            Button(
                onClick = {
                    if (parsedDate == null || description.isBlank() || parsedQty == null || parsedAmount == null) {
                        showErrors = true
                    } else {
                        vm.addRecord(
                            BusinessRecord(
                                type = if (sale) RecordType.SALE else RecordType.EXPENSE, date = parsedDate, description = description,
                                category = category, quantity = parsedQty, unitAmount = parsedAmount, customerOrSupplier = party, note = note,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t(Str.SAVE)) }
        }
    }
}
