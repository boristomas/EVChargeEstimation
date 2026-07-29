# EV Charge Estimation — release shrinker rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson models used for history JSON
-keepclassmembers class com.chupacabra.evchargeestimation.data.** {
    <fields>;
    <init>(...);
}
-keep class com.chupacabra.evchargeestimation.data.** { *; }

# ML Kit text recognition + Play Services
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Keep reminder receiver entry (manifest)
-keep class com.chupacabra.evchargeestimation.reminder.ChargeReminderReceiver { *; }
