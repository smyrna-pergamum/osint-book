package io.github.smyrna_pergamum.osintbook.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.ProviderSettings
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.providers.KeyNeed
import io.github.smyrna_pergamum.osintbook.providers.Provider
import io.github.smyrna_pergamum.osintbook.providers.Providers

/**
 * New query (§1): the user picks sources, sees exactly what goes to which host and how many
 * requests it costs (FR-03, NET-03), then watches progress and can cancel (NET-06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryScreen(app: OsintApp, targetId: String, back: () -> Unit, openSettings: () -> Unit) {
    val context = LocalContext.current
    var target by remember { mutableStateOf<Target?>(null) }
    LaunchedEffect(targetId) { target = app.db.targets().get(targetId) }
    val settingsList by app.db.providerSettings().observeAll().collectAsState(initial = emptyList())
    val progressAll by app.engine.progress.collectAsState()
    val runs by remember(targetId) { app.db.queryRuns().observeFor(targetId) }.collectAsState(initial = emptyList())
    val selected = remember { mutableStateListOf<String>() }
    var initialised by remember { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val t = target

    val settings = settingsList.associateBy { it.providerId }
    val candidates = t?.let { Providers.forType(it.type) }.orEmpty()
    fun usable(p: Provider): Boolean {
        val s = settings[p.id] ?: ProviderSettings(p.id)
        return s.enabled && (p.keyNeed != KeyNeed.REQUIRED || app.repo.hasKey(p.id))
    }
    LaunchedEffect(candidates, settingsList) {
        if (!initialised && t != null && settingsList.isNotEmpty()) {
            // Only key-free sources are preselected; nothing runs until the user taps Start (SEC-02).
            selected.addAll(candidates.filter { usable(it) && it.keyNeed != KeyNeed.REQUIRED && !it.active }.map { it.id })
            initialised = true
        }
    }

    val progress = progressAll[targetId].orEmpty()
    val running = progress.values.any { !it.status.isFinal } && app.engine.isRunning(targetId)
    val online = remember { isOnline(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.query_title)) },
            )
        },
    ) { pad ->
        if (t == null) return@Scaffold
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(targetTypeLabel(t.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(t.displayValue, style = MaterialTheme.typography.titleLarge)
            }
            if (!online) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.offline_hint))
                    }
                }
            }
            item { Text(stringResource(R.string.sources_choose), style = MaterialTheme.typography.titleMedium) }
            items(candidates, key = { it.id }) { p ->
                val s = settings[p.id] ?: ProviderSettings(p.id)
                ProviderRow(app, p, s, t, checked = p.id in selected, enabled = !running && usable(p),
                    progressStatus = progress[p.id]?.status, progressReq = progress[p.id]?.requests ?: 0,
                    newFindings = progress[p.id]?.newFindings, progressError = progress[p.id]?.errorCode, openSettings = openSettings) { on ->
                    if (on) selected.add(p.id) else selected.remove(p.id)
                }
            }
            item {
                val chosen = candidates.filter { it.id in selected && usable(it) }
                val total = chosen.sumOf { it.estimateRequests(t) }
                Text(stringResource(R.string.estimate_total, total, chosen.size), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.concurrency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { app.engine.cancel(targetId) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cancel_query))
                    }
                } else {
                    Button(enabled = chosen.isNotEmpty(), onClick = { app.engine.start(t, chosen, language) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.start_query))
                    }
                }
            }
            if (!running && progress.isNotEmpty()) item {
                val rows by remember(t.investigationId) { app.db.findings().observeRows(t.investigationId) }.collectAsState(initial = emptyList())
                val mine = rows.filter { it.targetId == t.id }
                val subject = stringResource(R.string.share_results_subject, t.displayValue)
                val statusLines = progress.values.map { p -> "${Providers.byId(p.providerId)?.name ?: p.providerId}: ${context.getString(runStatusRes(p.status))}" }
                OutlinedButton(enabled = mine.isNotEmpty(), onClick = {
                    val body = buildString {
                        appendLine(subject)
                        statusLines.forEach { appendLine("• $it") }
                        appendLine()
                        mine.groupBy { it.providerId }.forEach { (pid, list) ->
                            appendLine("[${Providers.byId(pid)?.name ?: pid}]")
                            list.forEach { appendLine("${it.type} · ${it.label}: ${it.value}\n  ${it.sourceUrl} (${it.retrievedAt} UTC)") }
                            appendLine()
                        }
                    }
                    shareText(context, subject, body)
                }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_results))
                }
                Text(stringResource(R.string.share_full_report_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (runs.isNotEmpty()) {
                item { Text(stringResource(R.string.run_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                items(runs.take(50), key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Providers.byId(r.providerId)?.name ?: r.providerId, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.run_meta, formatTime(r.startedAt), r.requestCount, r.errorCode ?: "—"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            errorText(r.errorCode)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        RunStatusChip(r.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    app: OsintApp, p: Provider, s: ProviderSettings, t: Target, checked: Boolean, enabled: Boolean,
    progressStatus: RunStatus?, progressReq: Int, newFindings: Int?, progressError: String?, openSettings: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val hasKey = app.repo.hasKey(p.id)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    // FR-03: what leaves the device and where it goes.
                    Text(if (p.active) stringResource(R.string.sends_direct, p.sentValue(t)) else stringResource(R.string.sends_to, p.sentValue(t), p.host),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.est_requests, p.estimateRequests(t)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (p.active) Text(stringResource(R.string.active_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            if (!s.enabled) Text(stringResource(R.string.source_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            // UX-03: why a module cannot run and where to get a key.
            if (p.keyNeed == KeyNeed.REQUIRED && !hasKey) {
                Text(stringResource(R.string.key_required_reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row {
                    p.signupUrl?.let { url -> TextButton(onClick = { openLink(context, url) }) { Text(stringResource(R.string.get_key)) } }
                    TextButton(onClick = openSettings) { Text(stringResource(R.string.enter_key)) }
                }
            }
            if (p.keyNeed == KeyNeed.OPTIONAL && !hasKey) Text(stringResource(R.string.key_optional_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.reportsReputation) Text(stringResource(R.string.reputation_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progressStatus != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RunStatusChip(progressStatus)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.progress_meta, progressReq, newFindings ?: 0), style = MaterialTheme.typography.labelSmall)
                }
                errorText(progressError)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
