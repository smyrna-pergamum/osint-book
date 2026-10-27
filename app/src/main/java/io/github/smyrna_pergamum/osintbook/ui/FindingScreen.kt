package io.github.smyrna_pergamum.osintbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.AssertionType
import io.github.smyrna_pergamum.osintbook.data.FindingRow
import io.github.smyrna_pergamum.osintbook.data.Note
import io.github.smyrna_pergamum.osintbook.data.Relation
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.providers.Providers
import io.github.smyrna_pergamum.osintbook.providers.COORDINATES_LABEL
import io.github.smyrna_pergamum.osintbook.providers.parseCoordinates
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** §1 Finding detail: source, times, evidence, review state, notes, tags and relations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingScreen(app: OsintApp, id: String, back: () -> Unit, openMap: (invId: String, findingId: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finding by remember(id) { app.db.findings().observe(id) }.collectAsState(initial = null)
    val evidence by remember(id) { app.db.evidence().observeFor(id) }.collectAsState(initial = emptyList())
    val notes by remember(id) { app.db.notes().observeForFinding(id) }.collectAsState(initial = emptyList())
    val tags by remember(id) { app.db.tags().observeForFinding(id) }.collectAsState(initial = emptyList())
    val f = finding
    val invId = f?.investigationId
    val targets by remember(invId) { invId?.let { app.db.targets().observeFor(it) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val rows by remember(invId) { invId?.let { app.db.findings().observeRows(it) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val relations by remember(invId) { invId?.let { app.db.relations().observeFor(it) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var editNote by remember { mutableStateOf<Note?>(null) }
    var addTag by remember { mutableStateOf(false) }
    var addRelation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.finding)) },
                actions = {
                    if (f != null) {
                        val shareSubject = stringResource(R.string.finding)
                        val retrieved = stringResource(R.string.retrieved_at)
                        val sourceLbl = stringResource(R.string.source_url)
                        IconButton(onClick = {
                            shareText(context, shareSubject, "${f.type} · ${f.label}: ${f.value}\n$sourceLbl: ${f.sourceUrl}\n$retrieved: ${f.retrievedAt} (UTC)\n— ${Providers.byId(f.providerId)?.name ?: f.providerId}")
                        }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                    }
                    if (f != null) IconButton(onClick = { scope.launch { app.repo.setFavorite(f.id, !f.favorite) } }) {
                        Icon(if (f.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(if (f.favorite) R.string.unfavorite else R.string.favorite))
                    }
                },
            )
        },
    ) { pad ->
        if (f == null) return@Scaffold
        val provider = Providers.byId(f.providerId)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${f.type} · ${f.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(f.value, style = MaterialTheme.typography.titleMedium) }
            if (provider?.reportsReputation == true && f.type == "PROVIDER_ASSESSMENT") {
                Text(stringResource(R.string.reputation_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (f.label == COORDINATES_LABEL) parseCoordinates(f.value)?.let { (lat, lon) ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Field(stringResource(R.string.latitude), String.format(java.util.Locale.ROOT, "%.5f", lat))
                    Field(stringResource(R.string.longitude), String.format(java.util.Locale.ROOT, "%.5f", lon))
                }
                OsmMap(listOf(MapPoint(lat, lon, f.value, provider?.name ?: f.providerId)), Modifier.fillMaxWidth().height(240.dp))
                Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openMap(f.investigationId, f.id) }) { Text(stringResource(R.string.open_full_map)) }
                    OutlinedButton(onClick = { openInMapApp(context, lat, lon, f.value) }) { Text(stringResource(R.string.open_in_map_app)) }
                }
                // Reverse geocoding is a separate, user-started query to a named host (FR-03).
                val engineProgress by app.engine.progress.collectAsState()
                val addrStatus = engineProgress[f.targetId]?.get("nominatim")?.status
                val lang = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
                Button(enabled = addrStatus == null || addrStatus.isFinal, onClick = {
                    scope.launch {
                        val target = app.db.targets().get(f.targetId) ?: return@launch
                        Providers.byId("nominatim")?.let { app.engine.start(target, listOf(it), lang, mapOf("lat" to "$lat", "lon" to "$lon")) }
                    }
                }) { Text(stringResource(R.string.find_address)) }
                Text(stringResource(R.string.find_address_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                addrStatus?.let { RunStatusChip(it) }
                Text(stringResource(R.string.geo_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Field(stringResource(R.string.source), provider?.name ?: f.providerId)
            Field(stringResource(R.string.retrieved_at), formatTime(f.retrievedAt) + " (UTC ${f.retrievedAt})")
            Field(stringResource(R.string.published_at), f.publishedAt?.let { formatTime(it) } ?: stringResource(R.string.not_stated))
            Text(stringResource(R.string.stale_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(stringResource(R.string.source_url), style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(f.sourceUrl, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { openLink(context, f.sourceUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Text("  " + stringResource(R.string.open_in_browser))
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.review_status), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewStatus.entries.forEach { r ->
                    FilterChip(f.reviewStatus == r, { scope.launch { app.repo.setReview(f.id, r) } }, { Text(stringResource(reviewRes(r))) },
                        leadingIcon = { Icon(reviewIcon(r), null) })
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.evidence), style = MaterialTheme.typography.titleSmall)
            evidence.forEach { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SelectionContainer { Text(e.excerpt.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall) }
                        Text("SHA-256: ${e.sha256}", style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.sha_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val raw = e.rawResponseRef
                        Text(
                            when {
                                raw == null -> stringResource(R.string.raw_not_kept)
                                app.repo.verifyRaw(raw, e.sha256) == true -> stringResource(R.string.raw_matches)
                                app.repo.verifyRaw(raw, e.sha256) == false -> stringResource(R.string.raw_changed)
                                else -> stringResource(R.string.raw_missing)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    InputChip(selected = false, onClick = { scope.launch { app.repo.removeTag(t) } }, label = { Text(t.label) },
                        trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.remove_tag, t.label)) })
                }
                AssistChip(onClick = { addTag = true }, label = { Text(stringResource(R.string.add_tag)) })
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { editNote = Note(investigationId = f.investigationId, findingId = f.id, content = "") }) { Text(stringResource(R.string.add_note)) }
            }
            notes.forEach { n -> NoteCard(n, edit = { editNote = n }, delete = { scope.launch { app.repo.deleteNote(n) } }) }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.relations), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { addRelation = true }) { Text(stringResource(R.string.add_relation)) }
            }
            val names = nodeNames(targets, rows)
            relations.filter { it.fromId == f.id || it.toId == f.id }.forEach { r ->
                Text("${names[r.fromId] ?: "?"} → ${r.type} → ${names[r.toId] ?: "?"} (${stringResource(assertionRes(r.assertionType))})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    editNote?.let { n -> NoteDialog(n, onDismiss = { editNote = null }) { text -> scope.launch { app.repo.saveNote(n.copy(content = text)); editNote = null } } }
    if (addTag && f != null) TagDialog(onDismiss = { addTag = false }) { label -> scope.launch { app.repo.addTag(f, label); addTag = false } }
    if (addRelation && f != null) RelationDialog(app, f.investigationId, f.id, targets, rows) { addRelation = false }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TagDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_tag)) },
        text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** FR-08: direction, type, supporting finding, assertion kind and the user's explanation. */
@Composable
fun RelationDialog(app: OsintApp, invId: String, fromId: String?, targets: List<Target>, findings: List<FindingRow>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val names = nodeNames(targets, findings)
    val nodes = names.keys.toList()
    var from by remember { mutableStateOf(fromId) }
    var to by remember { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf("") }
    var assertion by remember { mutableStateOf(AssertionType.INFERRED) }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<Int?>(null) } // 0 = from, 1 = to

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.add_relation)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = 0 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rel_from) + ": " + (from?.let { names[it] } ?: "…"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rel_to) + ": " + (to?.let { names[it] } ?: "…"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(type, { type = it.take(100) }, label = { Text(stringResource(R.string.rel_type)) }, placeholder = { Text(stringResource(R.string.rel_type_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssertionType.entries.forEach { a -> FilterChip(assertion == a, { assertion = a }, { Text(stringResource(assertionRes(a))) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.rel_note)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (picking != null) {
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(nodes) { n ->
                            Text(names[n] ?: n, Modifier.fillMaxWidth().clickable {
                                if (picking == 0) from = n else to = n
                                picking = null
                            }.padding(vertical = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = from != null && to != null && from != to && type.isNotBlank(), onClick = {
                scope.launch {
                    // The supporting evidence is the finding's own, when one end is a finding.
                    val evidenceId = listOfNotNull(from, to).firstNotNullOfOrNull { nid ->
                        if (findings.any { it.id == nid }) app.db.evidence().listForInvestigation(invId).firstOrNull { it.findingId == nid }?.id else null
                    }
                    app.repo.addRelation(Relation(investigationId = invId, fromId = from!!, toId = to!!, type = type.trim(),
                        evidenceId = evidenceId, assertionType = assertion, note = note.trim()))
                    onDone()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}
