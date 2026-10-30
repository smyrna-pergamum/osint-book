package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.export.*
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.sql.DriverManager

/** One serialized transaction boundary shared by all clients and background jobs. */
class Store(path: Path) : AutoCloseable {
    val directory: Path = path.toAbsolutePath().parent
    private val db = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    init {
        db.createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA busy_timeout=5000")
            it.execute("CREATE TABLE IF NOT EXISTS investigations(id TEXT PRIMARY KEY, body TEXT NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS quotas(id TEXT PRIMARY KEY, day TEXT NOT NULL, used INTEGER NOT NULL, blocked INTEGER NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS users(name TEXT PRIMARY KEY, password TEXT NOT NULL, admin INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)")
            it.execute("CREATE TABLE IF NOT EXISTS secrets(id TEXT PRIMARY KEY, value TEXT NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT, at TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, subject TEXT NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS password_requests(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, created_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', resolved_at TEXT)")
            it.execute("CREATE UNIQUE INDEX IF NOT EXISTS password_requests_pending ON password_requests(username) WHERE status='pending'")
        }
        // Runs interrupted by a process restart must never look permanently active.
        list().forEach { b ->
            if (b.queryRuns.any { !it.status.isFinal }) update(b.investigation.id) { old ->
                old.copy(queryRuns = old.queryRuns.map {
                    if (it.status.isFinal) it else it.copy(status = RunStatus.CANCELLED, completedAt = Time.nowIso(), errorCode = "server_restarted")
                })
            }
        }
    }
    @Synchronized fun list(): List<InvestigationBundle> = db.createStatement().use { st ->
        st.executeQuery("SELECT body FROM investigations").use { rs ->
            buildList { while (rs.next()) add(json.decodeFromString<InvestigationBundle>(rs.getString(1))) }
        }
    }
    @Synchronized fun get(id: String): InvestigationBundle? = db.prepareStatement("SELECT body FROM investigations WHERE id=?").use {
        it.setString(1, id)
        it.executeQuery().use { rs -> if (rs.next()) json.decodeFromString<InvestigationBundle>(rs.getString(1)) else null }
    }
    @Synchronized fun insert(b: InvestigationBundle) {
        db.prepareStatement("INSERT INTO investigations(id,body) VALUES(?,?)").use {
            it.setString(1, b.investigation.id); it.setString(2, json.encodeToString(b)); it.executeUpdate()
        }
    }
    @Synchronized fun update(id: String, change: (InvestigationBundle) -> InvestigationBundle): InvestigationBundle {
        db.autoCommit = false
        try {
            val old = get(id) ?: throw NoSuchElementException("not_found")
            val changed = change(old)
            val next = changed.copy(investigation = changed.investigation.copy(updatedAt = Time.nowIso()))
            db.prepareStatement("UPDATE investigations SET body=? WHERE id=?").use {
                it.setString(1, json.encodeToString(next)); it.setString(2, id); it.executeUpdate()
            }
            db.commit()
            return next
        } catch (e: Exception) { db.rollback(); throw e }
        finally { db.autoCommit = true }
    }
    @Synchronized fun delete(id: String) {
        db.prepareStatement("DELETE FROM investigations WHERE id=?").use { it.setString(1, id); it.executeUpdate() }
    }
    @Synchronized fun quota(id: String): Triple<String, Int, Long> = db.prepareStatement("SELECT day,used,blocked FROM quotas WHERE id=?").use {
        it.setString(1, id)
        it.executeQuery().use { rs -> if (rs.next()) Triple(rs.getString(1), rs.getInt(2), rs.getLong(3)) else Triple("", 0, 0L) }
    }
    @Synchronized fun quota(id: String, day: String, used: Int, blocked: Long) {
        db.prepareStatement("INSERT OR REPLACE INTO quotas VALUES(?,?,?,?)").use {
            it.setString(1,id); it.setString(2,day); it.setInt(3,used); it.setLong(4,blocked); it.executeUpdate()
        }
    }
    @Synchronized override fun close() = db.close()

    @Synchronized fun users(): List<User> = db.createStatement().use { st ->
        st.executeQuery("SELECT name,password,admin,enabled FROM users ORDER BY name").use { rs ->
            buildList { while (rs.next()) add(User(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4))) }
        }
    }
    @Synchronized fun addUser(name: String, password: String, admin: Boolean) {
        require(Regex("[a-zA-Z0-9_.-]{3,40}").matches(name)) { "username_invalid" }
        require(users().none { it.name == name.lowercase() }) { "username_exists" }
        val hash = Passwords.hash(password)
        db.prepareStatement("INSERT INTO users(name,password,admin,enabled) VALUES(?,?,?,1)").use {
            it.setString(1,name.lowercase());it.setString(2,hash);it.setBoolean(3,admin);it.executeUpdate()
        }
    }
    @Synchronized fun changeUser(name: String, password: String?, enabled: Boolean?, admin: Boolean?) {
        val old = users().firstOrNull { it.name == name } ?: throw NoSuchElementException()
        val next = old.copy(password = password?.let { Passwords.hash(it) } ?: old.password, enabled = enabled ?: old.enabled, admin = admin ?: old.admin)
        require(!old.admin || !old.enabled || (next.admin && next.enabled) || users().count { it.admin && it.enabled } > 1) { "last_admin" }
        db.prepareStatement("UPDATE users SET password=?,admin=?,enabled=? WHERE name=?").use {
            it.setString(1,next.password);it.setBoolean(2,next.admin);it.setBoolean(3,next.enabled);it.setString(4,name);it.executeUpdate()
        }
    }
    @Synchronized fun deleteUser(name: String) {
        val user = users().firstOrNull { it.name == name } ?: throw NoSuchElementException()
        require(!user.admin || !user.enabled || users().count { it.admin && it.enabled } > 1) { "last_admin" }
        db.autoCommit = false
        try {
            db.prepareStatement("DELETE FROM password_requests WHERE username=? AND status='pending'").use { it.setString(1,name); it.executeUpdate() }
            db.prepareStatement("DELETE FROM users WHERE name=?").use { it.setString(1,name); it.executeUpdate() }
            db.commit()
        } catch (e: Exception) { db.rollback(); throw e }
        finally { db.autoCommit = true }
    }
    @Synchronized fun secret(id: String): String? = db.prepareStatement("SELECT value FROM secrets WHERE id=?").use {
        it.setString(1,id);it.executeQuery().use { rs -> if(rs.next()) rs.getString(1) else null }
    }
    @Synchronized fun secret(id: String, encrypted: String?) {
        if(encrypted == null) db.prepareStatement("DELETE FROM secrets WHERE id=?").use { it.setString(1,id);it.executeUpdate() }
        else db.prepareStatement("INSERT OR REPLACE INTO secrets VALUES(?,?)").use { it.setString(1,id);it.setString(2,encrypted);it.executeUpdate() }
    }
    @Synchronized fun audit(actor: String, action: String, subject: String = "") {
        db.prepareStatement("INSERT INTO audit_log(at,actor,action,subject) VALUES(?,?,?,?)").use {
            it.setString(1,Time.nowIso());it.setString(2,actor.take(40));it.setString(3,action.take(80));it.setString(4,subject.take(120));it.executeUpdate()
        }
    }
    @Synchronized fun auditLog(): List<Map<String,String>> = db.createStatement().use { st ->
        st.executeQuery("SELECT at,actor,action,subject FROM audit_log ORDER BY id DESC LIMIT 500").use { rs ->
            buildList { while(rs.next()) add(mapOf("at" to rs.getString(1),"actor" to rs.getString(2),"action" to rs.getString(3),"subject" to rs.getString(4))) }
        }
    }
    @Synchronized fun requestPasswordReset(name: String) {
        if(users().none { it.name==name.lowercase() && it.enabled }) return
        db.prepareStatement("INSERT OR IGNORE INTO password_requests(username,created_at) VALUES(?,?)").use {
            it.setString(1,name.lowercase());it.setString(2,Time.nowIso());it.executeUpdate()
        }
    }
    @Synchronized fun passwordRequests(): List<Map<String,String>> = db.createStatement().use { st ->
        st.executeQuery("SELECT id,username,created_at,status FROM password_requests ORDER BY id DESC LIMIT 500").use { rs ->
            buildList { while(rs.next()) add(mapOf("id" to rs.getString(1),"username" to rs.getString(2),"createdAt" to rs.getString(3),"status" to rs.getString(4))) }
        }
    }
    @Synchronized fun resolvePasswordRequests(name: String) {
        db.prepareStatement("UPDATE password_requests SET status='resolved',resolved_at=? WHERE username=? AND status='pending'").use {
            it.setString(1,Time.nowIso());it.setString(2,name);it.executeUpdate()
        }
    }
}
