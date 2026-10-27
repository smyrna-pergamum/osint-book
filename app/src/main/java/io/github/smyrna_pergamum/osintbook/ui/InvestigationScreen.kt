package io.github.smyrna_pergamum.osintbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.FindingRow
import io.github.smyrna_pergamum.osintbook.data.Note
import io.github.smyrna_pergamum.osintbook.data.Relation
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.ParseResult
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.domain.Warning
import io.github.smyrna_pergamum.osintbook.providers.Providers
import io.github.smyrna_pergamum.osintbook.domain.Action
import io.github.smyrna_pergamum.osintbook.domain.Analyzer
import io.github.smyrna_pergamum.osintbook.data.AssertionType
import io.github.smyrna_pergamum.osintbook.providers.COORDINATES_LABEL
import io.github.smyrna_pergamum.osintbook.providers.parseCoordinates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigationScreen(
    app: OsintApp, id: String, back: () -> Unit,
    openQuery: (String) -> Unit, openFinding: (String) -> Unit, openReport: (String) -> Unit, openMap: (String) -> Unit,
    openSettings: () -> Unit,
) {
    val inv by remember(id) { app.db.investigations().observe(id) }.collectAsState(initial = null)
    val targets by remember(id) { app.db.targets().observeFor(id) }.collectAsState(initial = emptyList())
    val findings by remember(id) { app.db.findings().observeRows(id) }.collectAsState(initial = emptyList())
    val relations by remember(id) { app.db.relations().observeFor(id) }.collectAsState(initial = emptyList())
    val notes by remember(id) { app.db.notes().observeFor(id) }.collectAsState(initial = emptyList())
    val fullFindings by remember(id) { app.db.findings().observeFor(id) }.collectAsState(initial = emptyList())
    val runs by remember(id) { app.db.queryRuns().observeForInvestigation(id) }.collectAsState(initial = emptyList())
    val providerSettings by app.db.providerSettings().observeAll().collectAsState(initial = emptyList())
    // Recomputed whenever the stored data changes; it only reads, never queries (AT-10).
    val analysis = remember(targets, fullFindings, runs, relations, providerSettings) {
        Analyzer.analyze(targets, fullFindings, runs, relations, Providers.all.filter { app.repo.hasKey(it.id) }.map { it.id }.toSet())
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val targetAdded = stringResource(R.string.target_added)
    val relationAdded = stringResource(R.string.relation_added)
    var tab by rememberSaveable { mutableStateOf(0) }
    var addTarget by remember { mutableStateOf<String?>(null) }
    var editNote by remember { mutableStateOf<Note?>(null) }
    var addRelation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // FR-09: a link shared into the app is offered as a new target here, never queried automatically.
    val shared by app.sharedText.collectAsState()
    LaunchedEffect(shared) {
        shared?.let { addTarget = it; app.sharedText.value = null; tab = 1 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(inv?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (findings.any { it.label == COORDINATES_LABEL }) IconButton(onClick = { openMap(id) }) { Icon(Icons.Filled.Map, stringResource(R.string.open_full_map)) }
                    IconButton(onClick = { openReport(id) }) { Icon(Icons.Filled.Share, stringResource(R.string.share_report)) }
                    IconButton(onClick = { openReport(id) }) { Icon(Icons.Filled.Description, stringResource(R.string.report)) }
                },
            )
        },
        floatingActionButton = {
            when (tab) {
                0, 2 -> if (findings.isNotEmpty()) ExtendedFloatingActionButton(onClick = { openReport(id) }, icon = { Icon(Icons.Filled.PictureAsPdf, null) }, text = { Text(stringResource(R.string.make_report)) })
                    else if (tab == 0) ExtendedFloatingActionButton(onClick = { addTarget = "" }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_target)) })
                1 -> ExtendedFloatingActionButton(onClick = { addTarget = "" }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_target)) })
                3 -> ExtendedFloatingActionButton(onClick = { addRelation = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_relation)) })
                4 -> ExtendedFloatingActionButton(onClick = { editNote = Note(investigationId = id, content = "") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_note)) })
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                listOf(
                    stringResource(R.string.tab_analysis, analysis.observations.size),
                    stringResource(R.string.tab_targets, targets.size), stringResource(R.string.tab_findings, findings.size),
                    stringResource(R.string.tab_relations, relations.size), stringResource(R.string.tab_notes, notes.size),
                    stringResource(R.string.tab_map, findings.count { it.label == COORDINATES_LABEL }),
                ).forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> AnalysisTab(analysis, targets, openFinding) { action ->
                    scope.launch {
                        when (action) {
                            is Action.AddTarget -> {
                                app.repo.addTarget(Target(investigationId = id, type = action.type, rawValue = action.value, normalizedValue = action.value, displayValue = action.value))
                                android.widget.Toast.makeText(context, targetAdded, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            // Confirmed by the tap; recorded as stated by the source because the shared value comes from the findings.
                            is Action.AddRelation -> {
                                app.repo.addRelation(Relation(investigationId = id, fromId = action.fromId, toId = action.toId, type = action.type, assertionType = AssertionType.SOURCE_STATED))
                                android.widget.Toast.makeText(context, relationAdded, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            Action.OpenSettings -> openSettings()
                        }
                    }
                }
                1 -> Column {
                    if (findings.isNotEmpty()) OutlinedButton(onClick = { openReport(id) }, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.make_report))
                    }
                    TargetsTab(app, targets, openQuery)
                }
                2 -> FindingsTab(findings, openFinding)
                3 -> RelationsTab(app, relations, targets, findings)
                4 -> NotesTab(app, notes.filter { it.findingId == null }) { editNote = it }
                5 -> MapTab(findings, openFinding) { openMap(id) }
            }
        }
    }

    addTarget?.let { initial ->
        AddTargetDialog(initial, onDismiss = { addTarget = null }) { type, raw, ok ->
            scope.launch {
                app.repo.addTarget(Target(investigationId = id, type = type, rawValue = raw, normalizedValue = ok.normalized, displayValue = ok.display))
                addTarget = null
            }
        }
    }
    editNote?.let { n -> NoteDialog(n, onDismiss = { editNote = null }) { text -> scope.launch { app.repo.saveNote(n.copy(content = text)); editNote = null } } }
    if (addRelation) RelationDialog(app, id, null, targets, findings) { addRelation = false }
}

