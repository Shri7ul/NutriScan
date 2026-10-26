# Keep Retrofit/Gson reflection metadata for the Open Food Facts DTOs.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

-keep class com.nutriscan.app.network.** { *; }

# Recent scans are persisted with Gson, so field names must survive obfuscation.
-keep class com.nutriscan.app.model.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.google.mlkit.**
