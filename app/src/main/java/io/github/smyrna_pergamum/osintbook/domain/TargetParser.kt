package io.github.smyrna_pergamum.osintbook.domain

import io.github.smyrna_pergamum.osintbook.data.TargetType
import java.net.IDN
import java.net.URI

/** Result of validating user input for one target (FR-02). */
sealed interface ParseResult {
    data class Ok(
        val type: TargetType,
        val normalized: String,
        /** Unicode form for IDN hosts; equal to [normalized] otherwise. */
        val display: String,
        val warnings: List<Warning> = emptyList(),
        /** FR-03: the same input with secret-looking URL parts removed, when there were any. */
        val cleaned: String? = null,
    ) : ParseResult

    data class Error(val reason: Reason) : ParseResult
}

enum class Warning { SECRET_PARAMS, USERINFO, PRIVATE_IP, NO_VERSION }

enum class Reason { EMPTY, TOO_LONG, BAD_DOMAIN, BAD_URL, BAD_SCHEME, BAD_IP, BAD_REPO, BAD_PACKAGE, BAD_ORG, BAD_HASH }

object TargetParser {
    /** OSV ecosystem names, matched case-insensitively. */
    val ECOSYSTEMS = listOf("npm", "PyPI", "Maven", "Go", "crates.io", "NuGet", "RubyGems", "Packagist", "Pub", "Hex")

