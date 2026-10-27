package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import kotlinx.serialization.json.JsonElement
import java.util.Locale

/** Finding label that carries "lat, lon"; the map views look for it. */
const val COORDINATES_LABEL = "coordinates"

/** Parses "lat, lon" back into numbers; null when malformed or out of range. */
fun parseCoordinates(value: String): Pair<Double, Double>? {
    val parts = value.split(',').map { it.trim().toDoubleOrNull() ?: return null }
    if (parts.size != 2) return null
    val (lat, lon) = parts
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
}

/**
 * IP geolocation is an estimate of where the network is registered or routed, often the ISP's
 * city; it is not the location of a person or device. Two independent sources are offered so the
 * differences stay visible (§5: no merged verdict).
 */
abstract class IpGeoProvider : Provider {
    override val version = 1
    override val supports = setOf(TargetType.IP)
    override val keyNeed = KeyNeed.NONE
    override fun estimateRequests(target: Target) = 1

    abstract fun url(ip: String): String
    /** Request headers; providers that take a key in the URL build it in [url] instead. */
    open fun headers(ctx: QueryContext): Map<String, String> = emptyMap()
    abstract fun page(ip: String): String
    open fun keyedUrl(ip: String, key: String): String = url(ip)
    abstract fun fields(root: JsonElement): Map<String, String?>

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val ip = ctx.target.normalizedValue
        val url = if (keyNeed == KeyNeed.NONE) url(ip) else keyedUrl(ip, ctx.key ?: throw ProviderException(RunStatus.NO_KEY, "no_key"))
        val resp = ctx.http.get(url, headers(ctx), carriesSecret = ctx.key != null, quota = ctx.quota)
        val root = parseJson(resp.body)
        if (root["success"].str() == "false" || root["error"].str() == "true") {
            throw ProviderException(RunStatus.NO_RESULT, root["message"].str() ?: root["reason"].str() ?: "no_data")
        }
        val f = fields(root)
        val lat = f["latitude"]?.toDoubleOrNull()
        val lon = f["longitude"]?.toDoubleOrNull()
        val src = page(ip)
        val out = mutableListOf<FindingDraft>()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            val text = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)
            out += FindingDraft("GEOLOCATION", COORDINATES_LABEL, text, src, excerpt = excerptOf(root, 500))
        }
        listOf("accuracy_km", "confidence", "country", "region", "city", "postal", "timezone", "isp", "org", "asn").forEach { k ->
            f[k]?.takeIf { it.isNotBlank() }?.let { out += FindingDraft(if (k in setOf("isp", "org", "asn")) "NETWORK" else "GEOLOCATION", k, it, src, excerpt = it) }
        }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "empty")
        return ProviderResult(out, listOf(RawPart(publicUrl(ip), resp.body, resp.mediaType)))
    }

    /** The request URL as stored with evidence; never contains a key. */
    open fun publicUrl(ip: String) = url(ip)
}

/** ipwho.is: free, no key, HTTPS. */
class IpWhoIsProvider : IpGeoProvider() {
    override val id = "ipwhois"
    override val name = "ipwho.is"
    override val host = "ipwho.is"
    override val docsUrl = "https://ipwhois.io/documentation"

    override fun url(ip: String) = "https://ipwho.is/${ip.urlEncode()}"
    override fun page(ip: String) = "https://ipwho.is/${ip.urlEncode()}"
    override fun fields(root: JsonElement) = mapOf(
        "latitude" to root["latitude"].str(), "longitude" to root["longitude"].str(),
        "country" to root["country"].str(), "region" to root["region"].str(), "city" to root["city"].str(),
        "postal" to root["postal"].str(), "timezone" to root["timezone"]["id"].str(),
        "isp" to root["connection"]["isp"].str(), "org" to root["connection"]["org"].str(),
        "asn" to root["connection"]["asn"].str()?.let { "AS$it" },
    )
}

/** ipapi.co: free tier without key (about 1000 requests/day), HTTPS. */
class IpApiCoProvider : IpGeoProvider() {
    override val id = "ipapico"
    override val name = "ipapi.co"
    override val host = "ipapi.co"
    override val docsUrl = "https://ipapi.co/api/"
    override val dailyLimit = 1000

    override fun url(ip: String) = "https://ipapi.co/${ip.urlEncode()}/json/"
    override fun page(ip: String) = "https://ipapi.co/?q=${ip.urlEncode()}"
    override fun fields(root: JsonElement) = mapOf(
        "latitude" to root["latitude"].str(), "longitude" to root["longitude"].str(),
        "country" to root["country_name"].str(), "region" to root["region"].str(), "city" to root["city"].str(),
        "postal" to root["postal"].str(), "timezone" to root["timezone"].str(),
        "org" to root["org"].str(), "asn" to root["asn"].str(),
    )
}

/** GeoJS: free, no key, HTTPS. Reports an accuracy radius, which is shown as a finding. */
class GeoJsProvider : IpGeoProvider() {
    override val id = "geojs"
    override val name = "GeoJS"
    override val host = "get.geojs.io"
    override val docsUrl = "https://www.geojs.io/docs/v1/endpoints/geo/"

    override fun url(ip: String) = "https://get.geojs.io/v1/ip/geo/${ip.urlEncode()}.json"
    override fun page(ip: String) = url(ip)
    override fun fields(root: JsonElement) = mapOf(
        "latitude" to root["latitude"].str(), "longitude" to root["longitude"].str(),
        "accuracy_km" to root["accuracy"].str()?.let { "± $it km" },
        "country" to root["country"].str(), "region" to root["region"].str(), "city" to root["city"].str(),
        "timezone" to root["timezone"].str(), "org" to root["organization_name"].str(),
        "asn" to root["asn"].str()?.let { "AS$it" },
    )
}

/**
 * BigDataCloud IP Geolocation with the user's own free key. The API takes the key as a URL
 * parameter, so the request never follows redirects and the stored URL omits it.
 */
class BigDataCloudProvider : IpGeoProvider() {
    override val id = "bigdatacloud"
    override val name = "BigDataCloud"
    override val host = "api-bdc.net"
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://www.bigdatacloud.com/docs/api/ip-geolocation-api"
    override val signupUrl = "https://www.bigdatacloud.com/account"

    override fun url(ip: String) = "https://api-bdc.net/data/ip-geolocation?ip=${ip.urlEncode()}&localityLanguage=en"
    override fun keyedUrl(ip: String, key: String) = url(ip) + "&key=" + key.urlEncode()
    override fun page(ip: String) = "https://www.bigdatacloud.com/ip-geolocation"
    override fun fields(root: JsonElement): Map<String, String?> {
        val loc = root["location"]
        val carrier = root["network"]["carriers"].arr()?.firstOrNull()
        return mapOf(
            "latitude" to loc["latitude"].str(), "longitude" to loc["longitude"].str(),
            "confidence" to root["confidence"].str(),
            "country" to root["country"]["name"].str(), "region" to loc["principalSubdivision"].str(),
            "city" to loc["city"].str(), "postal" to loc["postcode"].str(),
            "timezone" to loc["timeZone"]["ianaTimeId"].str(),
            "org" to root["network"]["organisation"].str(),
            "asn" to carrier["asn"].str(),
        )
    }
}
