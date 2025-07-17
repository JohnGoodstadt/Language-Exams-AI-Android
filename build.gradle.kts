// <project-root>/build.gradle.kts
plugins {

    id("com.android.application") version "8.8.0" apply false // Use your AGP version from TOML
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false // Use your Kotlin version from TOML
    id("com.google.dagger.hilt.android") version "2.51.1" apply false // Use your Hilt version from TOML

    // This is the crucial fix. Declare the Compose Compiler plugin by its full ID and version.
   // id("org.jetbrains.kotlin.plugin.compose") version "1.5.14" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    //id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}
