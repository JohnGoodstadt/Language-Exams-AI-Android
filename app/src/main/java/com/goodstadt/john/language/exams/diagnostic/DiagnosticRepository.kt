package com.goodstadt.john.language.exams.diagnostic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore by preferencesDataStore(name = "diagnostic_prefs")

@Singleton
class DiagnosticRepository @Inject constructor(@ApplicationContext private val context: Context ) {

    private val KEY_COMPLETED = booleanPreferencesKey("diagnostic_completed")
    private val KEY_READINESS = floatPreferencesKey("exam_readiness_score")

    // Reactive flow for the rest of the app to observe
    val readinessScore: Flow<Float> = context.dataStore.data.map { it[KEY_READINESS] ?: 0.0f }
    val isDiagnosticComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_COMPLETED] ?: false }

    suspend fun saveResult(score: Int) {
        // Calculate the 5% per question logic (Max 50% as discussed)
        val percentage = (score * 0.05f).coerceAtMost(0.50f)

        context.dataStore.edit { prefs ->
            prefs[KEY_COMPLETED] = true
            prefs[KEY_READINESS] = percentage
        }
    }

    suspend fun skipDiagnostic() {
        context.dataStore.edit { it[KEY_COMPLETED] = true }
    }

    fun calculateCalibration(scores: List<Int>): Pair<Float, Float> {
        val completed = scores.size
        if (completed == 0) return 0f to 0f

        // 1. Calculate Confidence based on completion
        val confidence = when (completed) {
            1 -> 0.40f
            2 -> 0.65f
            3 -> 0.85f
            4 -> 0.98f
            else -> 0f
        }

        // 2. Calculate Readiness
        // Total possible is 40. We calculate the raw average and
        // apply a "sampling penalty" for low confidence.
        val rawAverage = scores.sum().toFloat() / (completed * 10).toFloat()

        // Readiness = Raw Performance x Confidence
        // If they get 10/10 on Test 1, Readiness is 40%.
        // If they get 40/40 on all 4, Readiness is 98%.
        val readiness = rawAverage * confidence

        return confidence to readiness
    }
}