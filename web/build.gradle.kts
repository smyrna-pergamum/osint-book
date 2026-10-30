plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

kotlin { jvmToolchain(21) }
application { mainClass.set("io.github.smyrna_pergamum.osintbook.web.ServerKt") }

// Compile the Android project's portable logic from its source of truth. Room annotations
// only describe Android persistence; the web store uses its own SQLite repository.
val portable = layout.buildDirectory.dir("generated/portable")
val generatePortable by tasks.registering {
    val base = file("../app/src/main/java/io/github/smyrna_pergamum/osintbook")
    inputs.dir(base)
    outputs.dir(portable)
    doLast {
        val out = portable.get().asFile
        out.deleteRecursively()
        val files = fileTree(base) {
            include("providers/*.kt", "net/Http.kt", "domain/TargetParser.kt", "domain/Analyzer.kt", "export/Transfer.kt", "data/Entities.kt")
        }
        files.forEach { src ->
            var body = src.readText()
            if (src.name == "Entities.kt") {
                body = body.replace(Regex("(?m)^import androidx\\.room\\..*\\r?\\n"), "")
                body = body.replace("@PrimaryKey ", "")
                while (body.contains("@Entity(")) {
                    val start = body.indexOf("@Entity(")
                    var end = start + "@Entity".length
                    var depth = 0
                    do {
                        if (body[end] == '(') depth++
                        if (body[end] == ')') depth--
                        end++
                    } while (depth > 0)
                    body = body.removeRange(start, end)
                }
            }
            if (src.name == "Http.kt") body = body.replace("(Android;", "(Web/JVM;")
            val dest = out.resolve(src.relativeTo(base))
            dest.parentFile.mkdirs()
            dest.writeText(body)
        }
    }
}
kotlin.sourceSets.main { kotlin.srcDir(portable) }
tasks.named("compileKotlin") { dependsOn(generatePortable) }

dependencies {
    implementation("io.ktor:ktor-server-netty:3.3.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(kotlin("test-junit"))
    testImplementation("io.ktor:ktor-server-test-host:3.3.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
kotlin.sourceSets.test {
    kotlin.srcDir("../app/src/test/java")
}

// cmd.exe limits a line to 8191 characters; listing every jar breaks the launcher in deep folders. Java expands lib\* itself.
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(windowsScript.readText().replace(Regex("(?m)^set CLASSPATH=.*$"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*"))
    }
}

// A server started from build/install keeps its data there; never delete it when reinstalling.
tasks.named<Sync>("installDist") { preserve { include("data/**") } }

distributions {
    main {
        contents {
            from("scripts")
            from("README.md")
            from("../LICENSE")
            from("../THIRD_PARTY.md")
            from("build/llama-runtime") { into("ai-runtime") }
            from("build/llama-runtime-gpu") { into("ai-runtime-gpu") }
        }
    }
}
