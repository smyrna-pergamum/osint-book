package io.github.smyrna_pergamum.osintbook.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.AppSettings
import io.github.smyrna_pergamum.osintbook.data.ThemeMode
import kotlinx.coroutines.launch

/** §1 Start screen: explains local storage and where queries go. Makes no network request (SEC-02). */
@Composable
fun OnboardingScreen(app: OsintApp, settings: AppSettings, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
            InfoCard(Icons.Filled.Lock, R.string.onb_local_title, R.string.onb_local_body)
            InfoCard(Icons.Filled.Public, R.string.onb_network_title, R.string.onb_network_body)
            InfoCard(Icons.Filled.Shield, R.string.onb_scope_title, R.string.onb_scope_body)
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            LanguagePicker()
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            ThemePicker(settings.theme) { m -> scope.launch { app.settings.setTheme(m) } }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { app.settings.setOnboarded(); done() } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.onb_start))
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: Int, body: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Languages shipped with the app, named in their own language. */
val APP_LANGUAGES = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español",
    "it" to "Italiano", "pt" to "Português", "ru" to "Русский", "ar" to "العربية")

/** UX-01: per-app language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker() {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = current.isEmpty(), onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
            label = { Text(stringResource(R.string.lang_system)) })
        APP_LANGUAGES.forEach { (tag, name) ->
            FilterChip(
                selected = current.startsWith(tag),
                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, label) ->
            FilterChip(selected = current == m, onClick = { onPick(m) }, label = { Text(stringResource(label)) })
        }
    }
}
