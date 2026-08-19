package com.goodstadt.john.language.exams.packages.UploadJson

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/** One JSON asset file that can be uploaded to / read back from Firestore. */
data class UploadJsonFile(
    val displayName: String, // e.g. "WordQuizHello1-de.json"
    val assetPath: String    // e.g. "Quizzes/SectionQuiz/A1/WordQuizHello1-de.json"
)

/** Files grouped under one CEFR level within a section. */
data class UploadJsonLevelGroup(
    val level: String, // "A1" / "A2" / "B1" / "B2"
    val files: List<UploadJsonFile>
)

/** A top-level section on the Upload screen (Grammar / Section Sheet / UsageQuiz). */
data class UploadJsonSection(
    val title: String,
    val groups: List<UploadJsonLevelGroup>
)

/**
 * Backs the DEBUG-only "Upload JSON" screen: enumerates the bundled quiz JSON that should be pushed
 * to the German Firestore project, grouped by section and level. The Upload / Read actions are
 * placeholders for now (they just log + set a per-file status) - the real Firestore format is TBD.
 */
@HiltViewModel
class UploadJsonViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val levels = listOf("A1", "A2", "B1", "B2")

    private val _sections = MutableStateFlow<List<UploadJsonSection>>(emptyList())
    val sections = _sections.asStateFlow()

    // Per-file status line (keyed by assetPath), shown under the filename.
    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val statuses = _statuses.asStateFlow()

    init {
        buildSections()
    }

    private fun listJsonAssets(folder: String): List<UploadJsonFile> =
        try {
            context.assets.list(folder)
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                ?.map { UploadJsonFile(displayName = it, assetPath = "$folder/$it") }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "UploadJSON: failed to list assets in '$folder'")
            emptyList()
        }

    private fun buildSections() {
        // Grammar and Section Sheet are nested per level: Quizzes/<root>/<level>/*.json
        val grammar = UploadJsonSection(
            title = "Grammar",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, listJsonAssets("Quizzes/Grammar/$lvl"))
            }
        )
        val sectionSheet = UploadJsonSection(
            title = "Section Sheet",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, listJsonAssets("Quizzes/SectionQuiz/$lvl"))
            }
        )
        // UsageQuiz is a flat folder; group by the level token in the filename (e.g. UsageQuiz1A2-de.json).
        val usageAll = listJsonAssets("Quizzes/UsageQuiz")
        val usageQuiz = UploadJsonSection(
            title = "UsageQuiz",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, usageAll.filter { it.displayName.contains(lvl) })
            }
        )
        _sections.value = listOf(grammar, sectionSheet, usageQuiz)
    }

    // --- Placeholder actions (real Firestore logic to be filled in later) ---

    /** Upload this file's JSON to the German Firestore project. Placeholder for now. */
    fun uploadFile(file: UploadJsonFile) {
        Timber.i("UploadJSON: TODO upload '${file.assetPath}' to Firestore")
        setStatus(file, "Upload:    not implemented yet")
    }

    /** Check the doc exists in Firestore and decode it back into memory to confirm validity. Placeholder. */
    fun readFile(file: UploadJsonFile) {
        Timber.i("UploadJSON: TODO read + verify '${file.assetPath}' from Firestore")
        setStatus(file, "Read: not implemented yet")
    }

    private fun setStatus(file: UploadJsonFile, status: String) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(file.assetPath, status) }
    }
}
