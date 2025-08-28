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

val VERSION_CODE = 33
val VERSION_NAME = "1.33"

println("DEBUG: MYAPP_UPLOAD_STORE_PASSWORD from properties file is: '${secretsProperties.getProperty("MYAPP_UPLOAD_STORE_PASSWORD")}'")
println("DEBUG: MYAPP_UPLOAD_KEY_PASSWORD from properties file is: '${secretsProperties.getProperty("MYAPP_UPLOAD_KEY_PASSWORD")}'")

android {
    namespace = "com.goodstadt.john.language.exams" // Base namespace
    compileSdk = 35 //was 34

    defaultConfig {
        applicationId = "com.goodstadt.john.language.exams"
        minSdk = 26 // Covers over 90% of devices
        targetSdk = 35
        versionCode = VERSION_CODE
        versionName = VERSION_NAME

        testInstrumentationRunner =
            "com.goodstadt.john.language.exams.CustomTestRunner" // For Hilt testing

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

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            secretsProperties.getProperty("GEMINI_API_KEY")
        )

        // buildCon figField("String", "TTS _API_ KEY", "\"AIzaSyBSG jKuHGjf CHmfMNBHxD4w uH0CO GQ0biY\"")

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
            versionCode = VERSION_CODE
            versionName = VERSION_NAME
            buildConfigField("String", "LANGUAGE_ID", "\"en\"")
        }
        create("de") {
            dimension = "language"
            applicationIdSuffix = ".de"
            versionNameSuffix = "-de"
            versionCode = VERSION_CODE
            versionName = VERSION_NAME
            buildConfigField("String", "LANGUAGE_ID", "\"de\"")
        }
        create("zh") {
            dimension = "language"
            applicationIdSuffix = ".zh"
            versionNameSuffix = "-zh"
            versionCode = VERSION_CODE
            versionName = VERSION_NAME
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
    //            storeFile = file(secretsProperties.getProperty("MYAPP_UPLOAD_STORE_FILE"))

    signingConfigs {
        create("release") {
            storeFile = file("/Users/johngoodstadt/Library/Mobile Documents/com~apple~CloudDocs/keystore/android_memorize_law_demo")
            storePassword = secretsProperties.getProperty("MYAPP_UPLOAD_STORE_PASSWORD")
            keyAlias = secretsProperties.getProperty("MYAPP_UPLOAD_KEY_ALIAS")
            keyPassword = secretsProperties.getProperty("MYAPP_UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // BuildConfig field to detect debug builds at runtime
            buildConfigField("Boolean", "IS_DEBUG", "true")
            signingConfig = signingConfigs.getByName("debug")

        }
        release {

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // BuildConfig field for release builds
            buildConfigField("Boolean", "IS_DEBUG", "false")
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.google.ai.generativeai)

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
//    implementation(libs.billing.ktx)

    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Add this for JSON parsing
    implementation(libs.kotlinx.serialization.json)
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Firebase
//    implementation(platform(libs.firebase.bom))
//    implementation(libs.firebase.config.ktx)
//    implementation(libs.firebase.analytics)
//    implementation(libs.firebase.auth)
//    implementation(libs.firebase.firestore) {
//        exclude(group = "com.google.firebase", module = "firebase-common")
//    }
//    implementation(libs.firebase.firestore)
//    implementation(libs.google.firebase.config.ktx)
    implementation(libs.androidx.datastore.preferences)
////    implementation(platform(libs.google.play.services.bom))
//    implementation("com.google.android.gms:play-services-auth")

//    implementation("com.google.android.gms:play-services-base:18.7.2")

    //
    // 1. Declare the Bills of Materials (BOMs) FIRST.
    //    This tells Gradle to use these as the "master version list".
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx") // If you have it

    // This library is NOT part of the Firebase BOM, so it needs its own version.
    // Ensure this is defined correctly in your TOML file.
//    implementation(libs.androidx.billing.ktx)
    implementation(libs.billing)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)

    constraints {
        implementation("com.android.billingclient:billing:7.0.0") {
            because("Enforce consistent billing library version across all dependencies")
        }
        implementation("com.android.billingclient:billing-ktx:7.0.0") {
            because("Enforce consistent billing library version across all dependencies")
        }
    }

//    implementation(platform(libs.play.services.bom))

    // 2. Now declare all your Firebase and GMS libraries WITHOUT versions.
    //    The BOMs will provide the correct, compatible versions for all of them.
//    implementation(libs.firebase.auth)
//    implementation(libs.firebase.firestore)
//    implementation(libs.firebase.config.ktx)
//    implementation(libs.firebase.analytics) // If you decided to keep it
//
    // It's good practice to explicitly include common dependencies
//    implementation("com.google.firebase:firebase-common-ktx")
//    implementation("com.google.android.gms:play-services-base")

    // --- YOUR BILLING LIBRARY ---
    // This is an AndroidX library, not a GMS library, so it's fine on its own.
//    implementation(libs.androidx.billing.ktx)

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