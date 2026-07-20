# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room entities (Room's consumer rules cover this, but be explicit)
-keep class com.apexfit.app.data.** { *; }

# Keep BuildConfig
-keep class com.apexfit.app.BuildConfig { *; }

# Keep generic type signatures for reflection
-keepattributes Signature, *Annotation*

# Keep enum values (used in ProgressionEngine.OutcomeType etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson serialization — keep all fields used by reflection
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes serialized by Gson
-keep class com.apexfit.app.ActiveSession { *; }
-keep class com.apexfit.app.ActiveSession$* { *; }
-keep class com.apexfit.app.ui.models.** { *; }
