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

# Firebase Firestore/RTDB POJO klase — reflection-based deserialization (toObject(),
# getValue()) traži original field-ova i no-arg konstruktora. R8 sa minifyEnabled=true
# bi preimenovao field-ove (npr. `displayName` -> `a`) pa Firestore mapper-i ne bi mogli
# da pronađu ono što traže. Bez ovog keep rules-a, release build silent-fail-uje sva
# čitanja iz Firebase-a (toObject vraća sve-null instance ili Throws-uje).
-keep class org.krug.app.core.user.UserModel { *; }
-keep class org.krug.app.core.circle.CircleModel { *; }
-keep class org.krug.app.core.circle.MemberModel { *; }
-keep class org.krug.app.core.circle.InviteModel { *; }
-keep class org.krug.app.core.sos.SosModel { *; }
-keep class org.krug.app.core.location.LocationModel { *; }
-keep class org.krug.app.core.settings.UserSettings { *; }

# Companion objects + default constructor su potrebni Firebase mapper-u.
-keepclassmembers class org.krug.app.core.user.UserModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.CircleModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.MemberModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.InviteModel { <init>(...); }
-keepclassmembers class org.krug.app.core.sos.SosModel { <init>(...); }
-keepclassmembers class org.krug.app.core.location.LocationModel { <init>(...); }
-keepclassmembers class org.krug.app.core.settings.UserSettings { <init>(...); }
