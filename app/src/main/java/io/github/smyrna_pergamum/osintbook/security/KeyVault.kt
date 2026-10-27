package io.github.smyrna_pergamum.osintbook.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SEC-03: API keys are AES-GCM encrypted with a key that never leaves Android Keystore.
 * Only ciphertext is written to app-private storage, which is excluded from backup (SEC-05).
 */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    sealed interface Read {
        data class Value(val secret: String) : Read
        data object Missing : Read
        /** Keystore key lost or the entry is damaged: the user must enter the key again. */
        data object Unreadable : Read
    }

    fun put(ref: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        cipher.updateAAD(ref.toByteArray())
        val ct = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + ct
        prefs.edit().putString(ref, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(ref: String): Read {
        val stored = prefs.getString(ref, null) ?: return Read.Missing
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return unreadable(ref), GCMParameterSpec(128, blob, 0, IV_LEN))
            cipher.updateAAD(ref.toByteArray())
            Read.Value(String(cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN), Charsets.UTF_8))
        } catch (e: Exception) {
            unreadable(ref)
        }
    }

    fun has(ref: String) = prefs.contains(ref)

    fun remove(ref: String) = prefs.edit().remove(ref).apply()

    fun clear() = prefs.edit().clear().apply()

    private fun unreadable(ref: String): Read {
        remove(ref)
        return Read.Unreadable
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun masterKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generateKey()
    }

    private companion object {
        const val ALIAS = "osintbook_credentials"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
