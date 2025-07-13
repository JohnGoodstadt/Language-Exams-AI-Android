package com.goodstadt.john.language.exams.di

import android.content.Context
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

    @Provides
    @Singleton
    fun provideControlRepository(
        @ApplicationContext context: Context
    ): ControlRepository {
        return ControlRepository(context)
    }
}