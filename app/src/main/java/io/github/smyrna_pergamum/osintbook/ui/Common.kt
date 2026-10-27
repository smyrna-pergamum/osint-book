package io.github.smyrna_pergamum.osintbook.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.data.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Orange brand colours; text-on-primary pairs keep at least 4.5:1 contrast.
private val Light = lightColorScheme(
    primary = Color(0xFFC2410C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9), onPrimaryContainer = Color(0xFF3A1100),
    secondary = Color(0xFF77574A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6D9), onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF6B5E10),
    background = Color(0xFFFFFBF8), surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFF5DED4), onSurfaceVariant = Color(0xFF53433C),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF4EE),
    surfaceContainer = Color(0xFFFCEEE7), surfaceContainerHigh = Color(0xFFF7E8E1),
    surfaceContainerHighest = Color(0xFFF1E2DB),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68F), onPrimary = Color(0xFF552100),
    primaryContainer = Color(0xFF7A3000), onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE7BEAD), onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034), onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFFD8C86F),
    background = Color(0xFF1A120E), surface = Color(0xFF1A120E),
    surfaceVariant = Color(0xFF53433C), onSurfaceVariant = Color(0xFFD8C2B8),
    surfaceContainerLowest = Color(0xFF140D09), surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A), surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D332E),
)

@Composable
fun OsintTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

/** DATA-01: stored UTC, shown in the user's time zone. */
fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    }.getOrDefault(iso)
}

/** SEC-07/08: links open in the user's browser, only on tap and only for web schemes. */
fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Opens the system share sheet with plain text; the user picks the app and completes the share. */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text.take(100_000))
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun targetTypeLabel(t: TargetType): String = stringResource(
    when (t) {
        TargetType.DOMAIN -> R.string.type_domain
        TargetType.URL -> R.string.type_url
        TargetType.IP -> R.string.type_ip
        TargetType.ORG -> R.string.type_org
        TargetType.REPO -> R.string.type_repo
        TargetType.PACKAGE -> R.string.type_package
        TargetType.HASH -> R.string.type_hash
    }
)

@Composable
fun targetTypeHint(t: TargetType): String = stringResource(
    when (t) {
        TargetType.DOMAIN -> R.string.hint_domain
        TargetType.URL -> R.string.hint_url
        TargetType.IP -> R.string.hint_ip
        TargetType.ORG -> R.string.hint_org
        TargetType.REPO -> R.string.hint_repo
        TargetType.PACKAGE -> R.string.hint_package
        TargetType.HASH -> R.string.hint_hash
    }
)

fun runStatusRes(s: RunStatus): Int = when (s) {
    RunStatus.PENDING -> R.string.st_pending
    RunStatus.RUNNING -> R.string.st_running
    RunStatus.SUCCESS -> R.string.st_success
    RunStatus.NO_RESULT -> R.string.st_no_result
    RunStatus.RATE_LIMITED -> R.string.st_rate_limited
    RunStatus.UNAUTHORIZED -> R.string.st_unauthorized
    RunStatus.NO_KEY -> R.string.st_no_key
    RunStatus.NETWORK -> R.string.st_network
    RunStatus.TIMEOUT -> R.string.st_timeout
    RunStatus.SERVER -> R.string.st_server
    RunStatus.PARSE -> R.string.st_parse
    RunStatus.TOO_LARGE -> R.string.st_too_large
    RunStatus.CANCELLED -> R.string.st_cancelled
    RunStatus.UNSUPPORTED -> R.string.st_unsupported
}

private fun runStatusIcon(s: RunStatus): ImageVector = when (s) {
    RunStatus.PENDING -> Icons.Filled.HourglassEmpty
    RunStatus.RUNNING -> Icons.Filled.Sync
    RunStatus.SUCCESS -> Icons.Filled.CheckCircle
    RunStatus.NO_RESULT -> Icons.Filled.RemoveCircleOutline
    RunStatus.RATE_LIMITED -> Icons.Filled.Schedule
    RunStatus.UNAUTHORIZED, RunStatus.NO_KEY -> Icons.Filled.Key
    RunStatus.NETWORK -> Icons.Filled.CloudOff
    RunStatus.TIMEOUT -> Icons.Filled.Timer
    RunStatus.CANCELLED, RunStatus.UNSUPPORTED -> Icons.Filled.Block
    RunStatus.SERVER, RunStatus.PARSE, RunStatus.TOO_LARGE -> Icons.Filled.ErrorOutline
}

/** UX-02: status is told by icon and text, never by colour alone. */
@Composable
fun RunStatusChip(s: RunStatus, modifier: Modifier = Modifier) {
    val color = when (s) {
        RunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        RunStatus.PENDING, RunStatus.RUNNING, RunStatus.NO_RESULT, RunStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(runStatusIcon(s), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(runStatusRes(s)), color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Plain-language reason for a run's error code; null when the code needs no explanation. */
@Composable
fun errorText(code: String?): String? = when {
    code == null -> null
    code == "no_scans" -> stringResource(R.string.err_no_scans)
    code.startsWith("no_rdap_for_tld:") -> stringResource(R.string.err_no_rdap_tld, code.substringAfter(':'))
    code == "no_rdap_for_range" -> stringResource(R.string.err_no_rdap_range)
    code == "not_listed" -> stringResource(R.string.err_not_listed)
    code == "redirect_blocked" -> stringResource(R.string.err_redirect)
    code == "http_429" || code == "daily_app_counter" -> stringResource(R.string.err_rate_limited)
    code.startsWith("blocked_until:") -> stringResource(R.string.blocked_until, formatTime(code.substringAfter(':')))
    code == "key_unreadable" -> stringResource(R.string.err_key_unreadable)
    code == "ping_unavailable" -> stringResource(R.string.err_ping_unavailable)
    code.startsWith("http_5") -> stringResource(R.string.err_server, code.removePrefix("http_"))
    else -> null
}

fun reviewRes(s: ReviewStatus) = when (s) {
    ReviewStatus.UNREVIEWED -> R.string.rv_unreviewed
    ReviewStatus.VERIFIED -> R.string.rv_verified
    ReviewStatus.CONFLICTING -> R.string.rv_conflicting
}

fun reviewIcon(s: ReviewStatus): ImageVector = when (s) {
    ReviewStatus.UNREVIEWED -> Icons.AutoMirrored.Filled.HelpOutline
    ReviewStatus.VERIFIED -> Icons.Filled.Verified
    ReviewStatus.CONFLICTING -> Icons.Filled.WarningAmber
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
