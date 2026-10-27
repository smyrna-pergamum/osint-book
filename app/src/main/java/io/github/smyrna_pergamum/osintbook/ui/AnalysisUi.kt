package io.github.smyrna_pergamum.osintbook.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.domain.Action
import io.github.smyrna_pergamum.osintbook.domain.Analysis
import io.github.smyrna_pergamum.osintbook.domain.Kind
import io.github.smyrna_pergamum.osintbook.domain.Level
import io.github.smyrna_pergamum.osintbook.domain.Observation
import io.github.smyrna_pergamum.osintbook.providers.Providers

private fun kindRes(k: Kind): Int = when (k) {
    Kind.SUMMARY -> R.string.an_summary
    Kind.REVIEW_STATE -> R.string.an_review_state
    Kind.DOMAIN_AGE -> R.string.an_domain_age
    Kind.NEW_DOMAIN -> R.string.an_new_domain
    Kind.EXPIRES_SOON -> R.string.an_expires_soon
    Kind.EXPIRED -> R.string.an_expired
    Kind.RECENTLY_CHANGED -> R.string.an_recently_changed
    Kind.STATUS_HOLD -> R.string.an_status_hold
    Kind.STATUS_DELETE -> R.string.an_status_delete
    Kind.DOMAIN_LOCKED -> R.string.an_domain_locked
    Kind.REGISTRAR -> R.string.an_registrar
    Kind.NO_ADDRESS -> R.string.an_no_address
    Kind.IPV6_YES -> R.string.an_ipv6_yes
    Kind.IPV6_NO -> R.string.an_ipv6_no
    Kind.NO_MX -> R.string.an_no_mx
    Kind.NO_SPF -> R.string.an_no_spf
    Kind.SPF_STRICT -> R.string.an_spf_strict
    Kind.SPF_SOFT -> R.string.an_spf_soft
    Kind.SPF_WEAK -> R.string.an_spf_weak
    Kind.NO_DMARC -> R.string.an_no_dmarc
    Kind.DMARC_NONE -> R.string.an_dmarc_none
    Kind.DMARC_POLICY -> R.string.an_dmarc_policy
    Kind.NO_CAA -> R.string.an_no_caa
    Kind.DNSSEC_ON -> R.string.an_dnssec_on
    Kind.DNSSEC_OFF -> R.string.an_dnssec_off
    Kind.CDN -> R.string.an_cdn
    Kind.GEO_SPREAD -> R.string.an_geo_spread
    Kind.GEO_FAR -> R.string.an_geo_far
    Kind.COUNTRY_MISMATCH -> R.string.an_country_mismatch
    Kind.COARSE_LOCATION -> R.string.an_coarse_location
    Kind.NETWORK_OWNER -> R.string.an_network_owner
    Kind.VT_FLAGGED -> R.string.an_vt_flagged
    Kind.VT_CLEAN -> R.string.an_vt_clean
    Kind.ABUSE_HIGH -> R.string.an_abuse_high
    Kind.ABUSE_LOW -> R.string.an_abuse_low
    Kind.URLHAUS_LISTED -> R.string.an_urlhaus_listed
    Kind.URLHAUS_CLEAN -> R.string.an_urlhaus_clean
    Kind.SB_MATCH -> R.string.an_sb_match
    Kind.SB_CLEAN -> R.string.an_sb_clean
    Kind.OTX_PULSES -> R.string.an_otx_pulses
    Kind.OTX_NONE -> R.string.an_otx_none
    Kind.MB_KNOWN -> R.string.an_mb_known
    Kind.MB_UNKNOWN -> R.string.an_mb_unknown
    Kind.SUGGEST_ADD_IP -> R.string.an_suggest_add_ip
    Kind.SUGGEST_RELATION -> R.string.an_suggest_relation
    Kind.ARCHIVE_BEFORE_REGISTRATION -> R.string.an_archive_before_registration
    Kind.VULNS -> R.string.an_vulns
    Kind.VULN_NO_VERSION -> R.string.an_vuln_no_version
    Kind.NO_VULNS -> R.string.an_no_vulns
    Kind.REPO_STALE -> R.string.an_repo_stale
    Kind.REPO_ARCHIVED -> R.string.an_repo_archived
    Kind.REPO_NO_LICENSE -> R.string.an_repo_no_license
    Kind.PROVIDERS_FAILED -> R.string.an_providers_failed
    Kind.NOT_QUERIED -> R.string.an_not_queried
    Kind.KEYS_MISSING -> R.string.an_keys_missing
}

