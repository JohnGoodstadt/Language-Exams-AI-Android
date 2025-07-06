// <project-root>/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    // Add this for Hilt compiler
    kotlin("kapt")
    kotlin("plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.goodstadt.john.language.exams" // Base namespace
    compileSdk = 34

    defaultConfig {
        applicationId = "com.goodstadt.john.language.exams"
        minSdk = 26 // Covers over 90% of devices
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.goodstadt.john.language.exams.CustomTestRunner" // For Hilt testing

        buildConfigField("String", "TTS_API_KEY", "\"AIzaSyBSGjKuHGjfCHmfMNBHxD4wuH0COGQ0biY\"")

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
        }
        create("de") {
            dimension = "language"
            applicationIdSuffix = ".de"
            versionNameSuffix = "-de"
        }
        create("zh") {
            dimension = "language"
            applicationIdSuffix = ".zh"
            versionNameSuffix = "-zh"
        }
    }

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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
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
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.material.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Add this for JSON parsing
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented/UI Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Allow Hilt to access classes in different build variants
kapt {
    correctErrorTypes = true
}