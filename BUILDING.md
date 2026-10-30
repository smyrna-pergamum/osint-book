# Building

## Android app

Requirements: OpenJDK 17+ (tested with 21) and the Android SDK (platform 36, build-tools 36.0.0). No paid service, secret CI variable or account is needed.

```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # unsigned: app/build/outputs/apk/release/app-release-unsigned.apk
```

Versions are pinned (FD-03): Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5. There are no dynamic `+` or SNAPSHOT dependencies.

### Signing
For a signed release, create `signing/keystore.properties` outside version control (it is in `.gitignore`):

```
storeFile=signing/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Never commit the signing key and keep an offline backup.

## Windows edition

Requirements: JDK 21 or later (with `jlink`) and Windows PowerShell. The Android SDK is not needed.

```powershell
.\web\Build.ps1                 # tests and a ZIP that relies on Java installed on the target computer
.\web\Build.ps1 -BundleRuntime  # the same with a trimmed Java runtime inside the ZIP (release build)
```

The script downloads the llama.cpp b11934 CPU and Vulkan builds from the official GitHub release once and checks them against pinned SHA-256 values. The result is `web/build/distributions/OSINT-Book-Web-<version>-Windows-x64.zip`, with a `.sha256` file next to it. The ZIP never contains user data. To run the server from the source tree, use `web\scripts\Start-Local.bat`; its data is kept in `web\data`.