fun levelRes(l: Level) = when (l) {
    Level.INFO -> R.string.lvl_info
    Level.WARN -> R.string.lvl_warn
    Level.MISSING -> R.string.lvl_missing
}

private fun levelIcon(l: Level): ImageVector = when (l) {
    Level.INFO -> Icons.Filled.Info
    Level.WARN -> Icons.Filled.WarningAmber
    Level.MISSING -> Icons.Filled.HelpOutline
}

private fun age(ctx: Context, days: Long): String = when {
    days >= 730 -> ctx.getString(R.string.age_years, (days / 365).toInt())
    days >= 60 -> ctx.getString(R.string.age_months, (days / 30).toInt())
    else -> ctx.getString(R.string.age_days, days.toInt())
}

/** Localised sentence for one observation; shared by the screen and the PDF. */
fun observationText(ctx: Context, o: Observation): String {
    // Values such as "Example Inc." already end in a dot; the sentence adds its own.
    val args: List<Any> = if (o.kind == Kind.DOMAIN_AGE) listOf(age(ctx, o.args[0].toLong()), o.args[1]) else o.args.map { it.trimEnd('.') }
    return ctx.getString(kindRes(o.kind), *args.toTypedArray())
}

/** Observations grouped as they appear: whole investigation first, then per target in target order. */
fun groupedObservations(a: Analysis, targets: List<Target>): List<Pair<Target?, List<Observation>>> {
    val general = a.observations.filter { it.targetId == null }
    val out = mutableListOf<Pair<Target?, List<Observation>>>()
    if (general.isNotEmpty()) out += null to general
    targets.forEach { t -> a.observations.filter { it.targetId == t.id }.takeIf { it.isNotEmpty() }?.let { out += t to it.sortedBy { o -> if (o.kind == Kind.SUMMARY) -1 else when (o.level) { Level.WARN -> 0; Level.MISSING -> 1; Level.INFO -> 2 } } } }
    return out
}

@Composable
fun AnalysisTab(
    analysis: Analysis, targets: List<Target>, openFinding: (String) -> Unit, onAction: (Action) -> Unit,
) {
    val ctx = LocalContext.current
    if (analysis.observations.isEmpty()) return EmptyState(stringResource(R.string.an_empty), Modifier.fillMaxSize())
    val groups = groupedObservations(analysis, targets)
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.an_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (t, obs) ->
            item(key = "g_" + (t?.id ?: "general")) {
                Text(t?.let { "${targetTypeLabel(it.type)}: ${it.displayValue}" } ?: stringResource(R.string.an_general),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(obs.size, key = { i -> "o_${t?.id}_$i" }) { i -> ObservationCard(ctx, obs[i], openFinding, onAction) }
        }
        if (analysis.timeline.isNotEmpty()) {
            item { Text(stringResource(R.string.an_timeline), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(analysis.timeline, key = { "tl_" + it.findingId }) { e ->
                val target = targets.firstOrNull { it.id == e.targetId }?.displayValue ?: ""
                Row(Modifier.fillMaxWidth().clickable { openFinding(e.findingId) }.padding(vertical = 6.dp)) {
                    Text(e.at.take(10), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.label} · ${e.value}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text("$target · ${Providers.byId(e.providerId)?.name ?: e.providerId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(ctx: Context, o: Observation, openFinding: (String) -> Unit, onAction: (Action) -> Unit) {
    val (container, tint) = when (o.level) {
        Level.WARN -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Level.MISSING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Level.INFO -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // UX-02: level told by icon and word, not colour alone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(levelIcon(o.level), null, Modifier.size(18.dp), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(levelRes(o.level)), style = MaterialTheme.typography.labelLarge, color = tint)
            }
            Text(observationText(ctx, o), style = MaterialTheme.typography.bodyMedium, color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (o.evidence.isNotEmpty()) {
                    Text(stringResource(R.string.an_evidence, o.evidence.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { openFinding(o.evidence.first()) }.padding(vertical = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                when (val a = o.action) {
                    is Action.AddTarget -> OutlinedButton(onClick = { onAction(a) }) { Text(stringResource(R.string.an_add_target)) }
                    is Action.AddRelation -> OutlinedButton(onClick = { onAction(a) }) { Text(stringResource(R.string.add_relation)) }
                    Action.OpenSettings -> OutlinedButton(onClick = { onAction(a) }) { Text(stringResource(R.string.settings)) }
                    null -> Unit
                }
            }
        }
    }
}
