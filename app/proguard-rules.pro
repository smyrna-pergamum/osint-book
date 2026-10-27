# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.smyrna_pergamum.osintbook.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.smyrna_pergamum.osintbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
