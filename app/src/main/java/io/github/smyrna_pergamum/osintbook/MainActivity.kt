package io.github.smyrna_pergamum.osintbook

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.smyrna_pergamum.osintbook.ui.AppNav
import io.github.smyrna_pergamum.osintbook.ui.OsintTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) takeShared(intent)
        val app = application as OsintApp
        setContent {
            val settings by app.settings.flow.collectAsState(initial = null)
            settings?.let { s -> OsintTheme(s.theme) { AppNav(app, s) } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShared(intent)
    }

    /** FR-09: only the text or link the user explicitly shared; no clipboard or history access. */
    private fun takeShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.take(2048) ?: return
        // Browsers often share "Title https://…"; keep the link when there is one.
        val link = Regex("https?://\\S+").find(text)?.value
        (application as OsintApp).sharedText.value = link ?: text
    }
}