    private val SECRET_PARAM = Regex(
        "^(api[_-]?key|apikey|key|token|access[_-]?token|auth|authorization|secret|client[_-]?secret|password|passwd|pwd|sig|signature|session|sessionid|sid|code|jwt|x-amz-signature|x-amz-credential)$",
        RegexOption.IGNORE_CASE,
    )
    /** MD5, SHA-1 or SHA-256 in hex. */
    private val HASH = Regex("^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$")
    private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
    private val TLD = Regex("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
    private val GH_OWNER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val GH_REPO = Regex("^[A-Za-z0-9._-]{1,100}$")

    /** Best guess at the type, so the user rarely has to pick one. */
    fun detect(raw: String): TargetType {
        val s = raw.trim()
        return when {
            s.isEmpty() -> TargetType.DOMAIN
            HASH.matches(s) -> TargetType.HASH
            s.startsWith("http://", true) || s.startsWith("https://", true) ->
                if (repoFromGithubUrl(s) != null) TargetType.REPO else TargetType.URL
            parseIp(s) != null -> TargetType.IP
            ECOSYSTEMS.any { s.startsWith("$it:", ignoreCase = true) } -> TargetType.PACKAGE
            s.count { it == '/' } == 1 && !s.contains(' ') && !s.contains('.') -> TargetType.REPO
            s.contains('.') && !s.contains(' ') -> TargetType.DOMAIN
            else -> TargetType.ORG
        }
    }

    fun parse(type: TargetType, raw: String): ParseResult {
        val s = raw.trim()
        if (s.isEmpty()) return ParseResult.Error(Reason.EMPTY)
        if (s.length > 2048) return ParseResult.Error(Reason.TOO_LONG)
        return when (type) {
            TargetType.DOMAIN -> parseDomain(s)
            TargetType.URL -> parseUrl(s)
            TargetType.IP -> parseIpTarget(s)
            TargetType.ORG -> parseOrg(s)
            TargetType.REPO -> parseRepo(s)
            TargetType.PACKAGE -> parsePackage(s)
            TargetType.HASH -> if (HASH.matches(s)) ParseResult.Ok(TargetType.HASH, s.lowercase(), s.lowercase()) else ParseResult.Error(Reason.BAD_HASH)
        }
    }

    fun parseDomain(input: String): ParseResult {
        var s = input.trim().trimEnd('.')
        // Accept a pasted URL or "host:port" in the domain field; keep only the host.
        if (s.contains("://")) s = runCatching { URI(s).host }.getOrNull() ?: return ParseResult.Error(Reason.BAD_DOMAIN)
        s = s.substringBefore('/').substringBefore(':')
        val ascii = toAsciiHost(s) ?: return ParseResult.Error(Reason.BAD_DOMAIN)
        if (parseIp(ascii) != null) return ParseResult.Error(Reason.BAD_DOMAIN)
        return ParseResult.Ok(TargetType.DOMAIN, ascii, toUnicodeHost(ascii))
    }

    /** IDNA conversion plus hostname syntax checks; null when invalid. */
    fun toAsciiHost(host: String): String? {
        if (host.isEmpty() || host.length > 253) return null
        val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull()?.lowercase() ?: return null
        if (ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.size < 2) return null
        if (!labels.all { LABEL.matches(it) }) return null
        if (!TLD.matches(labels.last())) return null
        return ascii
    }

    fun toUnicodeHost(ascii: String): String = runCatching { IDN.toUnicode(ascii) }.getOrDefault(ascii)

    fun parseUrl(input: String): ParseResult {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return ParseResult.Error(Reason.BAD_URL)
        val scheme = uri.scheme?.lowercase() ?: return ParseResult.Error(Reason.BAD_URL)
        // SEC-07: only web links are accepted.
        if (scheme != "http" && scheme != "https") return ParseResult.Error(Reason.BAD_SCHEME)
        val rawHost = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
            ?: return ParseResult.Error(Reason.BAD_URL)
        val host = if (parseIp(rawHost.trim('[', ']')) != null) rawHost.lowercase()
        else toAsciiHost(rawHost) ?: return ParseResult.Error(Reason.BAD_URL)

        val warnings = mutableListOf<Warning>()
        if (uri.rawUserInfo != null) warnings += Warning.USERINFO
        val params = uri.rawQuery?.split('&')?.filter { it.isNotEmpty() } ?: emptyList()
        val keptParams = params.filterNot { SECRET_PARAM.matches(it.substringBefore('=')) }
        if (keptParams.size != params.size) warnings += Warning.SECRET_PARAMS

        val defaultPort = (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)
        val port = if (uri.port == -1 || defaultPort) "" else ":${uri.port}"
        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        fun build(h: String, q: List<String>) =
            "$scheme://$h$port$path" + if (q.isEmpty()) "" else "?" + q.joinToString("&")

        // The fragment never leaves the browser, so it is dropped.
        val normalized = build(host, params)
        val cleaned = if (warnings.isNotEmpty()) build(host, keptParams) else null
        return ParseResult.Ok(TargetType.URL, normalized, build(toUnicodeHost(host), params), warnings, cleaned)
    }

    fun parseIpTarget(input: String): ParseResult {
        val ip = parseIp(input.trim()) ?: return ParseResult.Error(Reason.BAD_IP)
        val warnings = if (isNonPublicIp(ip)) listOf(Warning.PRIVATE_IP) else emptyList()
        return ParseResult.Ok(TargetType.IP, ip, ip, warnings)
    }

    /** Canonical text form of an IPv4 or IPv6 literal, or null. Never resolves names. */
    fun parseIp(s: String): String? = parseIpv4(s) ?: parseIpv6(s)

    fun parseIpv4(s: String): String? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { p ->
            // Leading zeros are rejected: some parsers read them as octal.
            if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit) || (p.length > 1 && p[0] == '0')) return null
            p.toInt().takeIf { it in 0..255 } ?: return null
        }
        return nums.joinToString(".")
    }

    fun parseIpv6(input: String): String? {
        val s = input.trim('[', ']').lowercase().substringBefore('%')
        if (!s.contains(':') || s.count { it == ':' } > 7 && !s.contains("::")) return null
        if (s.indexOf("::") != s.lastIndexOf("::")) return null
        val groups = mutableListOf<Int>()
        fun parseSide(side: String): List<Int>? {
            if (side.isEmpty()) return emptyList()
            val out = mutableListOf<Int>()
            val items = side.split(':')
            items.forEachIndexed { i, g ->
                if (i == items.lastIndex && g.contains('.')) {
                    val v4 = parseIpv4(g) ?: return null
                    val b = v4.split('.').map { it.toInt() }
                    out += (b[0] shl 8) or b[1]
                    out += (b[2] shl 8) or b[3]
                } else {
                    if (g.isEmpty() || g.length > 4 || !g.all { it in '0'..'9' || it in 'a'..'f' }) return null
                    out += g.toInt(16)
                }
            }
            return out
        }
        if (s.contains("::")) {
            val head = parseSide(s.substringBefore("::")) ?: return null
            val tail = parseSide(s.substringAfter("::")) ?: return null
            if (head.size + tail.size > 7) return null
            groups += head
            repeat(8 - head.size - tail.size) { groups += 0 }
            groups += tail
        } else {
            groups += parseSide(s) ?: return null
            if (groups.size != 8) return null
        }
        return compressIpv6(groups)
    }

    /** RFC 5952 text form: lowercase, longest zero run (length >= 2) replaced by "::". */
    private fun compressIpv6(g: List<Int>): String {
        var bestStart = -1; var bestLen = 0; var i = 0
        while (i < 8) {
            if (g[i] == 0) {
                var j = i
                while (j < 8 && g[j] == 0) j++
                if (j - i > bestLen) { bestStart = i; bestLen = j - i }
                i = j
            } else i++
        }
        val hex = g.map { Integer.toHexString(it) }
        if (bestLen < 2) return hex.joinToString(":")
        val head = hex.subList(0, bestStart).joinToString(":")
        val tail = hex.subList(bestStart + bestLen, 8).joinToString(":")
        return "$head::$tail"
    }

    /** Private, loopback, link-local and other ranges public sources know nothing about. */
    fun isNonPublicIp(ip: String): Boolean {
        parseIpv4(ip)?.let { v4 ->
            val b = v4.split('.').map { it.toInt() }
            return b[0] == 10 || b[0] == 127 || b[0] == 0 || b[0] >= 224 ||
                (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
                (b[0] == 169 && b[1] == 254) || (b[0] == 100 && b[1] in 64..127)
        }
        val v6 = ip.lowercase()
        return v6 == "::1" || v6 == "::" || v6.startsWith("fe8") || v6.startsWith("fe9") ||
            v6.startsWith("fea") || v6.startsWith("feb") || v6.startsWith("fc") || v6.startsWith("fd") ||
            v6.startsWith("ff")
    }

    fun parseOrg(input: String): ParseResult {
        val s = input.trim().replace(Regex("\\s+"), " ")
        if (s.length < 2) return ParseResult.Error(Reason.BAD_ORG)
        if (s.length > 200) return ParseResult.Error(Reason.TOO_LONG)
        return ParseResult.Ok(TargetType.ORG, s, s)
    }

    fun parseRepo(input: String): ParseResult {
        val s = input.trim()
        val pair = repoFromGithubUrl(s) ?: s.removeSuffix(".git").split('/').takeIf { it.size == 2 }?.let { it[0] to it[1] }
        // A bare account name ("mozilla") is accepted too and looked up as a user or organization.
        val (owner, repo) = pair ?: (s to "")
        if (!GH_OWNER.matches(owner)) return ParseResult.Error(Reason.BAD_REPO)
        if (repo.isNotEmpty() && (!GH_REPO.matches(repo) || repo == "." || repo == "..")) return ParseResult.Error(Reason.BAD_REPO)
        val normalized = if (repo.isEmpty()) owner.lowercase() else "${owner.lowercase()}/${repo.lowercase()}"
        return ParseResult.Ok(TargetType.REPO, normalized, if (repo.isEmpty()) owner else "$owner/$repo")
    }

    fun repoFromGithubUrl(s: String): Pair<String, String>? {
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        if (uri.host?.lowercase() != "github.com" && uri.host?.lowercase() != "www.github.com") return null
        val parts = uri.path.orEmpty().trim('/').split('/')
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
        return parts[0] to parts[1].removeSuffix(".git")
    }

    fun parsePackage(input: String): ParseResult {
        val eco = ECOSYSTEMS.firstOrNull { input.startsWith("$it:", ignoreCase = true) }
            ?: return ParseResult.Error(Reason.BAD_PACKAGE)
        val rest = input.substring(eco.length + 1).trim()
        // "@scope/name@1.2.3": the version separator is the last '@' that is not the first character.
        val at = rest.lastIndexOf('@')
        val (name, version) = if (at > 0) rest.substring(0, at) to rest.substring(at + 1) else rest to ""
        if (name.isEmpty() || name.length > 214 || name.any { it.isWhitespace() }) return ParseResult.Error(Reason.BAD_PACKAGE)
        if (version.any { it.isWhitespace() } || version.length > 64) return ParseResult.Error(Reason.BAD_PACKAGE)
        val normalized = if (version.isEmpty()) "$eco:$name" else "$eco:$name@$version"
        val warnings = if (version.isEmpty()) listOf(Warning.NO_VERSION) else emptyList()
        return ParseResult.Ok(TargetType.PACKAGE, normalized, normalized, warnings)
    }

    /** FR-06: the comparison form used for dedupe; display keeps the provider's text. */
    fun normalizeValue(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()
}
