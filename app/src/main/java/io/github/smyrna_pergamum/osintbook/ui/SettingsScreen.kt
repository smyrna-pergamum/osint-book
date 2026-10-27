package io.github.smyrna_pergamum.osintbook.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.BuildConfig
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.AppSettings
import io.github.smyrna_pergamum.osintbook.data.ProviderSettings
import io.github.smyrna_pergamum.osintbook.data.Time
import io.github.smyrna_pergamum.osintbook.export.ImportException
import io.github.smyrna_pergamum.osintbook.export.JsonTransfer
import io.github.smyrna_pergamum.osintbook.providers.KeyNeed
import io.github.smyrna_pergamum.osintbook.providers.Provider
import io.github.smyrna_pergamum.osintbook.providers.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/** What each source gives, shown in Settings so users know why they might turn it on. */
fun providerDescRes(id: String): Int = when (id) {
    "google_dns" -> R.string.desc_google_dns
    "rdap" -> R.string.desc_rdap
    "wayback" -> R.string.desc_wayback
    "github" -> R.string.desc_github
    "wikipedia" -> R.string.desc_wikipedia
    "osv" -> R.string.desc_osv
    "virustotal" -> R.string.desc_virustotal
    "abuseipdb" -> R.string.desc_abuseipdb
    "otx" -> R.string.desc_otx
    "ipwhois", "ipapico", "geojs" -> R.string.desc_ipgeo
    "bigdatacloud" -> R.string.desc_bigdatacloud
    "ping" -> R.string.desc_ping
    "urlhaus" -> R.string.desc_urlhaus
    "malwarebazaar" -> R.string.desc_malwarebazaar
    "safebrowsing" -> R.string.desc_safebrowsing
    "urlscan" -> R.string.desc_urlscan
    "nominatim" -> R.string.desc_nominatim
    else -> R.string.unknown
}

/** Step-by-step key instructions for sources that use one (UX-03). */
private fun keyStepsRes(id: String): Int? = when (id) {
    "github" -> R.string.steps_github
    "virustotal" -> R.string.steps_virustotal
    "abuseipdb" -> R.string.steps_abuseipdb
    "otx" -> R.string.steps_otx
    "urlhaus", "malwarebazaar" -> R.string.steps_abusech
    "safebrowsing" -> R.string.steps_safebrowsing
    "urlscan" -> R.string.steps_urlscan
    "bigdatacloud" -> R.string.steps_bigdatacloud
    else -> null
}

