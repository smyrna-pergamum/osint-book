# Third-party components

| Component | Licence |
|---|---|
| AndroidX (Compose, Material 3, Room, Navigation, DataStore, AppCompat, Lifecycle, Core) | Apache-2.0 |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| OkHttp, Okio | Apache-2.0 |
| osmdroid | Apache-2.0 |
| Material Icons | Apache-2.0 |
| OpenStreetMap map data (loaded at run time, not bundled) | ODbL-1.0 |

The Windows web package additionally includes:

| Component | Licence |
|---|---|
| Ktor, kotlinx libraries | Apache-2.0 |
| SQLite JDBC (xerial) | Apache-2.0 |
| SLF4J | MIT |
| llama.cpp / ggml b11934, CPU and Vulkan builds (`ai-runtime`, `ai-runtime-gpu`) | MIT |
| LLVM OpenMP runtime (shipped with llama.cpp) | Apache-2.0 with LLVM exception |
| Eclipse Temurin Java runtime (when bundled) | GPL-2.0 with Classpath Exception |

AI models are not bundled. Models downloaded from Hugging Face by an administrator are covered by their own licences shown on each model card.

There is no Google Play Services, Firebase, advertising or tracking SDK. The app icon is the project's own artwork (GPL-3.0-or-later).
Full transitive list: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
