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
-keep class com.example.data.** { *; }

# Keep BuildConfig
-keep class com.example.BuildConfig { *; }

# Keep generic type signatures for reflection
-keepattributes Signature, *Annotation*

# Keep enum values (used in ProgressionEngine.OutcomeType etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
