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

-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
-keep class com.goodstadt.john.language.exams.data.** { *; }
-keep class com.goodstadt.john.language.exams.models.** { *; }
-keep class com.goodstadt.john.language.exams.packages.dailydictionary.** { *; }

# -----------------------------------------------------------------
# KOTLINX SERIALIZATION FIXES (Add this to stop the crash)
# -----------------------------------------------------------------

# 1. Keep the @Serializable annotation so R8 knows which classes are special
-keepattributes *Annotation*, InnerClasses

# 2. Prevent R8 from warning about internal serialization classes
-dontnote kotlinx.serialization.**

# 3. CRITICAL: Keep the 'Companion' object of serializable classes.
# This is where the generated serializer ($serializer) actually lives.
# If R8 removes this, the app crashes with "No properties found".
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# 4. The "Safety Net" Rule
# Instead of listing every package manually, this rule says:
# "If I put @Serializable on a class, DO NOT touch it."
-keep @kotlinx.serialization.Serializable class * {
    *;
}

# -----------------------------------------------------------------
# HILT & LIFECYCLE FIXES (Fixes "Multiple entries with same key" crash)
# -----------------------------------------------------------------

# 1. Keep generic Lifecycle classes so R8 doesn't merge CreationExtras keys
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# 2. Keep Hilt & Dagger internals
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# 3. Specifically prevent renaming of ViewModel keys
-keepnames class androidx.lifecycle.viewmodel.CreationExtras$Key

# -----------------------------------------------------------------
# HILT & VIEWMODEL INSTANTIATION FIXES (Fixes "Cannot create instance")
# -----------------------------------------------------------------

# 1. Keep all ViewModels and their constructors
# This ensures Hilt can find the constructor to inject dependencies into.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 2. Keep the Entry Points (Activities/Fragments annotated with @AndroidEntryPoint)
-keep @dagger.hilt.android.AndroidEntryPoint class * {
    *;
}

# 3. Keep the Application class (@HiltAndroidApp)
-keep @dagger.hilt.android.HiltAndroidApp class * {
    *;
}

# 4. Keep any class that uses @Inject (Repositories, UseCases, etc.)
# If R8 strips the constructor of a dependency, the ViewModel using it will crash.
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# 5. Keep Dagger's generated code (Factories)
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }