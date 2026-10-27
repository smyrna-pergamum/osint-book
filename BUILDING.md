# Derleme

Gereksinimler: OpenJDK 17+ (21 ile test edildi) ve Android SDK (platform 36, build-tools 36.0.0). Ücretli servis, gizli CI değişkeni veya hesap gerekmez.

```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # birim testleri
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # imzasız: app/build/outputs/apk/release/app-release-unsigned.apk
```

Sürümler sabittir (FD-03): Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5. Dinamik `+` veya SNAPSHOT bağımlılık yoktur.

## İmzalama
İmzalı release için depo dışında tutulan `signing/keystore.properties` dosyasını oluşturun (`.gitignore` içindedir):

```
storeFile=signing/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

İmza anahtarını depoya koymayın ve çevrimdışı yedekleyin. F-Droid uygulamayı kendi imzasıyla derleyebilir. Doğrudan dağıtılan APK ile aynı imzayı korumak için yeniden üretilebilir derleme gerekir (şartname §7).
