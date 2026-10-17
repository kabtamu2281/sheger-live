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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.app.data.Repository.Companion.toSearchable
import et.bizassistant.core.LibrarySearch
import et.bizassistant.core.Str
import et.bizassistant.core.TaskType
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val results = remember(docs, query) {
        val byId = docs.associateBy { it.id }
        LibrarySearch.search(docs.map { it.toSearchable() }, query).mapNotNull { byId[it.id] }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.LIBRARY), actions = { TextButton(onClick = { vm.navigate(Screen.History) }) { Text(t(Str.HISTORY)) } })
        Field(t(Str.SEARCH), query, { query = it }, Modifier.padding(horizontal = 16.dp))
        if (results.isEmpty()) Text(t(Str.NO_DOCUMENTS), Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().clickable { vm.openDocument(d.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val kindTitle = runCatching { TaskType.valueOf(d.kind).title }.getOrNull()?.let { t(it) } ?: d.kind
                            Text("$kindTitle · ${DateFormat.getDateInstance().format(Date(d.createdAt))}", style = MaterialTheme.typography.labelMedium)
                            Text(d.title, style = MaterialTheme.typography.titleSmall)
                            Text(d.body.take(90).replace('\n', ' '), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        IconButton(onClick = { pendingDelete = d.id to d.title }) { Icon(Icons.Default.Delete, t(Str.DELETE)) }
                    }
                }
            }
        }
    }
    pendingDelete?.let { (id, title) ->
        ConfirmDialog(t(Str.DELETE_CONFIRM), title, t(Str.DELETE), { vm.deleteDocument(id) }, { pendingDelete = null }, destructive = true)
    }
}

/** Saved task conversations (inputs, notices and drafts). Each can be read or deleted. */
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.HISTORY), onBack = { vm.back() })
        if (conversations.isEmpty()) Text(t(Str.NO_DOCUMENTS), Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(conversations, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { scope.launch { open = c.title to vm.conversationText(c.id) } }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DateFormat.getDateTimeInstance().format(Date(c.createdAt)), style = MaterialTheme.typography.labelMedium)
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                        }
                        IconButton(onClick = { pendingDelete = c.id }) { Icon(Icons.Default.Delete, t(Str.DELETE)) }
                    }
                }
            }
        }
    }
    open?.let { (title, text) ->
        ConfirmDialog(title, text, t(Str.CONTINUE), {}, { open = null })
    }
    pendingDelete?.let { id ->
        ConfirmDialog(t(Str.DELETE_CONFIRM), null, t(Str.DELETE), { vm.deleteConversation(id) }, { pendingDelete = null }, destructive = true)
    }
}