@Composable
private fun TargetsTab(app: OsintApp, targets: List<Target>, openQuery: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Target?>(null) }
    if (targets.isEmpty()) return EmptyState(stringResource(R.string.empty_targets), Modifier.fillMaxSize())
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(targets, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(targetTypeLabel(t.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(t.displayValue, style = MaterialTheme.typography.titleMedium)
                    if (t.displayValue != t.normalizedValue) Text(stringResource(R.string.ascii_form, t.normalizedValue), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.added_at, formatTime(t.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { openQuery(t.id) }) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.query))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { deleting = t }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_target)) }
                    }
                }
            }
        }
    }
    deleting?.let { t ->
        ConfirmDialog(stringResource(R.string.delete_target), stringResource(R.string.delete_target_body, t.displayValue), stringResource(R.string.delete),
            onConfirm = { scope.launch { app.repo.deleteTarget(t) } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun FindingsTab(rows: List<FindingRow>, openFinding: (String) -> Unit) {
    var provider by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var review by rememberSaveable { mutableStateOf<ReviewStatus?>(null) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    if (rows.isEmpty()) return EmptyState(stringResource(R.string.empty_findings), Modifier.fillMaxSize())

    val providers = rows.map { it.providerId }.distinct()
    val types = rows.map { it.type }.distinct()
    val shown = rows.filter {
        (provider == null || it.providerId == provider) && (type == null || it.type == type) &&
            (review == null || it.reviewStatus == review) && (!favOnly || it.favorite) &&
            (q.isBlank() || it.value.contains(q, true) || it.label.contains(q, true))
    }
    Column {
        OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Filled.Search, null) }, label = { Text(stringResource(R.string.filter_text)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        ChipRow {
            FilterChip(provider == null, { provider = null }, { Text(stringResource(R.string.all_sources)) })
            providers.forEach { p -> FilterChip(provider == p, { provider = if (provider == p) null else p }, { Text(Providers.byId(p)?.name ?: p) }) }
        }
        ChipRow {
            FilterChip(type == null, { type = null }, { Text(stringResource(R.string.all_types)) })
            types.forEach { t -> FilterChip(type == t, { type = if (type == t) null else t }, { Text(t) }) }
        }
        ChipRow {
            FilterChip(favOnly, { favOnly = !favOnly }, { Text(stringResource(R.string.favorites)) }, leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) })
            ReviewStatus.entries.forEach { r ->
                FilterChip(review == r, { review = if (review == r) null else r }, { Text(stringResource(reviewRes(r))) }, leadingIcon = { Icon(reviewIcon(r), null, Modifier.size(16.dp)) })
            }
        }
        Text(stringResource(R.string.showing_n_of_m, shown.size, rows.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
        // Grouped by the query that produced them: target · source · run time.
        val groups = shown.groupBy { it.queryRunId }
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (runId, list) ->
                val first = list.first()
                item(key = "h_$runId") {
                    var open by rememberSaveable(runId) { mutableStateOf(true) }
                    Card(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clickable { open = !open },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${first.targetValue} · ${Providers.byId(first.providerId)?.name ?: first.providerId}", style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.group_meta, formatTime(first.runStartedAt), list.size), style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(if (open) R.string.hide_details else R.string.show_details))
                        }
                    }
                    if (open) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { f -> FindingItem(f) { openFinding(f.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun FindingItem(f: FindingRow, onClick: () -> Unit) {
    val reviewText = stringResource(reviewRes(f.reviewStatus))
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${f.type} · ${f.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(f.value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.finding_meta, Providers.byId(f.providerId)?.name ?: f.providerId, f.targetValue, formatTime(f.retrievedAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (f.favorite) Icon(Icons.Filled.Star, stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.tertiary)
            Icon(reviewIcon(f.reviewStatus), null, Modifier.padding(start = 6.dp).semantics { contentDescription = reviewText })
        }
    }
}

/** All coordinates found in this investigation on one map, with a text list as the accessible alternative. */
@Composable
private fun MapTab(rows: List<FindingRow>, openFinding: (String) -> Unit, openFullMap: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coords = rows.filter { it.label == COORDINATES_LABEL }.mapNotNull { r -> parseCoordinates(r.value)?.let { r to it } }
    if (coords.isEmpty()) return EmptyState(stringResource(R.string.map_empty), Modifier.fillMaxSize())
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.geo_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        item {
            OsmMap(
                coords.map { (r, c) -> MapPoint(c.first, c.second, r.targetValue, Providers.byId(r.providerId)?.name ?: r.providerId) },
                Modifier.fillMaxWidth().height(360.dp),
            )
            Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = openFullMap, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_full_map))
            }
        }
        items(coords, key = { it.first.id }) { (r, c) ->
            Card(Modifier.fillMaxWidth().clickable { openFinding(r.id) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.targetValue, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.lat_lon, c.first, c.second), style = MaterialTheme.typography.bodyMedium)
                        Text(Providers.byId(r.providerId)?.name ?: r.providerId, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { openInMapApp(context, c.first, c.second, r.targetValue) }) {
                        Icon(Icons.Filled.Map, stringResource(R.string.open_in_map_app))
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationsTab(app: OsintApp, relations: List<Relation>, targets: List<Target>, findings: List<FindingRow>) {
    val scope = rememberCoroutineScope()
    if (relations.isEmpty()) return EmptyState(stringResource(R.string.empty_relations), Modifier.fillMaxSize())
    val names = nodeNames(targets, findings)
    // §1 Relations: an accessible list is the primary view of nodes and edges.
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(relations, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(names[r.fromId] ?: "?", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                            Text(" ${r.type}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(names[r.toId] ?: "?", style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(assertionRes(r.assertionType)), style = MaterialTheme.typography.labelSmall)
                        if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { scope.launch { app.repo.deleteRelation(r) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
        }
    }
}

fun nodeNames(targets: List<Target>, findings: List<FindingRow>): Map<String, String> =
    targets.associate { it.id to "${it.type.name}: ${it.displayValue}" } + findings.associate { it.id to "${it.label}: ${it.value.take(80)}" }

fun assertionRes(a: io.github.smyrna_pergamum.osintbook.data.AssertionType) = when (a) {
    io.github.smyrna_pergamum.osintbook.data.AssertionType.SOURCE_STATED -> R.string.assert_source
    io.github.smyrna_pergamum.osintbook.data.AssertionType.INFERRED -> R.string.assert_inferred
}

@Composable
private fun NotesTab(app: OsintApp, notes: List<Note>, edit: (Note) -> Unit) {
    val scope = rememberCoroutineScope()
    if (notes.isEmpty()) return EmptyState(stringResource(R.string.empty_notes), Modifier.fillMaxSize())
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(notes, key = { it.id }) { n -> NoteCard(n, edit = { edit(n) }, delete = { scope.launch { app.repo.deleteNote(n) } }) }
    }
}

@Composable
fun NoteCard(n: Note, edit: () -> Unit, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(n.content, style = MaterialTheme.typography.bodyMedium)
                Text(formatTime(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = edit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
            IconButton(onClick = delete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
    }
}

@Composable
fun NoteDialog(n: Note, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(n.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note)) },
        text = { OutlinedTextField(text, { text = it.take(20_000) }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AddTargetDialog(initial: String, onDismiss: () -> Unit, onSave: (TargetType, String, ParseResult.Ok) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var manualType by rememberSaveable { mutableStateOf<TargetType?>(null) }
    val type = manualType ?: TargetParser.detect(value)
    val result = if (value.isBlank()) null else TargetParser.parse(type, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_target)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRowPlain {
                    TargetType.entries.forEach { t -> FilterChip(type == t, { manualType = t }, { Text(targetTypeLabel(t)) }) }
                }
                val focus = remember { FocusRequester() }
                OutlinedTextField(value, { value = it.take(2048) }, label = { Text(targetTypeHint(type)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                LaunchedEffect(Unit) { focus.requestFocus() }
                when (result) {
                    null -> Unit
                    is ParseResult.Error -> Text(stringResource(R.string.invalid_input, targetTypeLabel(type)), color = MaterialTheme.colorScheme.error)
                    is ParseResult.Ok -> {
                        Text(stringResource(R.string.normalized_as, result.normalized), style = MaterialTheme.typography.bodySmall)
                        if (result.display != result.normalized) Text(stringResource(R.string.unicode_as, result.display), style = MaterialTheme.typography.bodySmall)
                        result.warnings.forEach { w -> Text(stringResource(warningRes(w)), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                        result.cleaned?.let { c -> OutlinedButton(onClick = { value = c }) { Text(stringResource(R.string.remove_secrets)) } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = result is ParseResult.Ok, onClick = { (result as? ParseResult.Ok)?.let { onSave(type, value.trim(), it) } }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChipRowPlain(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

fun warningRes(w: Warning) = when (w) {
    Warning.SECRET_PARAMS -> R.string.warn_secret
    Warning.USERINFO -> R.string.warn_userinfo
    Warning.PRIVATE_IP -> R.string.warn_private_ip
    Warning.NO_VERSION -> R.string.warn_no_version
}
