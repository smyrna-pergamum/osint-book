# Third-party components

| Component | Licence |
|---|---|
| AndroidX (Compose, Material 3, Room, Navigation, DataStore, AppCompat, Lifecycle, Core) | Apache-2.0 |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| OkHttp, Okio | Apache-2.0 |
| osmdroid | Apache-2.0 |
| Material Icons | Apache-2.0 |
| OpenStreetMap map data (loaded at run time, not bundled) | ODbL-1.0 |

There is no Google Play Services, Firebase, advertising or tracking SDK. The app icon is the project's own artwork (GPL-3.0-or-later).
Full transitive list: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
