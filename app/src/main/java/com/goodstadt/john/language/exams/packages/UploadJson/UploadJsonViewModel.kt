package com.goodstadt.john.language.exams.packages.UploadJson

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.Optional
import javax.inject.Inject

/** One JSON file that can be uploaded to / read back from Firestore. */
data class UploadJsonFile(
    val displayName: String, // short label shown in the UI, e.g. "Modal Verbs"
    val fileName: String,    // the REAL filename, kept for uploading, e.g. "GrammarModalVerbs-de.json"
    val assetPath: String,   // unique id + source: an asset path, or "raw/<name>.json" for a res/raw file
    val firestoreDocName: String = "" // target Firestore doc name (set for vocab; TBD for quiz files)
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

/** Per-row result flag: NONE (not done, empty), SUCCESS (tick), ERROR (cross). */
enum class RowStatus { NONE, SUCCESS, ERROR }

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

    // Per-file result flag (keyed by assetPath), shown as a tick / cross / empty in each row.
    private val _statuses = MutableStateFlow<Map<String, RowStatus>>(emptyMap())
    val statuses = _statuses.asStateFlow()

    // One-shot messages for the UI to show as a long Toast (e.g. upload errors).
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast.asSharedFlow()

    // Result of the top-of-screen admin action (read GermanA1Vocab uploadDate).
    private val _adminResult = MutableStateFlow<String?>(null)
    val adminResult = _adminResult.asStateFlow()

    /** True when the admin library is available (German debug build only). */
    val isAdminAvailable: Boolean get() = adminRepository.isPresent

    // Declared BEFORE init so loadStatuses() (called from init) can use it.
    private val statusPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        buildSections()
        loadStatuses() // restore ticks/crosses saved from a previous run
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

    private fun listJsonAssets(
        folder: String,
        stripPrefix: String,
        docNameFor: (String) -> String = { "" }
    ): List<UploadJsonFile> =
        try {
            context.assets.list(folder)
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                ?.map { fileName ->
                    UploadJsonFile(
                        displayName = shortDisplayName(fileName, stripPrefix),
                        fileName = fileName,
                        assetPath = "$folder/$fileName",
                        firestoreDocName = docNameFor(fileName)
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

    /**
     * The main vocab lists live in res/raw (vocab_data_<level>.json), not in assets, and each maps to
     * a fixed Firestore doc name (GermanA1Vocab …) matching the completed English project so the same
     * Android/iOS code runs. One row per level.
     */
    private fun buildVocabSection(): UploadJsonSection {
        val vocab = listOf(
            "A1" to "GermanA1Vocab",
            "A2" to "GermanA2Vocab",
            "B1" to "GermanB1Vocab",
            "B2" to "GermanB2Vocab",
        )
        val files = vocab.map { (lvl, docName) ->
            val rawName = "vocab_data_${lvl.lowercase()}" // res/raw resource name
            UploadJsonFile(
                displayName = docName,             // e.g. "GermanA1Vocab" (the Firestore target)
                fileName = "$rawName.json",        // "vocab_data_a1.json"
                assetPath = "raw/$rawName.json",   // unique id; the "raw/" prefix marks a res/raw source
                firestoreDocName = docName
            )
        }
        return UploadJsonSection(VOCAB_SECTION_TITLE, listOf(UploadJsonLevelGroup(VOCAB_GROUP_LEVEL, files)))
    }

    /**
     * Reference-tab content (res/raw). A mix of fileFormat 1 (adjectives, conjugations, prepositions -
     * same structure as fileFormat 0) and fileFormat 2 (word pairs). The upload/read routine is picked
     * from the JSON's fileFormat field. Firestore doc names are regularised (e.g. german_a1_adjectives
     * -> GermanA1Adjectives).
     */
    private fun buildReferenceSection(): UploadJsonSection {
        fun ref(rawName: String, docName: String) = UploadJsonFile(
            displayName = docName,
            fileName = "$rawName.json",
            assetPath = "raw/$rawName.json",
            firestoreDocName = docName
        )
        val adjectives = UploadJsonLevelGroup(
            "Adjectives",
            listOf(
                ref("german_a1_adjectives", "GermanA1Adjectives"),
                ref("german_a2_adjectives", "GermanA2Adjectives"),
                ref("german_b1_adjectives", "GermanB1Adjectives"),
                ref("german_b2_adjectives", "GermanB2Adjectives")
            )
        )
        val conjugations = UploadJsonLevelGroup(
            "Conjugations",
            listOf(
                ref("conjugations_to_be", "GermanConjugationsToBe"),
                ref("conjugations_to_do", "GermanConjugationsToDo"),
                ref("conjugations_to_get", "GermanConjugationsToGet"),
                ref("conjugations_to_have", "GermanConjugationsToHave")
            )
        )
        val wordPairs = UploadJsonLevelGroup(
            "Word Pairs",
            listOf(
                ref("german_bringen_holen", "GermanBringenHolen"),
                ref("german_fragen_bitten", "GermanFragenBitten"),
                ref("german_hoeren_zuhoeren", "GermanHoerenZuhoeren"),
                ref("german_kennen_wissen", "GermanKennenWissen")
            )
        )
        val prepositions = UploadJsonLevelGroup(
            "Prepositions",
            listOf(ref("german_prepositions", "GermanPrepositions"))
        )
        return UploadJsonSection("Reference", listOf(adjectives, conjugations, wordPairs, prepositions))
    }

    companion object {
        const val VOCAB_SECTION_TITLE = "Main Vocab"
        const val VOCAB_GROUP_LEVEL = "Files"
        private const val PREFS_NAME = "upload_json_status"
        private const val PREFS_STATUSES_KEY = "statuses"
    }

    private fun buildSections() {
        // Grammar and Section Sheet are nested per level: Quizzes/<root>/<level>/*.json
        val grammar = UploadJsonSection(
            title = "Grammar",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(
                    lvl,
                    // Firestore doc name embeds the level: GrammarModalVerbs-de.json (A1) -> GermanA1ModalVerbs
                    listJsonAssets(
                        "Quizzes/Grammar/$lvl",
                        stripPrefix = "Grammar",
                        docNameFor = { fileName ->
                            com.goodstadt.john.language.exams.data.GrammarSheetMapping
                                .normalizeToLogicalName(fileName, lvl)
                        }
                    )
                )
            }
        )
        val sectionSheet = UploadJsonSection(
            title = "Section Sheet",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(
                    lvl,
                    // Firestore doc name embeds the level: WordQuizAdjectives1-de.json (A1) -> GermanA1Adjectives1
                    listJsonAssets(
                        "Quizzes/SectionQuiz/$lvl",
                        stripPrefix = "WordQuiz",
                        docNameFor = { fileName ->
                            com.goodstadt.john.language.exams.data.SectionQuizSheetMapping
                                .normalizeToLogicalName(fileName, lvl)
                        }
                    )
                )
            }
        )
        // UsageQuiz is a flat folder; group by the level token in the real filename (e.g. UsageQuiz1A2-de.json).
        // Firestore doc name = German + the base filename: UsageQuiz1A1-de.json -> GermanUsageQuiz1A1.
        val usageAll = listJsonAssets(
            "Quizzes/UsageQuiz",
            stripPrefix = "UsageQuiz",
            docNameFor = { fileName ->
                com.goodstadt.john.language.exams.data.UsageQuizSheetMapping.normalizeToLogicalName(fileName)
            }
        )
        val usageQuiz = UploadJsonSection(
            title = "UsageQuiz",
            groups = levels.map { lvl ->
                UploadJsonLevelGroup(lvl, usageAll.filter { it.fileName.contains(lvl) })
            }
        )
        _sections.value = listOf(buildVocabSection(), buildReferenceSection(), grammar, sectionSheet, usageQuiz)
    }

    // --- Row actions. Each returns a Result; a tick on success, a cross + log + long Toast on error. ---

    /** Upload this file's JSON to the German Firestore project. */
    fun uploadFile(file: UploadJsonFile) {
        viewModelScope.launch {
            val repo = adminRepository.orElse(null)
            if (repo == null) {
                fail(file, "Upload", IllegalStateException("Admin library unavailable (German debug only)"))
                return@launch
            }
            if (file.firestoreDocName.isBlank()) {
                fail(file, "Upload", IllegalStateException("No Firestore sheet name set for ${file.fileName}"))
                return@launch
            }

            // Guard against a SECOND upload (there is no Delete yet): if the sheet already exists, block.
            val alreadyExists = repo.sheetExists(file.firestoreDocName).getOrElse { e ->
                fail(file, "Upload", e) // couldn't even check -> treat as an error
                return@launch
            }
            if (alreadyExists) {
                Timber.w("UploadJSON: '${file.firestoreDocName}' already exists - upload blocked (no Delete yet)")
                _toast.tryEmit(
                    "Already uploaded: ${file.fileName}\nDelete '${file.firestoreDocName}' in Firestore first (Delete not implemented yet)."
                )
                return@launch // leave the existing flag unchanged
            }

            applyResult(file, action = "Upload", result = performUpload(file))
        }
    }

    /**
     * Read the sheet back from Firestore. Distinguishes three outcomes:
     *  - not present (never uploaded) -> blank flag (NONE), no toast;
     *  - exists + reads cleanly       -> tick (SUCCESS);
     *  - real error (permission, bad format at a lower level, …) -> cross (ERROR) + toast.
     */
    fun readFile(file: UploadJsonFile) {
        viewModelScope.launch {
            val repo = adminRepository.orElse(null)
            if (repo == null) {
                fail(file, "Read", IllegalStateException("Admin library unavailable (German debug only)"))
                return@launch
            }
            if (file.firestoreDocName.isBlank()) {
                fail(file, "Read", IllegalStateException("No Firestore sheet name set for ${file.fileName}"))
                return@launch
            }
            repo.readSheet(file.firestoreDocName).fold(
                onSuccess = { summary ->
                    if (summary == null) {
                        Timber.i("UploadJSON: '${file.firestoreDocName}' not present -> blank")
                        setStatus(file, RowStatus.NONE)
                    } else {
                        Timber.i("UploadJSON: read OK - $summary")
                        setStatus(file, RowStatus.SUCCESS)
                    }
                },
                onFailure = { e -> fail(file, "Read", e) }
            )
        }
    }

    // Upload goes through the admin library (German debug only), targeting the sheet document
    // /global/exam_sheets/sheets/<firestoreDocName>.
    private suspend fun performUpload(file: UploadJsonFile): Result<Unit> {
        val repo = adminRepository.orElse(null)
            ?: return Result.failure(IllegalStateException("Admin library unavailable (German debug only)"))
        if (file.firestoreDocName.isBlank())
            return Result.failure(IllegalStateException("No Firestore sheet name set for ${file.fileName}"))
        val json = readFileContent(file)
            ?: return Result.failure(IllegalStateException("Could not read ${file.fileName} from the app bundle"))
        return repo.uploadSheet(file.firestoreDocName, json)
    }

    /** Reads a bundled file's JSON: from res/raw when assetPath starts with "raw/", else from assets. */
    private fun readFileContent(file: UploadJsonFile): String? = try {
        if (file.assetPath.startsWith("raw/")) {
            val resName = file.fileName.removeSuffix(".json")
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId == 0) {
                Timber.e("UploadJSON: raw resource '$resName' not found")
                null
            } else {
                context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
            }
        } else {
            context.assets.open(file.assetPath).bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadJSON: failed to read content for '${file.assetPath}'")
        null
    }

    /** Turn a Result into the row's tick/cross, logging + toasting on error. */
    private fun applyResult(file: UploadJsonFile, action: String, result: Result<Unit>) {
        result.fold(
            onSuccess = { setStatus(file, RowStatus.SUCCESS) },
            onFailure = { e -> fail(file, action, e) }
        )
    }

    /** Cross the row, log the error, and show a long Toast. */
    private fun fail(file: UploadJsonFile, action: String, e: Throwable) {
        Timber.e(e, "UploadJSON: $action failed for '${file.assetPath}'")
        setStatus(file, RowStatus.ERROR)
        _toast.tryEmit("$action failed: ${file.fileName}\n${e.localizedMessage ?: e.toString()}")
    }

    private fun setStatus(file: UploadJsonFile, status: RowStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            // NONE = blank; don't persist it, just clear the row so a blank survives a restart too.
            if (status == RowStatus.NONE) remove(file.assetPath) else put(file.assetPath, status)
        }
        persistStatuses()
    }

    // --- Persistence: remember each row's tick/cross across app restarts (SharedPreferences). ---

    private fun loadStatuses() {
        val stored = statusPrefs.getString(PREFS_STATUSES_KEY, null) ?: return
        try {
            val obj = JSONObject(stored)
            val restored = mutableMapOf<String, RowStatus>()
            obj.keys().forEach { key ->
                runCatching { RowStatus.valueOf(obj.getString(key)) }.getOrNull()?.let { restored[key] = it }
            }
            _statuses.value = restored
        } catch (e: Exception) {
            Timber.e(e, "UploadJSON: failed to load saved statuses")
        }
    }

    private fun persistStatuses() {
        try {
            val obj = JSONObject()
            _statuses.value.forEach { (key, status) -> obj.put(key, status.name) }
            statusPrefs.edit().putString(PREFS_STATUSES_KEY, obj.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "UploadJSON: failed to save statuses")
        }
    }
}
