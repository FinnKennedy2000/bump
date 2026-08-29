# kotlinx.serialization keeps generated serializers reachable via reflection on the
# companion; without these the release build silently fails to deserialise AppState.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.finn.bump.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.finn.bump.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.finn.bump.data.**$$serializer { *; }
