package io.github.smyrna_pergamum.osintbook.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** DATA-02: raw provider responses are kept only when the user turns this on. */
    val keepRaw: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private val onboarded = booleanPreferencesKey("onboarded")
    private val theme = stringPreferencesKey("theme")
    private val keepRaw = booleanPreferencesKey("keep_raw")

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            onboarded = p[onboarded] ?: false,
            theme = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            keepRaw = p[keepRaw] ?: false,
        )
    }

    suspend fun setOnboarded() = context.store.edit { it[onboarded] = true }
    suspend fun setTheme(m: ThemeMode) = context.store.edit { it[theme] = m.name }
    suspend fun setKeepRaw(v: Boolean) = context.store.edit { it[keepRaw] = v }
}
