package com.goodstadt.john.language.exams.models


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// 1. Define the extension property at the top level
// This creates the actual file on disk named "user_settings.preferences_pb"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    // 2. Tell Hilt how to provide the DataStore instance
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}