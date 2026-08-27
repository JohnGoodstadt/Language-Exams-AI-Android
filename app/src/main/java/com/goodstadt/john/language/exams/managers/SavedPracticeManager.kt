package com.goodstadt.john.language.exams.managers

import android.content.Context
import com.goodstadt.john.language.exams.models.SavedSentence
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's "saved for practice" list: sentences they swiped to Save on the vocab tabs, so a future
 * "Me"-tab screen can list them for focused practice.
 *
 * - **Level-aware:** stored as `level -> (id -> SavedSentence)`, so A1/A2/B1/B2 each have their own list;
 *   switching level shows a different set.
 * - **Local-only** (like the spaced-repetition data): persisted to `saved_practice.json` in internal
 *   storage. Cloud sync could be a future extension.
 * - **Reactive:** [savedState] lets the future read screen observe changes live.
 */
@Singleton
class SavedPracticeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FILENAME = "saved_practice.json"
    }

    private val gson = Gson()
    private val file = File(context.filesDir, FILENAME)

    // level -> (sentenceId -> entry)
    private var saved: MutableMap<String, MutableMap<String, SavedSentence>> = mutableMapOf()

    private val _savedState = MutableStateFlow<Map<String, Map<String, SavedSentence>>>(emptyMap())
    /** Full saved map (level -> id -> entry) for reactive UIs. Use [getForLevel] for a level's list. */
    val savedState = _savedState.asStateFlow()

    init {
        load()
    }

    /** Is this sentence currently saved for [level]? */
    fun isSaved(level: String, id: String): Boolean =
        saved[level]?.containsKey(id) == true

    /**
     * Toggle a sentence in the saved list for its level. Returns true if it is now SAVED, false if it was
     * removed. (Swiping Save on an already-saved row removes it.)
     */
    fun toggle(entry: SavedSentence): Boolean {
        val levelMap = saved.getOrPut(entry.level) { mutableMapOf() }
        val nowSaved: Boolean = if (levelMap.containsKey(entry.id)) {
            levelMap.remove(entry.id)
            false
        } else {
            levelMap[entry.id] = entry
            true
        }
        persist()
        emit()
        return nowSaved
    }

    fun remove(level: String, id: String) {
        saved[level]?.remove(id)
        persist()
        emit()
    }

    /** Saved sentences for a level, newest first — for the future practice list screen. */
    fun getForLevel(level: String): List<SavedSentence> =
        saved[level]?.values?.sortedByDescending { it.savedAt } ?: emptyList()

    // --- persistence ---

    private fun persist() {
        try {
            file.writeText(gson.toJson(saved))
        } catch (e: Exception) {
            Timber.e(e, "SavedPracticeManager: failed to persist saved_practice.json")
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val type = object : TypeToken<MutableMap<String, MutableMap<String, SavedSentence>>>() {}.type
            saved = gson.fromJson(file.readText(), type) ?: mutableMapOf()
            emit()
        } catch (e: Exception) {
            Timber.e(e, "SavedPracticeManager: failed to load saved_practice.json")
            saved = mutableMapOf()
        }
    }

    private fun emit() {
        _savedState.value = saved.mapValues { it.value.toMap() }
    }
}
