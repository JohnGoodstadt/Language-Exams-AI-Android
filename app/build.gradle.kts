
// <project-root>/app/build.gradle.kts

import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)// <-- ADD THIS LINE to apply the new plugin
    alias(libs.plugins.kotlinSerialization)
   // kotlin("kapt")
    alias(libs.plugins.google.gms.google.services)
    id("com.google.devtools.ksp")
}

val secretsProperties = Properties()
val secretsFile = rootProject.file("secrets.properties")
if (secretsFile.exists()) {
    secretsProperties.load(FileInputStream(secretsFile))
}

android {
    namespace = "com.goodstadt.john.language.exams" // Base namespace
    compileSdk = 35 //was 34

    defaultConfig {
        applicationId = "com.goodstadt.john.language.exams"
        minSdk = 26 // Covers over 90% of devices
        targetSdk = 35
        versionCode = 10
        versionName = "1.0"

        testInstrumentationRunner = "com.goodstadt.john.language.exams.CustomTestRunner" // For Hilt testing

        buildConfigField(
                "String",
                "TTS_API_KEY",
                secretsProperties.getProperty("TTS_API_KEY")
        )

        buildConfigField(
                "String",
                "OPENAI_API_KEY",
                secretsProperties.getProperty("OPENAI_API_KEY")
        )

       // buildConfigField("String", "TTS_API_KEY", "\"AIzaSyBSGjKuHGjfCHmfMNBHxD4wuH0COGQ0biY\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }


    // Define a dimension for your product flavors
    flavorDimensions += "language"

    productFlavors {
        create("en") {
            dimension = "language"
            applicationIdSuffix = ".en"
            versionNameSuffix = "-en"
            versionCode = 11
            versionName = "1.1"
            buildConfigField("String", "LANGUAGE_ID", "\"en\"")
        }
        create("de") {
            dimension = "language"
            applicationIdSuffix = ".de"
            versionNameSuffix = "-de"
            versionCode = 12
            versionName = "1.2"
            buildConfigField("String", "LANGUAGE_ID", "\"de\"")
        }
        create("zh") {
            dimension = "language"
            applicationIdSuffix = ".zh"
            versionNameSuffix = "-zh"
            versionCode = 13
            versionName = "1.3"
            buildConfigField("String", "LANGUAGE_ID", "\"zh\"")
        }
    }

    // --- ADD THIS ENTIRE sourceSets BLOCK ---
    sourceSets {
        getByName("main") {
            // This line is optional but good practice. It confirms where the main assets are.
            assets.srcDirs("src/main/assets")
        }
        getByName("en") {
            // For the 'en' flavor, add its specific assets directory.
            assets.srcDir("src/en/assets")
        }
        getByName("de") {
            // For the 'de' flavor, add its specific assets directory.
            assets.srcDir("src/de/assets")
        }
        getByName("zh") {
            // For the 'zh' flavor, add its specific assets directory.
            assets.srcDir("src/zh/assets")
        }
    }
    // --- END OF NEW BLOCK ---


    buildTypes {
        debug {
            isMinifyEnabled = false
            // BuildConfig field to detect debug builds at runtime
            buildConfigField("Boolean", "IS_DEBUG", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            // BuildConfig field for release builds
            buildConfigField("Boolean", "IS_DEBUG", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true // Enable BuildConfig generation
    }
//    composeOptions {
//        kotlinCompilerExtensionVersion = "1.5.11"
//    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    // Add the new dependency here
    implementation(libs.google.android.material) // <-- ADD THIS LINE

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
//    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.accompanist.navigation.material)

    // Hilt

    implementation(libs.hilt.android)
    implementation(libs.androidx.lifecycle.runtime.compose.android)

    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Add this for JSON parsing
    implementation(libs.kotlinx.serialization.json)
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore) {
        exclude(group = "com.google.firebase", module = "firebase-common")
    }
    implementation(libs.firebase.firestore)
    implementation(libs.google.firebase.config.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // --- ADD THESE TWO IMPLEMENTATIONS ---
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core) // Core Mockito library
    testImplementation(libs.mockito.kotlin) // Helper library for Mockito with Kotlin
    testImplementation(libs.kotlinx.coroutines.test) // For testing suspend functions
    testImplementation(libs.kotlin.test)
    
    // Instrumented/UI Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
//    kaptAndroidTest(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.okhttp)
}

//// Allow Hilt to access classes in different build variants
//kapt {
//    correctErrorTypes = true
//}