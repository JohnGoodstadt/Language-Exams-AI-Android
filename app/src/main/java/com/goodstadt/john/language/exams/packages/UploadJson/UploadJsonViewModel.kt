package com.goodstadt.john.language.exams.packages.UploadJson

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Optional
import javax.inject.Inject

/** One JSON asset file that can be uploaded to / read back from Firestore. */
data class UploadJsonFile(
    val displayName: String, // short label shown in the UI, e.g. "Modal Verbs"
    val fileName: String,    // the REAL filename, kept for uploading, e.g. "GrammarModalVerbs-de.json"
    val assetPath: String    // e.g. "Quizzes/Grammar/A1/GrammarModalVerbs-de.json"
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
    @ApplicationContext private val context: Context,
    // Present only in the German debug build (src/deDebug provides the binding); empty otherwise.
    private val adminRepository: Optional<UploadAdminRepository>
) : ViewModel() {

    private val levels = listOf("A1", "A2", "B1", "B2")

    private val _sections = MutableStateFlow<List<UploadJsonSection>>(emptyList())
    val sections = _sections.asStateFlow()

    // Per-file status line (keyed by assetPath), shown under the filename.
    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val statuses = _statuses.asStateFlow()

    // Result of the top-of-screen admin action (read GermanA1Vocab uploadDate).
    private val _adminResult = MutableStateFlow<String?>(null)
    val adminResult = _adminResult.asStateFlow()

    /** True when the admin library is available (German debug build only). */
    val isAdminAvailable: Boolean get() = adminRepository.isPresent

    init {
        buildSections()
    }

    /** Reads /global/exam_sheets/sheets/GermanA1Vocab.uploadDate via the admin library and shows it. */
    fun readGermanA1UploadDate() {
        val repo = adminRepository.orElse(null)
        if (repo == null) {
            _adminResult.value = "Admin library not available in this build (German debug only)."
            return
        }
        _adminResult.value = "Reading uploadDate…"
        viewModelScope.launch {
            _adminResult.value = repo.readUploadDate().fold(
                onSuccess = { date -> "GermanA1Vocab uploadDate: ${date ?: "(no value / document not found)"}" },
                onFailure = { e -> "Error: ${e.localizedMessage ?: e.toString()}" }
            )
        }
    }

    private fun listJsonAssets(folder: String, stripPrefix: String): List<UploadJsonFile> =
        try {
            context.assets.list(folder)
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                ?.map { fileName ->
                    UploadJsonFile(
                        displayName = shortDisplayName(fileName, stripPrefix),
                        fileName = fileName,
                        assetPath = "$folder/$fileName"
                    )
                }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "UploadJSON: failed to list assets in '$folder'")
            emptyList()
        }

    /**
     * Turns a raw quiz filename into a short, human label: drop the section prefix (e.g. "Grammar"),
     * the "-de"/"-en" language suffix and the ".json" extension, then split CamelCase into words.
     * "GrammarModalVerbs-de.json" -> "Modal Verbs"; "WordQuizAdjectives1-de.json" -> "Adjectives 1".
     */
    private fun shortDisplayName(fileName: String, prefix: String): String {
        var s = fileName.removeSuffix(".json")
        s = s.replace(Regex("-[a-z]{2}$"), "")               // drop -de / -en language suffix
        if (s.startsWith(prefix)) s = s.substring(prefix.length)
        s = s.replace(Regex("(?<=[a-zäöüß])(?=[A-ZÄÖÜ])"), " ")  // CamelCase -> words
        s = s.replace(Regex("(?<=[a-zäöüß])(?=\\d)"), " ")       // letter -> number boundary
        return s.trim().ifEmpty { fileName.removeSuffix(".json") }
    }

    private fun buildSections() {
        // Grammar and Section Sheet are nested per level: Quizzes/<root>/<level>/*.json
        val grammar = UploadJsonSection(
            title = "Grammar",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, listJsonAssets("Quizzes/Grammar/$lvl", stripPrefix = "Grammar"))
            }
        )
        val sectionSheet = UploadJsonSection(
            title = "Section Sheet",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, listJsonAssets("Quizzes/SectionQuiz/$lvl", stripPrefix = "WordQuiz"))
            }
        )
        // UsageQuiz is a flat folder; group by the level token in the real filename (e.g. UsageQuiz1A2-de.json).
        val usageAll = listJsonAssets("Quizzes/UsageQuiz", stripPrefix = "UsageQuiz")
        val usageQuiz = UploadJsonSection(
            title = "UsageQuiz",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, usageAll.filter { it.fileName.contains(lvl) })
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
