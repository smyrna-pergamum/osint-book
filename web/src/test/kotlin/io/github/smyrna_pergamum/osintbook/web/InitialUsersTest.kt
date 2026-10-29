package io.github.smyrna_pergamum.osintbook.web

import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class InitialUsersTest {
    @Test fun firstSetupAddsSeveralUsersAndRetriesInvalidInput() {
        Store(Files.createTempDirectory("setup-users-").resolve("db.sqlite")).use { store ->
            val lines = ArrayDeque(listOf("admin", "y", "n", "admin", "analyst", "yes", "yes", "manager", "n"))
            val passwords = ArrayDeque(listOf("administrator-pass", "duplicate-password", "analyst-password", "manager-password"))
            val messages = mutableListOf<String>()
            setupInitialUsers(store, "", "", { lines.removeFirstOrNull() }, { passwords.removeFirstOrNull() }, messages::add)
            val users = store.users().associateBy { it.name }
            assertEquals(setOf("admin", "analyst", "manager"), users.keys)
            assertTrue(users.getValue("admin").admin)
            assertFalse(users.getValue("analyst").admin)
            assertTrue(users.getValue("manager").admin)
            assertTrue(Passwords.verify("analyst-password", users.getValue("analyst").password))
            assertTrue(messages.any { it.contains("already exists") })
            setupInitialUsers(store, "newadmin", "new-admin-password", { error("Existing setup must not prompt") }, null)
            assertEquals(3, store.users().size)
        }
    }

    @Test fun unattendedSetupDoesNotPromptAndCreatesOneAdmin() {
        Store(Files.createTempDirectory("setup-env-").resolve("db.sqlite")).use { store ->
            setupInitialUsers(store, "admin", "administrator-pass", { error("Unexpected prompt") }, null)
            assertEquals("admin", store.users().single().name)
            assertTrue(store.users().single().admin)
        }
    }
}
