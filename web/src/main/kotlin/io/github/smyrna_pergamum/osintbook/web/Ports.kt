package io.github.smyrna_pergamum.osintbook.web

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal data class Ports(val user: Int, val admin: Int)
internal fun configurePorts(directory: Path, userOverride: String?, adminOverride: String?, prompt: ((String) -> String?)?): Ports {
    val file=directory.resolve("ports.properties")
    val saved=Properties()
    if(Files.exists(file)) Files.newInputStream(file).use { saved.load(it) }
    fun port(value: String): Int = value.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw IllegalArgumentException("Port must be between 1 and 65535.")
    fun choose(name: String, override: String?, default: String): Int {
        if(!override.isNullOrBlank()) return port(override)
        saved.getProperty(name)?.let { return port(it) }
        if(prompt == null) return port(default)
        while(true) {
            val input=prompt("$name port [$default]: ") ?: error("Port setup cancelled.")
            val parsed=(input.ifBlank { default }).toIntOrNull()
            if(parsed != null && parsed in 1..65535) return parsed
            println("Port must be between 1 and 65535.")
        }
    }
    val user=choose("user",userOverride,"8080")
    var admin=choose("admin",adminOverride,"8081")
    while(admin==user) {
        require(prompt != null && adminOverride.isNullOrBlank() && saved.getProperty("admin") == null) { "User and admin ports must be different." }
        println("The admin port must differ from the user port.")
        admin=choose("admin",null,"8081")
    }
    // Environment overrides are temporary; do not overwrite previously chosen ports.
    if(!Files.exists(file)) {
        val props=Properties().apply { setProperty("user",user.toString());setProperty("admin",admin.toString()) }
        Files.newOutputStream(file).use { props.store(it,"OSINT-Book ports") }
    }
    return Ports(user,admin)
}
