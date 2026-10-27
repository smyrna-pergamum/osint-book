package io.github.smyrna_pergamum.osintbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.Investigation
import io.github.smyrna_pergamum.osintbook.data.InvestigationSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: OsintApp, open: (String) -> Unit, openSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val items by remember(query, archived) { app.db.investigations().observeSummaries(query.trim(), archived) }.collectAsState(initial = null)
    var editing by remember { mutableStateOf<Investigation?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Investigation?>(null) }
    val shared by app.sharedText.collectAsState()
    val copySuffix = stringResource(R.string.copy_suffix)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.investigations)) },
                actions = {
                    IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.new_investigation)) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                label = { Text(stringResource(R.string.search_local)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !archived, onClick = { archived = false }, label = { Text(stringResource(R.string.active)) })
                FilterChip(selected = archived, onClick = { archived = true }, label = { Text(stringResource(R.string.archived)) })
            }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(stringResource(if (query.isBlank() && !archived) R.string.empty_investigations else R.string.no_matches), Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { s ->
                        InvestigationCard(
                            s, onOpen = { open(s.id) },
                            onRename = { scope.launch { editing = app.db.investigations().get(s.id) } },
                            onCopy = { scope.launch { app.db.investigations().get(s.id)?.let { app.repo.copy(it, copySuffix) } } },
                            onArchive = { scope.launch { app.db.investigations().get(s.id)?.let { app.repo.setArchived(it, s.archivedAt == null) } } },
                            onDelete = { scope.launch { deleting = app.db.investigations().get(s.id) } },
                        )
                    }
                }
            }
        }
    }

    if (creating) InvestigationDialog(null, onDismiss = { creating = false }) { t, d ->
        scope.launch { val inv = app.repo.createInvestigation(t, d); creating = false; open(inv.id) }
    }
    editing?.let { inv ->
        InvestigationDialog(inv, onDismiss = { editing = null }) { t, d -> scope.launch { app.repo.rename(inv, t, d); editing = null } }
    }
    deleting?.let { inv ->
        ConfirmDialog(
            stringResource(R.string.delete_investigation_q, inv.title), stringResource(R.string.delete_investigation_body),
            stringResource(R.string.delete), onConfirm = { scope.launch { app.repo.delete(inv) } }, onDismiss = { deleting = null },
        )
    }
    // FR-09: a shared link waits until the user picks where it goes.
    shared?.let { text -> SharedTextDialog(app, text, onPick = { id -> open(id) }) }
}

@Composable
private fun InvestigationCard(
    s: InvestigationSummary, onOpen: () -> Unit, onRename: () -> Unit, onCopy: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.open), onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.inv_counts, s.targetCount, s.findingCount, formatTime(s.updatedAt)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text(stringResource(if (s.archivedAt == null) R.string.archive else R.string.unarchive)) }, onClick = { menu = false; onArchive() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun InvestigationDialog(existing: Investigation?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var desc by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_investigation else R.string.rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(title, { title = it.take(300) }, label = { Text(stringResource(R.string.title)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(desc, { desc = it }, label = { Text(stringResource(R.string.description)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, desc) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SharedTextDialog(app: OsintApp, text: String, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val list by remember { app.db.investigations().observeSummaries("", false) }.collectAsState(initial = emptyList())
    val newTitle = stringResource(R.string.shared_new_title)
    AlertDialog(
        onDismissRequest = { app.sharedText.value = null },
        title = { Text(stringResource(R.string.shared_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.shared_pick), style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(list, key = { it.id }) { s ->
                        TextButton(onClick = { onPick(s.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { onPick(app.repo.createInvestigation(newTitle, "").id) } }) { Text(stringResource(R.string.new_investigation)) }
        },
        dismissButton = { TextButton(onClick = { app.sharedText.value = null }) { Text(stringResource(R.string.cancel)) } },
    )
}
