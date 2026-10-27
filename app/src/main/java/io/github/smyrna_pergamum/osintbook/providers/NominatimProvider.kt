package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import java.util.Locale

/**
 * Reverse geocoding of coordinates another source found for an IP. Zoom 10 asks for city level on
 * purpose: IP coordinates are never precise enough for a street address. Started only from a
 * coordinates finding; Nominatim's policy allows at most one request per second.
 */
class NominatimProvider : Provider {
    override val id = "nominatim"
    override val name = "OpenStreetMap Nominatim"
    override val version = 1
    override val host = "nominatim.openstreetmap.org"
    override val supports = setOf(TargetType.IP)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://operations.osmfoundation.org/policies/nominatim/"
    override val minIntervalMs = 1_100L
    override val manualOnly = true

    override fun sentValue(target: Target) = "lat/lon"
    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val lat = ctx.extra["lat"]?.toDoubleOrNull()
        val lon = ctx.extra["lon"]?.toDoubleOrNull()
        if (lat == null || lon == null) throw ProviderException(RunStatus.UNSUPPORTED, "no_coordinates")
        val la = String.format(Locale.ROOT, "%.5f", lat)
        val lo = String.format(Locale.ROOT, "%.5f", lon)
        val lang = if (ctx.language.isBlank()) "en" else ctx.language
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$la&lon=$lo&zoom=10&addressdetails=1&accept-language=$lang"
        val resp = ctx.http.get(url, quota = ctx.quota)
        val root = parseJson(resp.body)
        root["error"].str()?.let { throw ProviderException(RunStatus.NO_RESULT, "no_address") }
        val src = "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=10/$la/$lo"
        val rid = "$la,$lo"
        val out = mutableListOf<FindingDraft>()
        root["display_name"].str()?.let { out += FindingDraft("GEOLOCATION", "approx_address", it, src, rid, excerpt = excerptOf(root["address"], 400)) }
        val a = root["address"]
        listOf("city", "town", "village", "county", "state", "postcode", "country").forEach { k ->
            a[k].str()?.let { out += FindingDraft("GEOLOCATION", "address_$k", it, src, rid, excerpt = it) }
        }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "empty")
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}