/** §1 Sources and settings, grouped into sections with plain explanations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: OsintApp, settings: AppSettings, back: () -> Unit, openInvestigation: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val providerSettings by app.db.providerSettings().observeAll().collectAsState(initial = emptyList())
    var confirmWipe by remember { mutableStateOf(false) }
    var rawSize by remember { mutableStateOf(app.repo.rawSize()) }
    var keyVersion by remember { mutableIntStateOf(0) } // re-reads key presence after changes
    val importFailed = stringResource(R.string.import_failed)
    val keySaved = stringResource(R.string.key_saved)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // FR-14: the size is checked before the whole file is read.
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { s ->
                        val buf = s.readNBytesCompat(JsonTransfer.MAX_BYTES + 1)
                        if (buf.size > JsonTransfer.MAX_BYTES) throw ImportException("too_large")
                        buf
                    }
                    app.repo.importJson(bytes)
                }
            }
            result.onSuccess { openInvestigation(it.id) }
                .onFailure { e -> snack.showSnackbar(importFailed + " (" + ((e as? ImportException)?.reason ?: "error") + ")") }
        }
    }

    val keyed = Providers.all.filter { it.keyNeed != KeyNeed.NONE }
    val free = Providers.all.filter { it.keyNeed == KeyNeed.NONE }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.settings)) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Section(Icons.Filled.Palette, stringResource(R.string.sec_appearance)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                    LanguagePicker()
                    Spacer(Modifier.heightIn(min = 4.dp))
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                    ThemePicker(settings.theme) { m -> scope.launch { app.settings.setTheme(m) } }
                }
            }

            item {
                SectionHeader(Icons.Filled.Key, stringResource(R.string.sec_keys))
                InfoBox(stringResource(R.string.sec_keys_intro))
            }
            items(keyed, key = { "k_" + it.id }) { p ->
                ProviderCard(app, p, providerSettings.firstOrNull { it.providerId == p.id } ?: ProviderSettings(p.id), keyVersion) { saved ->
                    keyVersion++
                    if (saved) scope.launch { snack.showSnackbar(keySaved) }
                }
            }

            item {
                SectionHeader(Icons.Filled.LockOpen, stringResource(R.string.sec_free_sources))
                InfoBox(stringResource(R.string.sec_free_intro))
            }
            items(free, key = { "f_" + it.id }) { p ->
                ProviderCard(app, p, providerSettings.firstOrNull { it.providerId == p.id } ?: ProviderSettings(p.id), keyVersion) { keyVersion++ }
            }

            item {
                Section(Icons.Filled.Storage, stringResource(R.string.sec_data)) {
                    SwitchRow(stringResource(R.string.keep_raw), stringResource(R.string.keep_raw_body), settings.keepRaw) { v ->
                        scope.launch { app.settings.setKeepRaw(v) }
                    }
                    Text(stringResource(R.string.raw_size, rawSize / 1024), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { scope.launch { app.repo.clearRaw(); rawSize = app.repo.rawSize() } }) { Text(stringResource(R.string.clear_raw)) }
                    HorizontalDivider()
                    Text(stringResource(R.string.import_json_body), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.import_json)) }
                    HorizontalDivider()
                    Text(stringResource(R.string.db_not_encrypted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { confirmWipe = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.delete_all)) }
                }
            }
            item {
                Section(Icons.Filled.Policy, stringResource(R.string.privacy)) {
                    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.privacy_map), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Section(Icons.Filled.Info, stringResource(R.string.sec_about)) {
                    Text(stringResource(R.string.app_license, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    LICENSES.forEach { (name, lic) -> Text("• $name — $lic", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (confirmWipe) ConfirmDialog(
        stringResource(R.string.delete_all), stringResource(R.string.delete_all_body), stringResource(R.string.delete),
        onConfirm = { scope.launch { app.repo.deleteEverything(); app.repo.deleteAllKeys(); rawSize = 0; keyVersion++ } },
        onDismiss = { confirmWipe = false },
    )
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    SectionHeader(icon, title)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun InfoBox(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onCheckedChange = null)
    }
}

/** UX-02: key state told by icon and words. */
@Composable
private fun KeyStateChip(p: Provider, hasKey: Boolean) {
    val (icon, text, color) = when {
        p.keyNeed == KeyNeed.NONE -> Triple(Icons.Filled.CheckCircle, stringResource(R.string.chip_no_key), MaterialTheme.colorScheme.primary)
        hasKey -> Triple(Icons.Filled.CheckCircle, stringResource(R.string.chip_key_saved), MaterialTheme.colorScheme.primary)
        p.keyNeed == KeyNeed.OPTIONAL -> Triple(Icons.Filled.Key, stringResource(R.string.chip_key_optional), MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.WarningAmber, stringResource(R.string.chip_key_missing), MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ProviderCard(app: OsintApp, p: Provider, s: ProviderSettings, keyVersion: Int, onKeyChanged: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(p.id) { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val hasKey = remember(keyVersion, s) { app.repo.hasKey(p.id) }
    val expandLabel = stringResource(if (expanded) R.string.hide_details else R.string.show_details)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = expandLabel) { expanded = !expanded }.heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    KeyStateChip(p, hasKey)
                }
                Switch(s.enabled, { v -> scope.launch { app.repo.setProviderEnabled(p.id, v) } })
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, expandLabel, Modifier.padding(start = 8.dp))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(end = 8.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(providerDescRes(p.id)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.supports, p.supports.joinToString { it.name }), style = MaterialTheme.typography.bodySmall)
                    Text(if (p.active) stringResource(R.string.active_warning) else stringResource(R.string.sends_to_host, p.host),
                        style = MaterialTheme.typography.bodySmall, color = if (p.active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)

                    // NET-05: our counter is labelled as ours; the provider's quota is shown only when it reports one.
                    val today = if (s.dayKey == LocalDate.now(ZoneOffset.UTC).toString()) s.requestsToday else 0
                    Text(stringResource(R.string.quota_app_counter, today, p.dailyLimit?.toString() ?: stringResource(R.string.unknown)), style = MaterialTheme.typography.labelSmall)
                    s.quotaState?.let { Text(stringResource(R.string.quota_provider, it), style = MaterialTheme.typography.labelSmall) }
                    if (s.blockedUntil > System.currentTimeMillis()) Text(stringResource(R.string.blocked_until, formatTime(Time.iso(s.blockedUntil))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                    if (p.keyNeed != KeyNeed.NONE) {
                        HorizontalDivider()
                        if (hasKey) {
                            Text(stringResource(R.string.key_saved_long), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { scope.launch { app.repo.deleteKey(p.id); onKeyChanged(false) } }) { Text(stringResource(R.string.delete_key)) }
                        } else {
                            Text(stringResource(R.string.how_to_key), style = MaterialTheme.typography.titleSmall)
                            keyStepsRes(p.id)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                            p.signupUrl?.let { url ->
                                OutlinedButton(onClick = { openLink(context, url) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.open_signup))
                                }
                            }
                            OutlinedTextField(
                                keyInput, { keyInput = it.trim().take(512) },
                                label = { Text(stringResource(R.string.api_key)) },
                                supportingText = { Text(stringResource(R.string.key_where_hint)) },
                                singleLine = true,
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            stringResource(if (showKey) R.string.hide_key else R.string.show_key))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(enabled = keyInput.length >= 8, onClick = {
                                scope.launch { app.repo.saveKey(p.id, keyInput); keyInput = ""; showKey = false; onKeyChanged(true) }
                            }) { Text(stringResource(R.string.save_key)) }
                        }
                    }
                    TextButton(onClick = { openLink(context, p.docsUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.provider_docs))
                    }
                }
            }
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

/** DEL-05: direct dependencies and their licences. */
private val LICENSES = listOf(
    "AndroidX (Compose, Room, Navigation, DataStore, AppCompat, Lifecycle)" to "Apache-2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache-2.0",
    "OkHttp, Okio" to "Apache-2.0",
    "osmdroid" to "Apache-2.0",
    "Material Icons" to "Apache-2.0",
    "Map data © OpenStreetMap contributors" to "ODbL-1.0",
)
