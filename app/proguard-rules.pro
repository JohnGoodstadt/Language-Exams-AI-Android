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
-dontwarn org.slf4j.impl.StaticLoggerBinder

#-keep class com.android.billingclient.** { *; }
#
# =========== Firebase / Google Play Services (General) ===========
# These are the standard rules to prevent crashes with Firebase and GMS.
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
#-keep class com.google.firebase.** { *; }
#
## =========== Your App's Data Models ===========
## Keep all data models that are used with serialization (Firestore, Remote Config JSON, etc.)
-keep class com.goodstadt.john.language.exams.data.** { *; }
-keep class com.goodstadt.john.language.exams.models.** { *; }

# Strip out android.util.Log methods: v, d, i, println
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int println(...);
}

# Strip out Java/Kotlin print/println
-assumenosideeffects class java.io.PrintStream {
    public void print(...);
    public void println(...);
}
#
## =========== Kotlinx Serialization ===========
## If you use kotlinx.serialization, these rules are recommended.
#-keepclasseswithmembernames class * {
#    @kotlinx.serialization.Serializable <methods>;
#}
#-keep class **$$serializer { *; }
#
## =========== Kotlinx Coroutines ===========
## This prevents issues with coroutines in minified builds.
#-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
#    private final java.util.List a;
#    private final java.lang.String b;
#}
