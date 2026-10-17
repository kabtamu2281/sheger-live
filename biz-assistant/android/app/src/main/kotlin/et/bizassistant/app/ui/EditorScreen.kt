package et.bizassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import et.bizassistant.app.platform.Exporter
import et.bizassistant.app.platform.ReadAloud
import et.bizassistant.core.DraftSource
import et.bizassistant.core.Str

/** Edit, preview, save, export and read aloud any generated result. */
@Composable
fun EditorScreen(vm: AppViewModel) {
    val e = vm.editor
    if (e == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val readAloud = remember { ReadAloud(context) }
    DisposableEffect(Unit) { onDispose { readAloud.shutdown() } }
    var tab by rememberSaveable { mutableStateOf(0) }
    var speaking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.RESULT), onBack = { vm.back() })
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(t(Str.EDIT)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(t(Str.PREVIEW)) })
        }
        ScrollColumn {
            NoticeCard(
                t(
                    when (e.source) {
                        DraftSource.AI -> Str.SOURCE_AI
                        DraftSource.TEMPLATE -> Str.SOURCE_TEMPLATE
                        DraftSource.CALCULATED -> Str.SOURCE_CALCULATED
                    },
                ),
                warning = e.source == DraftSource.AI,
            )
            e.notice?.let { NoticeCard(it, warning = true) }

            if (tab == 0) {
                Field(t(Str.DOCUMENT_TITLE), e.title, { vm.updateEditor(title = it) })
                Field(t(Str.RESULT), e.body, { vm.updateEditor(body = it) }, multiline = true)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            Text(e.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Button(onClick = { vm.saveEditor() }, modifier = Modifier.fillMaxWidth()) { Text(t(if (e.saved) Str.SAVED else Str.SAVE)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Exporter.sharePdf(context, e.title, e.title + "\n\n" + e.body) }, Modifier.weight(1f)) { Text(t(Str.EXPORT_PDF)) }
                OutlinedButton(onClick = { Exporter.shareText(context, e.title, e.body) }, Modifier.weight(1f)) { Text(t(Str.EXPORT_TEXT)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (speaking) { readAloud.stop(); speaking = false }
                        else if (readAloud.speak(e.body, e.language)) speaking = true
                        else vm.message = Str.TTS_UNAVAILABLE.get(e.language)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t(if (speaking) Str.STOP else Str.READ_ALOUD)) }
                OutlinedButton(onClick = { vm.saveEditorAsTemplate() }, Modifier.weight(1f)) { Text(t(Str.LIBRARY) + " +") }
            }
            if (e.docId != 0L) {
                TextButton(onClick = { confirmDelete = true }) { Text(t(Str.DELETE), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(t(Str.DELETE_CONFIRM), e.title, t(Str.DELETE), { vm.deleteDocument(e.docId) }, { confirmDelete = false }, destructive = true)
    }
}
