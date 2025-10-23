package com.goodstadt.john.language.exams.di

import android.content.Context
import android.content.SharedPreferences
import com.goodstadt.john.language.exams.data.AudioPlayerService
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Hilt already knows how to provide these because they have @Inject constructors,
    // but being explicit in a module is also good practice.
    // We only need to be explicit if the class has dependencies that are not
    // directly injectable (like Context).

    @Provides
    @Singleton
    fun provideGoogleCloudTTS(): GoogleCloudTTS {
        return GoogleCloudTTS()
    }

    @Provides
    @Singleton
    fun provideAudioPlayerService(): AudioPlayerService {
        return AudioPlayerService()
    }

    // Since VocabRepository now has dependencies, we need to explicitly provide it.
    /*
    @Provides
    @Singleton
    fun provideVocabRepository(
        @ApplicationContext context: Context,
        googleCloudTts: GoogleCloudTTS,
        audioPlayerService: AudioPlayerService,
        userPreferencesRepository: UserPreferencesRepository
    ): VocabRepository {
        return VocabRepository(context, googleCloudTts, audioPlayerService, userPreferencesRepository)
    }
*/
    @Singleton
    @Provides
    fun provideJsonParser(): Json {
        // We create a single, reusable instance of the Json parser for the whole app.
        // `ignoreUnknownKeys = true` is a robust setting that prevents crashes if
        // your remote JSON ever has extra fields that your data class doesn't.
        return Json { ignoreUnknownKeys = true }
    }

    @Provides
    @Singleton
    fun provideControlRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository
    ): ControlRepository {
        return ControlRepository(context,userPreferencesRepository)
    }

    @Singleton // We want a single instance of SharedPreferences for the whole app
    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        // Hilt already knows how to provide the @ApplicationContext,
        // so we can use it here to create the SharedPreferences.
        return context.getSharedPreferences("app_main_prefs", Context.MODE_PRIVATE)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object AppModule {
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}