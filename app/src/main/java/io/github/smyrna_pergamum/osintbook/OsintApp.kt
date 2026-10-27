package io.github.smyrna_pergamum.osintbook

import android.app.Application
import io.github.smyrna_pergamum.osintbook.data.AppDatabase
import io.github.smyrna_pergamum.osintbook.data.Repository
import io.github.smyrna_pergamum.osintbook.data.SettingsStore
import io.github.smyrna_pergamum.osintbook.domain.QueryEngine
import io.github.smyrna_pergamum.osintbook.domain.RawStore
import io.github.smyrna_pergamum.osintbook.net.Http
import io.github.smyrna_pergamum.osintbook.security.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency container; no network work happens here (SEC-02, AT-10). */
class OsintApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: AppDatabase
    lateinit var settings: SettingsStore
    lateinit var repo: Repository
    lateinit var engine: QueryEngine
    lateinit var http: Http

    /** FR-09: text the user shared into the app, waiting to be placed in an investigation. */
    val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        val vault = KeyVault(this)
        val raw = RawStore(filesDir.resolve("raw"))
        repo = Repository(db, raw, vault)
        http = Http()
        engine = QueryEngine(db, http, vault, raw, scope, keepRaw = { settings.flow.first().keepRaw })
        scope.launch {
            db.queryRuns().markInterrupted()
            repo.ensureProviderRows()
            repo.purgeOldRaw()
        }
    }
}
