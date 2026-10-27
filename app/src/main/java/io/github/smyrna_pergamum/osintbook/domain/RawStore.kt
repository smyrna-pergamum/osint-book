package io.github.smyrna_pergamum.osintbook.domain

import java.io.File

/**
 * DATA-02: optional local copies of raw provider responses, content-addressed by SHA-256
 * so the hash in Evidence can be re-checked against the file (DATA-03).
 */
class RawStore(private val dir: File) {
    init { dir.mkdirs() }

    /** Returns the file name used as Evidence.rawResponseRef. */
    fun write(bytes: ByteArray): String {
        val name = sha256Hex(bytes) + ".raw"
        val f = File(dir, name)
        if (!f.exists()) f.writeBytes(bytes)
        return name
    }

    fun read(ref: String): ByteArray? = file(ref)?.takeIf { it.exists() }?.readBytes()

    /** True when the local copy still matches the stored hash. */
    fun verify(ref: String, sha256: String): Boolean? = read(ref)?.let { sha256Hex(it) == sha256 }

    fun delete(ref: String) { file(ref)?.delete() }

    /** Removes copies older than [maxAgeMs]; returns the refs removed so Evidence can be updated. */
    fun purgeOlderThan(maxAgeMs: Long, now: Long = System.currentTimeMillis()): List<String> =
        dir.listFiles().orEmpty().filter { now - it.lastModified() > maxAgeMs }.map { it.delete(); it.name }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    fun sizeBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    // Refs come from our own database, but are still confined to the raw directory.
    private fun file(ref: String): File? = if (ref.matches(Regex("^[0-9a-f]{64}\\.raw$"))) File(dir, ref) else null

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
