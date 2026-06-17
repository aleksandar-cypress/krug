# Compose, Hilt, Firebase, Mapbox bring their own consumer rules.
# Add app-specific rules below.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization
-keep,includedescriptorclasses class org.krug.app.**$$serializer { *; }
-keepclassmembers class org.krug.app.** {
    *** Companion;
}
-keepclasseswithmembers class org.krug.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
