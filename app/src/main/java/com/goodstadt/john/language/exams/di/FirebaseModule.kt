package com.goodstadt.john.language.exams.di // Create a 'di' package if you don't have one

import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton // We only want one instance of Remote Config for the whole app
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        // 1. Get the instance of Remote Config
        val remoteConfig = Firebase.remoteConfig

        // 2. Create configuration settings
        val configSettings = remoteConfigSettings {
            // Set a low fetch interval for debug builds to allow for rapid testing.
            // For release builds, a higher value (e.g., 12 hours) is recommended.
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                0
            } else {
                3600 // 1 hour
            }
        }

        // 3. Apply the settings
        remoteConfig.setConfigSettingsAsync(configSettings)

        // 4. Set default values. This is a crucial step.
        // It ensures your app has values to work with on the very first launch,
        // even before it has fetched anything from the server.
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        return remoteConfig
    }

}