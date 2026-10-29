package io.github.smyrna_pergamum.osintbook.web

/** First-run console setup; existing accounts are never modified. */
internal fun setupInitialUsers(
    store: Store,
    username: String,
    password: String,
    readLine: ((String) -> String?)?,
    readPassword: ((String) -> String?)?,
    print: (String) -> Unit = ::println,
) {
    if (store.users().isNotEmpty()) return
    val unattended = username.isNotBlank() && password.isNotBlank()
    print("First start: create the server administrator account.")
    if (unattended) {
        store.addUser(username, password, true)
        return
    }
    require(readLine != null && readPassword != null) { "Set OSINT_USERNAME and OSINT_PASSWORD for unattended first startup." }

    fun addAccount(admin: Boolean, suppliedName: String = "", suppliedPassword: String = ""): Boolean {
        var name = suppliedName
        var secret = suppliedPassword
        while (true) {
            if (name.isBlank()) name = readLine("Username (3-40 letters, numbers, . _ -): ") ?: return false
            if (secret.isBlank()) secret = readPassword("Password (at least 12 characters): ") ?: return false
            try {
                store.addUser(name, secret, admin)
                print(if (admin) "Administrator account created." else "User account created.")
                return true
            } catch (e: IllegalArgumentException) {
                val message = when (e.message) {
                    "username_invalid" -> "Invalid username. Use 3-40 letters, numbers, dots, underscores or hyphens."
                    "username_exists" -> "That username already exists."
                    "password_length" -> "Password must contain 12-256 characters."
                    else -> "Unable to create the account. Check the username and password."
                }
                print(message)
                name = ""; secret = ""
            }
        }
    }
    if (!addAccount(true, username, password)) error("Administrator account setup was cancelled.")
    while (true) {
        val choice = readLine("Add another user? [y/N]: ")?.trim()?.lowercase() ?: break
        if (choice.isEmpty() || choice == "n" || choice == "no") break
        if (choice != "y" && choice != "yes") { print("Please enter y or n."); continue }
        var admin = false
        while (true) {
            val role = readLine("Make this user an administrator? [y/N]: ")?.trim()?.lowercase() ?: return
            if (role.isEmpty() || role == "n" || role == "no") break
            if (role == "y" || role == "yes") { admin = true; break }
            print("Please enter y or n.")
        }
        if (!addAccount(admin)) break
    }
}
