package com.goodstadt.john.language.exams.data


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auditDataStore by preferencesDataStore(name = "readiness_audit_prefs")

@Singleton
class ReadinessAuditRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Keys for the 4 parts
    private val KEY_PART_1 = intPreferencesKey("audit_score_1")
    private val KEY_PART_2 = intPreferencesKey("audit_score_2")
    private val KEY_PART_3 = intPreferencesKey("audit_score_3")
    private val KEY_PART_4 = intPreferencesKey("audit_score_4")

    /**
     * Saves the score for a specific part (1-4).
     */
    suspend fun saveScore(partIndex: Int, score: Int) {
        context.auditDataStore.edit { prefs ->
            when (partIndex) {
                1 -> prefs[KEY_PART_1] = score
                2 -> prefs[KEY_PART_2] = score
                3 -> prefs[KEY_PART_3] = score
                4 -> prefs[KEY_PART_4] = score
            }
        }
    }

    /**
     * Returns all stored audit scores as a Map for the AuditEngine.
     */
    suspend fun getAuditScores(): Map<Int, Int> {
        val prefs = context.auditDataStore.data.first()
        val scores = mutableMapOf<Int, Int>()

        prefs[KEY_PART_1]?.let { scores[1] = it }
        prefs[KEY_PART_2]?.let { scores[2] = it }
        prefs[KEY_PART_3]?.let { scores[3] = it }
        prefs[KEY_PART_4]?.let { scores[4] = it }

        return scores
    }
}