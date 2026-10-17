package et.bizassistant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.core.AppLanguage
import et.bizassistant.core.ChoiceLabels
import et.bizassistant.core.FieldKind
import et.bizassistant.core.Fields
import et.bizassistant.core.Str
import et.bizassistant.core.TaskType

/**
 * Guided form for an AI-assisted task. Missing required information is requested before anything
 * is produced, and the user confirms each time before text is sent to the AI service.
 */
@Composable
fun TaskScreen(vm: AppViewModel, type: TaskType) {
    val lang by vm.language.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val inputs = remember(type) { mutableStateMapOf<String, String>() }
    var outputLang by rememberSaveable { mutableStateOf(lang.tag) }
    var confirmSend by remember { mutableStateOf(false) }
    val missingKeys = vm.missingFields.map { it.key }.toSet()

    fun submit() {
        val values = inputs.toMap()
        if (type.missingFields(values).isEmpty() && user.aiConsentGranted) confirmSend = true
        else vm.runTask(type, values, AppLanguage.fromTag(outputLang))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(type.title), onBack = { vm.back() })
        ScrollColumn {
            NoticeCard(t(if (user.aiConsentGranted) Str.AI_ENABLED else Str.AI_DISABLED), warning = !user.aiConsentGranted)
            if (vm.missingFields.isNotEmpty()) {
                NoticeCard(t(Str.MISSING_INFO) + "\n" + vm.missingFields.map { "• " + t(it.label) }.joinToString("\n"), warning = true)
            }

            type.fields.forEach { field ->
                val error = if (field.key in missingKeys) t(Str.REQUIRED_FIELD) else null
                when (field.kind) {
                    FieldKind.CHOICE -> {
                        Text(t(field.label) + if (field.required) " *" else "", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(field.choices.map { it to ChoiceLabels.label(it, lang) }, inputs[field.key]) { inputs[field.key] = it }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    FieldKind.LANGUAGE -> {
                        Text(t(field.label), style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AppLanguage.entries.map { it.tag to it.nativeName }, inputs[field.key]) { inputs[field.key] = it }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    else -> Field(
                        label = t(field.label) + if (field.required) " *" else "",
                        value = inputs[field.key].orEmpty(),
                        onChange = { inputs[field.key] = it },
                        multiline = field.kind == FieldKind.LONG_TEXT,
                        error = error,
                        hint = field.hint?.let { t(it) },
                        trailing = { VoiceButton(lang, { spoken -> inputs[field.key] = (inputs[field.key].orEmpty() + " " + spoken).trim() }, { vm.message = Str.VOICE_UNAVAILABLE.get(lang) }) },
                    )
                }
            }

            if (type != TaskType.TRANSLATION) {
                Text(t(Str.LANGUAGE), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(AppLanguage.entries.map { it.tag to it.nativeName }, outputLang) { outputLang = it }
            }

            Button(onClick = { submit() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                if (vm.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(t(Str.GENERATING))
                    }
                } else Text(t(Str.GENERATE))
            }

            val mine = templates.filter { it.taskType == type.name }
            if (mine.isNotEmpty()) {
                SectionLabel(t(Str.LIBRARY))
                mine.forEach { tpl ->
                    Card(Modifier.fillMaxWidth().clickable { vm.openTemplate(tpl) }) {
                        Text(tpl.title, Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (confirmSend) {
        ConfirmDialog(
            title = t(Str.WHAT_IS_SENT),
            body = t(Str.CONFIRM_SEND) + "\n\n" + type.fields.filter { !inputs[it.key].isNullOrBlank() }.map { f -> "${t(f.label)}: ${inputs[f.key]}" }
                .joinToString("\n").take(600),
            confirm = t(Str.SEND),
            onConfirm = {
                val values = inputs.toMap()
                vm.runTask(type, values, if (type == TaskType.TRANSLATION) AppLanguage.fromTag(values[Fields.TARGET_LANGUAGE]) else AppLanguage.fromTag(outputLang))
            },
            onDismiss = { confirmSend = false },
        )
    }
}
