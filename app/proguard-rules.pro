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
-keep class org.krug.app.core.location.LocationHistoryPoint { *; }
-keep class org.krug.app.core.settings.UserSettings { *; }
-keep class org.krug.app.core.places.PlaceModel { *; }
-keep class org.krug.app.core.places.PlaceEventModel { *; }
-keep class org.krug.app.core.driving.TripModel { *; }
# 1.2.0 modeli (32.-32.5. sesija) — bez ovih R8 obfuskuje field-ove pa Firestore
# mapper baca "No properties to serialize found on class r8.a" pri prvom fetch-u
# koji se dešava odmah pri otvaranju mape (SOS/place events observers).
-keep class org.krug.app.core.speeding.SpeedingEventModel { *; }
-keep class org.krug.app.core.checkin.CheckInEventModel { *; }
-keep class org.krug.app.core.eta.EtaShareModel { *; }
-keep class org.krug.app.core.device.DeviceModel { *; }

# Companion objects + default constructor su potrebni Firebase mapper-u.
-keepclassmembers class org.krug.app.core.user.UserModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.CircleModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.MemberModel { <init>(...); }
-keepclassmembers class org.krug.app.core.circle.InviteModel { <init>(...); }
-keepclassmembers class org.krug.app.core.sos.SosModel { <init>(...); }
-keepclassmembers class org.krug.app.core.location.LocationModel { <init>(...); }
-keepclassmembers class org.krug.app.core.location.LocationHistoryPoint { <init>(...); }
-keepclassmembers class org.krug.app.core.settings.UserSettings { <init>(...); }
-keepclassmembers class org.krug.app.core.places.PlaceModel { <init>(...); }
-keepclassmembers class org.krug.app.core.places.PlaceEventModel { <init>(...); }
-keepclassmembers class org.krug.app.core.driving.TripModel { <init>(...); }
-keepclassmembers class org.krug.app.core.speeding.SpeedingEventModel { <init>(...); }
-keepclassmembers class org.krug.app.core.checkin.CheckInEventModel { <init>(...); }
-keepclassmembers class org.krug.app.core.eta.EtaShareModel { <init>(...); }
-keepclassmembers class org.krug.app.core.device.DeviceModel { <init>(...); }

# Firebase Firestore/RTDB annotation-marked classes — defense-in-depth.
# Bilo koji data class sa @ServerTimestamp / @DocumentId / @IgnoreExtraProperties fields
# se automatski čuva. Ovo hvata buduće nove modele koje neko doda a zaboravi da doda
# u eksplicit listu iznad.
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keepclasseswithmembers class org.krug.app.** {
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}
-keepclasseswithmembers class org.krug.app.** {
    @com.google.firebase.firestore.DocumentId <fields>;
}
