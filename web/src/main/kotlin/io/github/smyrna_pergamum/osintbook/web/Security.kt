package io.github.smyrna_pergamum.osintbook.web

import java.nio.file.Files
import java.nio.file.attribute.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.*

data class User(val name: String, val password: String, val admin: Boolean, val enabled: Boolean)
object Passwords {
    private val random = SecureRandom()
    private fun derive(password: String, salt: ByteArray) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(),salt,210000,256)).encoded
    fun hash(password: String): String {
        require(password.length in 12..256) { "password_length" }
        val salt=ByteArray(16).also { random.nextBytes(it) }
        return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt))
    }
    fun verify(password: String, saved: String): Boolean = runCatching {
        val parts=saved.split(':')
        MessageDigest.isEqual(Base64.getDecoder().decode(parts[1]),derive(password,Base64.getDecoder().decode(parts[0])))
    }.getOrDefault(false)
}

class Access(private val store: Store) {
    private data class Session(val name: String, val expires: Long)
    private val sessions=ConcurrentHashMap<String,Session>()
    private val dummy=Passwords.hash("not-a-real-account-password")
    private var attempts=0
    private var window=System.currentTimeMillis()
    private var resetAttempts=0
    private var resetWindow=System.currentTimeMillis()
    @Synchronized fun requestReset(name: String) {
        val now=System.currentTimeMillis()
        if(now-resetWindow>60000){resetWindow=now;resetAttempts=0}
        require(++resetAttempts<=20){"reset_rate_limited"}
        store.requestPasswordReset(name)
    }
    @Synchronized fun login(name: String, password: String): String? {
        val now=System.currentTimeMillis()
        if(now-window>60000){window=now;attempts=0}
        require(++attempts<=30){"login_rate_limited"}
        val user=store.users().firstOrNull { it.name==name.lowercase() }
        val correct=Passwords.verify(password,user?.password ?: dummy)
        if(!correct || user==null || !user.enabled) return null
        sessions.entries.removeIf { it.value.expires<now }
        require(sessions.size<100){"session_limit"}
        val token=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        sessions[token]=Session(user.name,now+28800000)
        return token
    }
    fun user(token: String?): User? {
        val session=token?.let { sessions[it] } ?: return null
        if(session.expires<System.currentTimeMillis()) { sessions.remove(token);return null }
        return store.users().firstOrNull { it.name==session.name && it.enabled }
    }
    fun valid(token: String?)=user(token)!=null
    fun logout(token: String?){if(token!=null)sessions.remove(token)}
    fun revoke(name: String){sessions.entries.removeIf { it.value.name==name }}
}

/** Server-owned AES-GCM key, with owner-only file permissions. Never included in exports. */
class Secrets(private val store: Store) {
    private val path=store.directory.resolve("secrets.key")
    private val key: SecretKeySpec
    init {
        if(!Files.exists(path)) {
            val bytes=ByteArray(32).also { SecureRandom().nextBytes(it) }
            if(Files.getFileStore(store.directory).supportsFileAttributeView("posix")) {
                Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            } else {
                Files.createFile(path)
                val view=Files.getFileAttributeView(path,AclFileAttributeView::class.java)
                    ?: error("Owner-only file permissions are required for the key vault.")
                view.acl=listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(*AclEntryPermission.entries.toTypedArray()).build())
            }
            Files.write(path,bytes)
        }
        key=SecretKeySpec(Files.readAllBytes(path).also { require(it.size==32){"Invalid vault key file."} },"AES")
    }
    fun get(id: String): String? {
        val saved=store.secret(id)
        if(saved!=null) {
            val data=Base64.getDecoder().decode(saved)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,data.copyOfRange(0,12)))
            cipher.updateAAD(id.toByteArray())
            return cipher.doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
        }
        return System.getenv("OSINT_KEY_${id.uppercase()}")?.takeIf { it.isNotBlank() }
    }
    fun save(id: String, value: String?) {
        if(value==null){store.secret(id,null);return}
        require(value.isNotBlank() && value.length<=4096){"key_invalid"}
        val iv=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv));cipher.updateAAD(id.toByteArray())
        store.secret(id,Base64.getEncoder().encodeToString(iv+cipher.doFinal(value.toByteArray())))
    }
    fun stored(id: String)=store.secret(id)!=null
}
