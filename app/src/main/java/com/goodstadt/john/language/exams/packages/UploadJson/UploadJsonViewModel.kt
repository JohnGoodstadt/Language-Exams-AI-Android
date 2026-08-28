package com.goodstadt.john.language.exams.packages.UploadJson

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.UsageQuizSheetMapping
import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename
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

/** One German vocab source exposed by the targeted field-update debug UI. */
data class UpdateFieldsFile(
    val displayName: String,
    val rawResourceName: String,
    val firestoreDocName: String
) {
    val statusKey: String get() = "update_fields/$firestoreDocName"
}

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

    // Firestore logical-name language prefix for the current flavour (matches the *SheetMapping helpers
    // and each flavour's Firestore project: GermanA1Vocab / EnglishA1Vocab, …).
    private val languagePrefix: String = when (BuildConfig.FLAVOR) {
        "de" -> "German"
        "en" -> "English"
        "zh" -> "Chinese"
        else -> "German"
    }

    /** The current flavour's A1 vocab sheet doc name (e.g. "GermanA1Vocab" / "EnglishA1Vocab"). */
    val a1VocabDocName: String get() = "${languagePrefix}A1Vocab"

    /** The targeted updater is intentionally invisible and unusable outside the German variant. */
    val isGermanVariant: Boolean = BuildConfig.FLAVOR == "de"

    val updateFieldsFiles: List<UpdateFieldsFile> = if (isGermanVariant) {
        levels.mapNotNull { level ->
            val rawName = "vocab_data_${level.lowercase()}"
            if (!rawResourceExists(rawName)) return@mapNotNull null
            UpdateFieldsFile(
                displayName = "German${level}Vocab",
                rawResourceName = rawName,
                firestoreDocName = "German${level}Vocab"
            )
        }
    } else {
        emptyList()
    }

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

    /** Reads /global/exam_sheets/sheets/<flavour>A1Vocab.uploadDate via the admin library and shows it. */
    fun readVocabUploadDate() {
        val repo = adminRepository.orElse(null)
        if (repo == null) {
            _adminResult.value = "Admin library not available in this build (debug only)."
            return
        }
        val docName = a1VocabDocName
        _adminResult.value = "Reading uploadDate…"
        viewModelScope.launch {
            _adminResult.value = repo.readUploadDate(docName).fold(
                onSuccess = { date -> "$docName uploadDate: ${date ?: "(no value / document not found)"}" },
                onFailure = { e -> "Error: ${e.localizedMessage ?: e.toString()}" }
            )
        }
    }

    /**
     * The bundled UsageQuiz file for an enum baseName, tolerating the enum's language suffix being wrong
     * (some `de` B2 entries are listed as "-de" but ship as "-en"). Prefers the exact suffix; otherwise
     * falls back to the canonical "-en" file. Never returns the regional variants (-in/-vn/-tr/-eg/-enAI)
     * - those are runtime, region-selected overrides, not upload targets. Null if nothing is bundled.
     */
    private fun resolveUsageQuizAsset(baseName: String): String? {
        val exact = "$baseName.json"
        if (assetExists("Quizzes/UsageQuiz/$exact")) return exact
        val stem = baseName.replace(Regex("-[a-z]{2}$"), "") // "UsageQuiz1B2"
        val enFallback = "$stem-en.json"
        return if (assetExists("Quizzes/UsageQuiz/$enFallback")) enFallback else null
    }

    private fun assetExists(assetPath: String): Boolean =
        try { context.assets.open(assetPath).close(); true } catch (e: Exception) { false }

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
     * The main vocab lists live in res/raw (vocab_data_<level>.json), each mapping to the flavour's
     * Firestore doc name ("$languagePrefix" + level + "Vocab", e.g. GermanA1Vocab / EnglishA1Vocab). One
     * row per level that this flavour actually bundles.
     */
    private fun buildVocabSection(): UploadJsonSection {
        val files = levels.mapNotNull { lvl ->
            val rawName = "vocab_data_${lvl.lowercase()}" // res/raw resource name
            if (!rawResourceExists(rawName)) return@mapNotNull null
            val docName = "$languagePrefix${lvl}Vocab"    // e.g. "EnglishA1Vocab" (the Firestore target)
            UploadJsonFile(
                displayName = docName,
                fileName = "$rawName.json",               // "vocab_data_a1.json"
                assetPath = "raw/$rawName.json",          // unique id; the "raw/" prefix marks a res/raw source
                firestoreDocName = docName
            )
        }
        return UploadJsonSection(VOCAB_SECTION_TITLE, listOf(UploadJsonLevelGroup(VOCAB_GROUP_LEVEL, files)))
    }

    /**
     * Reference-tab content (res/raw). The bundled set differs per flavour (e.g. `en` ships only
     * conjugations + prepositions; the other English reference sheets already live on Firestore), so we
     * probe res/raw for what THIS flavour actually bundles and skip the rest. Doc names are the
     * language-independent key with the flavour prefix ("$languagePrefix" + "ConjugationsToBe" …),
     * except shared sheets like DailyWordDictionary which have no prefix. Groups with no bundled file are
     * dropped. The upload/read routine is picked from each JSON's fileFormat field.
     */
    private fun buildReferenceSection(): UploadJsonSection {
        // First candidate raw name that exists in this flavour wins; null -> not bundled here, skip row.
        fun ref(candidates: List<String>, docName: String): UploadJsonFile? {
            val rawName = candidates.firstOrNull { rawResourceExists(it) } ?: return null
            return UploadJsonFile(
                displayName = docName,
                fileName = "$rawName.json",
                assetPath = "raw/$rawName.json",
                firestoreDocName = docName
            )
        }
        fun grp(title: String, rows: List<UploadJsonFile?>) = UploadJsonLevelGroup(title, rows.filterNotNull())

        val groups = listOf(
            grp("Adjectives", listOf(
                ref(listOf("german_a1_adjectives"), "${languagePrefix}A1Adjectives"),
                ref(listOf("german_a2_adjectives"), "${languagePrefix}A2Adjectives"),
                ref(listOf("german_b1_adjectives"), "${languagePrefix}B1Adjectives"),
                ref(listOf("german_b2_adjectives"), "${languagePrefix}B2Adjectives"),
            )),
            grp("Conjugations", listOf(
                ref(listOf("conjugations_to_be"), "${languagePrefix}ConjugationsToBe"),
                ref(listOf("conjugations_to_do"), "${languagePrefix}ConjugationsToDo"),
                ref(listOf("conjugations_to_get"), "${languagePrefix}ConjugationsToGet"),
                ref(listOf("conjugations_to_have"), "${languagePrefix}ConjugationsToHave"),
            )),
            grp("Word Pairs", listOf(
                ref(listOf("german_bringen_holen"), "${languagePrefix}BringenHolen"),
                ref(listOf("german_fragen_bitten"), "${languagePrefix}FragenBitten"),
                ref(listOf("german_hoeren_zuhoeren"), "${languagePrefix}HoerenZuhoeren"),
                ref(listOf("german_kennen_wissen"), "${languagePrefix}KennenWissen"),
            )),
            grp("Prepositions", listOf(
                ref(listOf("german_prepositions", "prepositions_en"), "${languagePrefix}Prepositions"),
            )),
            grp("Sounds the Same", listOf(
                ref(listOf("german_sounds_the_same"), "${languagePrefix}SoundsTheSame"), // fileFormat 1
            )),
            grp("Word of the Day", listOf(
                ref(listOf("daily_word_dictionary_pool_v1"), "DailyWordDictionary"), // shared, no prefix
            )),
        ).filter { it.files.isNotEmpty() } // drop groups this flavour doesn't bundle

        return UploadJsonSection("Reference", groups)
    }

    /** True if a res/raw resource with this base name exists in the current flavour's build. */
    private fun rawResourceExists(rawName: String): Boolean =
        context.resources.getIdentifier(rawName, "raw", context.packageName) != 0

    companion object {
        const val VOCAB_SECTION_TITLE = "Main Vocab"
        const val VOCAB_GROUP_LEVEL = "Files"
        const val UPDATE_FIELDS_SECTION_TITLE = "Update DE Fields"
        const val UPDATE_FIELDS_GROUP_TITLE = "Sheets"

        // TEMPORARY TEST GUARD: true commits sheetName + the first word atomically, then exits.
        // Change to false only after the first Firestore document has been checked.
        const val UPDATE_FIELDS_TEST_STOP_AFTER_FIRST_WORD = false
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
        // UsageQuiz: drive the rows from the flavour's authoritative UsageQuizLevelsFilename enum, NOT a
        // folder listing. The en folder ships extra language/AI variants of each quiz (-eg, -in, -tr, -vn,
        // -enAI) that must NOT be uploaded, and a crude "filename contains level" filter also duplicated
        // rows (e.g. every *B1* variant landed in B1). Each enum entry pins the exact baseName the app
        // actually uses (e.g. "UsageQuiz1B1-en") and its ESOL level. Firestore doc name =
        // <Lang>UsageQuiz<n><Level> via UsageQuizSheetMapping (e.g. -> "EnglishUsageQuiz1B1").
        val usageQuiz = UploadJsonSection(
            title = "UsageQuiz",
            groups = levels.map { lvl ->
                val quizzes = UsageQuizLevelsFilename.entries
                    .firstOrNull { it.ESOL == lvl }?.quizzes.orEmpty()
                UploadJsonLevelGroup(
                    level = lvl,
                    files = quizzes.mapNotNull { quiz ->
                        // Resolve the enum baseName to the real asset (some de B2 entries say -de but ship
                        // as -en). Skip if no bundled file exists.
                        val fileName = resolveUsageQuizAsset(quiz.baseName) ?: run {
                            Timber.w("UploadJSON: no UsageQuiz asset for '${quiz.baseName}'")
                            return@mapNotNull null
                        }
                        val docName = UsageQuizSheetMapping.normalizeToLogicalName(quiz.baseName)
                        UploadJsonFile(
                            displayName = docName,   // e.g. "EnglishUsageQuiz1A1" (the Firestore target)
                            fileName = fileName,
                            assetPath = "Quizzes/UsageQuiz/$fileName",
                            firestoreDocName = docName
                        )
                    }
                )
            }
        )
        // Baseline / Readiness Audit sheets (Quizzes/ReadinessAudit, flat folder, fileFormat 10). 8 files
        // per flavour. Firestore doc name = languagePrefix + base filename (minus -de/-en), e.g.
        // "AuditA2-1-de.json" -> "GermanAuditA2-1", "BaselineAuditA2-1-en.json" -> "EnglishBaselineAuditA2-1".
        val baselineFiles = (context.assets.list("Quizzes/ReadinessAudit") ?: emptyArray())
            .filter { it.endsWith(".json") }
            .sorted()
            .map { fileName ->
                val base = fileName.removeSuffix(".json").replace(Regex("-[a-z]{2}$"), "") // "AuditA2-1"
                val docName = "$languagePrefix$base"                                        // "GermanAuditA2-1"
                UploadJsonFile(
                    displayName = docName,
                    fileName = fileName,
                    assetPath = "Quizzes/ReadinessAudit/$fileName",
                    firestoreDocName = docName
                )
            }
        val baselineQuiz = UploadJsonSection(
            title = "Baseline Quiz",
            groups = listOf(UploadJsonLevelGroup("Files", baselineFiles))
        )

        _sections.value = listOf(
            buildVocabSection(), buildReferenceSection(), grammar, sectionSheet, usageQuiz, baselineQuiz
        )
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

    /** Updates one existing German vocab sheet from its bundled res/raw source. */
    @Suppress("FunctionName")
    fun UpdateFields(file: UpdateFieldsFile) {
        if (!isGermanVariant) {
            failUpdate(file, IllegalStateException("Update DE Fields is available only in the de variant"))
            return
        }

        viewModelScope.launch {
            val repo = adminRepository.orElse(null)
            if (repo == null) {
                failUpdate(file, IllegalStateException("Admin library unavailable (debug only)"))
                return@launch
            }
            val json = readRawResourceContent(file.rawResourceName)
            if (json == null) {
                failUpdate(file, IllegalStateException("Could not read ${file.rawResourceName}.json"))
                return@launch
            }

            repo.updateVocabFields(
                docName = file.firestoreDocName,
                json = json,
                stopAfterFirstWord = UPDATE_FIELDS_TEST_STOP_AFTER_FIRST_WORD
            ).fold(
                onSuccess = { summary ->
                    setStatus(file.statusKey, RowStatus.SUCCESS)
                    _toast.tryEmit(summary)
                },
                onFailure = { error -> failUpdate(file, error) }
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

    private fun readRawResourceContent(rawResourceName: String): String? = try {
        val resourceId = context.resources.getIdentifier(rawResourceName, "raw", context.packageName)
        if (resourceId == 0) {
            Timber.e("UploadJSON: raw resource '$rawResourceName' not found")
            null
        } else {
            context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        Timber.e(e, "UploadJSON: failed to read raw resource '$rawResourceName'")
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

    private fun failUpdate(file: UpdateFieldsFile, error: Throwable) {
        Timber.e(error, "UploadJSON: UpdateFields failed for '${file.firestoreDocName}'")
        setStatus(file.statusKey, RowStatus.ERROR)
        _toast.tryEmit(
            "Update failed: ${file.firestoreDocName}\n${error.localizedMessage ?: error.toString()}"
        )
    }

    private fun setStatus(file: UploadJsonFile, status: RowStatus) {
        setStatus(file.assetPath, status)
    }

    private fun setStatus(statusKey: String, status: RowStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            // NONE = blank; don't persist it, just clear the row so a blank survives a restart too.
            if (status == RowStatus.NONE) remove(statusKey) else put(statusKey, status)
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
