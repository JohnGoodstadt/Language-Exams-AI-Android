package com.goodstadt.john.language.exams.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What one offline download put on the device, so it can be removed later without touching files the user
 * already had from tapping. `added` / `alreadyHad` are local filenames in filesDir.
 */
@Serializable
data class OfflinePackRecord(
    val id: String,
    val sheetName: String,
    val level: String,
    val voice: String,
    val total: Int,
    val added: List<String>,
    val alreadyHad: List<String>,
    val unavailable: Int,
    val lastRun: Long
)

enum class OfflineConnection { WIFI, CELLULAR, NONE }

/** One sentence to download and the voice it plays in. */
data class OfflineAudioItem(val text: String, val voice: String)

enum class ReferenceFormat { VOCAB, FORMAT1, FORMAT2, FORMAT6, FORMAT8, FORMAT9 }

/**
 * Downloads the vocab tab audio (each word's first sentence, the text a vocab row plays) for one sheet in one
 * voice from cloud storage into filesDir, under the normal local name, so taps play it with no internet.
 *
 * Cloud only - it never calls Google TTS. Files are named exactly as on iOS (iOS fills the cloud for the default
 * voices); old Android-named uploads are a fallback. Same design as the iOS OfflineAudioDownloader.
 * Runs in its own scope, so the user can keep studying; pauses on network loss / mobile data.
 */
@Singleton
class OfflineAudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
) {
    companion object {
        private const val PARALLEL_DOWNLOADS = 5
        /** Per-file limit; with no network Firebase would otherwise retry one file for 10 minutes. */
        private const val DOWNLOAD_TIMEOUT_MS = 20_000L
        const val ESTIMATED_BYTES_PER_FILE = 25_000
        const val WAITING_FOR_WIFI = "Waiting for Wi-Fi"
        const val WAITING_FOR_CONNECTION = "Waiting for a connection"
        /** Logcat tag for every download line. Info level, so release builds show them too (RELEASE_LOGCAT). */
        private const val TAG = "OfflineDL"
        /** "not in cloud" lines logged per run; the rest are only counted, to keep Logcat readable. */
        private const val MAX_MISSING_LOGGED = 30

        fun packId(sheetName: String, voice: String) = "vocab-$sheetName-$voice"
        fun referencePackId(language: String, voice: String) = "reference-$language-$voice"

        /**
         * The Reference tab sheets (as in debug_manifest.json) whose audio "Reference Pages" downloads. Leaves out
         * the local-language sheets (Spanish, Portuguese, Indian) and the quizzes. Same list as iOS.
         */
        val referenceSheets: Map<String, List<Pair<String, ReferenceFormat>>> = mapOf(
            "German" to listOf(
                "GermanConjugationsToHaveTeaching" to ReferenceFormat.FORMAT6, "GermanConjugationsToBeTeaching" to ReferenceFormat.FORMAT6,
                "GermanConjugationsToDoTeaching" to ReferenceFormat.FORMAT6, "GermanConjugationsToGetTeaching" to ReferenceFormat.FORMAT6,
                "GermanPrepositionsTeaching" to ReferenceFormat.FORMAT6,
                "GermanA1Adjectives" to ReferenceFormat.VOCAB, "GermanA2Adjectives" to ReferenceFormat.VOCAB,
                "GermanB1Adjectives" to ReferenceFormat.VOCAB, "GermanB2Adjectives" to ReferenceFormat.VOCAB,
                "GermanReferencePronouns" to ReferenceFormat.FORMAT6,
                "GermanPronouns2Nominative2" to ReferenceFormat.FORMAT8, "GermanPronouns2Accusative2" to ReferenceFormat.FORMAT8,
                "GermanPronouns2Dative2" to ReferenceFormat.FORMAT8, "GermanPronouns2Patterns2" to ReferenceFormat.FORMAT8,
                "GermanPronouns2BothCases2" to ReferenceFormat.FORMAT8, "GermanPronouns2LearningVerbs2" to ReferenceFormat.FORMAT8,
                "GermanPronouns3Ich" to ReferenceFormat.FORMAT8, "GermanPronouns3Du" to ReferenceFormat.FORMAT8,
                "GermanPronouns3Er" to ReferenceFormat.FORMAT8, "GermanPronouns3SieSingular" to ReferenceFormat.FORMAT8,
                "GermanPronouns3Es" to ReferenceFormat.FORMAT8, "GermanPronouns3Wir" to ReferenceFormat.FORMAT8,
                "GermanPronouns3Ihr" to ReferenceFormat.FORMAT8, "GermanPronouns3SiePlural" to ReferenceFormat.FORMAT8,
                "GermanPronouns3SieFormal" to ReferenceFormat.FORMAT8,
                "GermanPodcastFootballTripStuttgart" to ReferenceFormat.FORMAT9, "GermanPodcastSkiWeekendStubaital" to ReferenceFormat.FORMAT9,
                "GermanSoundsTheSame" to ReferenceFormat.FORMAT1,
                "GermanKennenWissen" to ReferenceFormat.FORMAT2, "GermanFragenBitten" to ReferenceFormat.FORMAT2,
                "GermanBringenHolen" to ReferenceFormat.FORMAT2, "GermanHoerenZuhoeren" to ReferenceFormat.FORMAT2,
            ),
            "English" to listOf(
                "EnglishConjugationsToHaveTeaching" to ReferenceFormat.FORMAT6, "EnglishConjugationsToBeTeaching" to ReferenceFormat.FORMAT6,
                "EnglishConjugationsToDoTeaching" to ReferenceFormat.FORMAT6, "EnglishConjugationsToGetTeaching" to ReferenceFormat.FORMAT6,
                "EnglishPrepositionsTeaching" to ReferenceFormat.FORMAT6,
                "EnglishA1Adjectives" to ReferenceFormat.VOCAB, "EnglishA2Adjectives" to ReferenceFormat.VOCAB,
                "EnglishB1Adjectives" to ReferenceFormat.VOCAB, "EnglishB2Adjectives" to ReferenceFormat.VOCAB,
                "EnglishReferencePronouns" to ReferenceFormat.FORMAT6,
                "EnglishPodcastFootballTripManchester" to ReferenceFormat.FORMAT9,
                "EnglishDefinitionsFormat1" to ReferenceFormat.FORMAT1,
                "EnglishGoodVsWell" to ReferenceFormat.FORMAT2, "EnglishSayVsTell" to ReferenceFormat.FORMAT2,
                "EnglishSpeakVsTalk" to ReferenceFormat.FORMAT2, "EnglishHearVsListen" to ReferenceFormat.FORMAT2,
                "EnglishBorrowVsLend" to ReferenceFormat.FORMAT2, "EnglishBringVsTake" to ReferenceFormat.FORMAT2,
                "EnglishLookVsSee" to ReferenceFormat.FORMAT2,
            ),
        )

        /**
         * The language prefix used by [referenceSheets] ("German" / "English"), from the build flavour. The current
         * sheet name can't be used: on Android it is the resource name ("vocab_data_a1"), not "GermanA1Vocab".
         */
        @Suppress("UNUSED_PARAMETER")
        fun languagePrefix(currentSheet: String): String? = when (com.goodstadt.john.language.exams.BuildConfig.FLAVOR) {
            "de" -> "German"
            "en" -> "English"
            else -> null
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsFile get() = File(context.filesDir, "offline_audio_packs.json")

    private val _records = MutableStateFlow<Map<String, OfflinePackRecord>>(emptyMap())
    val records = _records.asStateFlow()
    private val _runningPackId = MutableStateFlow<String?>(null)
    val runningPackId = _runningPackId.asStateFlow()
    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()
    private val _pauseReason = MutableStateFlow("")
    val pauseReason = _pauseReason.asStateFlow()
    private val _done = MutableStateFlow(0)
    val done = _done.asStateFlow()
    private val _total = MutableStateFlow(0)
    val total = _total.asStateFlow()
    private val _connection = MutableStateFlow(OfflineConnection.NONE)
    val connection = _connection.asStateFlow()

    private var job: Job? = null
    private var allowCellular = false
    /** The pack being downloaded, kept while paused so it can resume. */
    private class Running(
        val id: String, val sheetName: String, val level: String, val voice: String,
        val items: suspend () -> List<OfflineAudioItem>
    )
    private var current: Running? = null

    private fun log(message: String) = Timber.tag(TAG).i(message)

    init {
        loadRecords()
        watchNetwork()
    }

    // MARK: Sentences

    /** The text each vocab row plays: the word's first sentence. */
    suspend fun sentences(sheetName: String): List<String> {
        val file = contentRepository.getFormat0Data(sheetName).getOrNull() ?: return emptyList()
        return file.categories.flatMap { it.words }
            .mapNotNull { it.sentences.firstOrNull()?.sentence?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct()
    }

    // MARK: Reference pages

    /**
     * Every sentence the Reference pages play when tapped, in the voice a tap uses: [voice], except podcast lines,
     * which always play in their speaker's own voice.
     */
    suspend fun referenceItems(language: String, voice: String): List<OfflineAudioItem> {
        val out = LinkedHashSet<OfflineAudioItem>()
        fun add(text: String, v: String) {
            val t = text.trim()
            if (t.isNotEmpty()) out += OfflineAudioItem(t, v)
        }
        for ((doc, format) in referenceSheets[language].orEmpty()) {
            when (format) {
                // Adjectives: all sentences of each word are shown and tappable.
                ReferenceFormat.VOCAB -> contentRepository.getFormat0Data(doc).getOrNull()?.categories.orEmpty()
                    .forEach { c -> c.words.forEach { w -> w.sentences.forEach { add(it.sentence, voice) } } }
                ReferenceFormat.FORMAT1 -> contentRepository.getFormat1Data(doc).getOrNull()?.data.orEmpty()
                    .forEach { g -> g.wordsAndSentences.forEach { add(it.sentence, voice) } }
                ReferenceFormat.FORMAT2 -> contentRepository.getFormat2Data(doc).getOrNull()?.data.orEmpty()
                    .forEach { g -> g.wordsAndSentences.forEach { w -> w.sentences.forEach { add(it.sentence, voice) } } }
                ReferenceFormat.FORMAT6 -> contentRepository.getFormat6Data(doc).getOrNull()?.categories.orEmpty()
                    .forEach { c -> c.patterns.forEach { p -> p.sections.forEach { sec -> sec.sentences.forEach { add(it.text, voice) } } } }
                ReferenceFormat.FORMAT8 -> contentRepository.getFormat8Data(doc).getOrNull()?.sections.orEmpty()
                    .forEach { sec -> sec.entries.forEach { e -> e.examples.forEach { add(it.german, voice) } } }
                ReferenceFormat.FORMAT9 -> contentRepository.getFormat9Data(doc).getOrNull()?.let { file ->
                    val voices = file.speakers.associate { it.id to it.voiceId }
                    file.scenes.forEach { scene ->
                        scene.lines.forEach { line ->
                            add(file.spokenText(line), voices[line.speakerId]?.takeIf { it.isNotBlank() } ?: voice)
                        }
                    }
                }
            }
        }
        return out.toList()
    }

    private fun localName(text: String, voice: String) = FirebaseAudioService.generateUnifiedFilename(text, voice)
    private fun isOnDevice(name: String) = File(context.filesDir, name).exists()

    fun onDeviceCount(sentences: List<String>, voice: String) = sentences.count { isOnDevice(localName(it, voice)) }
    fun onDeviceCount(items: List<OfflineAudioItem>) = items.count { isOnDevice(localName(it.text, it.voice)) }

    // MARK: Start / pause / remove

    fun start(sheetName: String, level: String, voice: String, allowCellular: Boolean) =
        startPack(Running(packId(sheetName, voice), sheetName, level, voice) {
            sentences(sheetName).map { OfflineAudioItem(it, voice) }
        }, allowCellular)

    /** Download the Reference pages pack for [language] ("German" / "English"). */
    fun startReference(language: String, voice: String, allowCellular: Boolean) =
        startPack(Running(referencePackId(language, voice), "${language}Reference", "", voice) {
            referenceItems(language, voice)
        }, allowCellular)

    private fun startPack(pack: Running, allowCellular: Boolean) {
        if (job?.isActive == true || _runningPackId.value != null) {
            log("start ${pack.id} ignored - ${_runningPackId.value} is already running")
            return
        }
        log("start ${pack.id} sheet=${pack.sheetName} level=${pack.level} voice=${pack.voice} connection=${_connection.value} allowCellular=$allowCellular")
        this.allowCellular = allowCellular
        current = pack
        _isPaused.value = false
        _pauseReason.value = ""
        launchRun()
    }

    fun pause(reason: String = "Paused") {
        if (_runningPackId.value == null || _isPaused.value) return
        log("pause ${_runningPackId.value} - $reason (${_done.value} of ${_total.value} done)")
        _isPaused.value = true
        _pauseReason.value = reason
        job?.cancel()
    }

    fun resume() {
        if (!_isPaused.value || current == null) return
        log("resume ${current?.id} (was: ${_pauseReason.value})")
        scope.launch {
            job?.join()
            _isPaused.value = false
            _pauseReason.value = ""
            launchRun()
        }
    }

    fun resumeAllowingCellular() {
        allowCellular = true
        resume()
    }

    /** Delete the files this pack added (unless another pack also uses them) and forget the pack. */
    fun remove(packId: String) {
        val record = _records.value[packId] ?: return
        val others = _records.value.values.filter { it.id != packId }
            .flatMap { it.added + it.alreadyHad }.toSet()
        var deleted = 0
        record.added.filter { it !in others }.forEach { if (File(context.filesDir, it).delete()) deleted++ }
        _records.value = _records.value - packId
        saveRecords()
        log("removed $packId - deleted $deleted files (${record.added.size} added by the pack, ${record.alreadyHad.size} kept as already heard)")
    }

    private fun launchRun() {
        val pack = current ?: return
        _runningPackId.value = pack.id
        job = scope.launch { run(pack.id, pack.sheetName, pack.level, pack.voice, pack.items) }
    }

    private suspend fun run(id: String, sheetName: String, level: String, voice: String, items: suspend () -> List<OfflineAudioItem>) {
        val existing = _records.value[id]
        val added = existing?.added?.toMutableSet() ?: mutableSetOf()
        val alreadyHad = existing?.alreadyHad?.toMutableSet() ?: mutableSetOf()
        var unavailable = 0
        var total = existing?.total ?: 0
        var missingLogged = 0
        try {
            val sentences = items()
            total = sentences.size
            log("$id: $total sentences to cover" + if (total == 0) " - NONE: the sheet did not load (check the vocab/reference sheet)" else "")
            val toFetch = sentences.filter { item ->
                val name = localName(item.text, item.voice)
                // A tapped file cached under the old Android name counts as already on the device.
                FirebaseAudioService.adoptLegacyLocalFile(item.text, item.voice, File(context.filesDir, name))
                if (isOnDevice(name)) {
                    if (name !in added) alreadyHad += name
                    false
                } else true
            }
            _total.value = total
            _done.value = total - toFetch.size
            log("$id: ${total - toFetch.size} already on device, ${toFetch.size} to fetch from the cloud")
            saveRecord(id, sheetName, level, voice, total, added, alreadyHad, unavailable)

            // Started while offline (the network callback may not have reported it yet): wait instead of hanging.
            if (toFetch.isNotEmpty() && !connectionAllowed(_connection.value)) {
                pause(WAITING_FOR_CONNECTION)
                return
            }

            var sinceSave = 0
            for (batch in toFetch.chunked(PARALLEL_DOWNLOADS)) {
                val results = coroutineScope {
                    ensureActive()
                    batch.map { item -> async { item to fetch(item.text, item.voice) } }.awaitAll()
                }
                // Every file in the batch failed for a reason other than "not in the cloud": treat it as no
                // connection, so pause (Resume retries this batch) rather than mark the files unavailable.
                if (results.all { it.second == FirebaseAudioService.CloudFetch.FAILED }) {
                    log("$id: a whole batch of ${batch.size} failed (no connection or timeout) - pausing")
                    pause(WAITING_FOR_CONNECTION)
                    break
                }
                for ((item, r) in results) {
                    if (r == FirebaseAudioService.CloudFetch.OK) {
                        added += localName(item.text, item.voice)
                    } else {
                        unavailable++
                        // The file name to look for in Cloud Storage (audio_cache/), plus the sentence.
                        if (missingLogged++ < MAX_MISSING_LOGGED) log("$id: $r ${localName(item.text, item.voice)} \"${item.text.take(50)}\"")
                    }
                }
                _done.value += batch.size
                sinceSave += batch.size
                if (sinceSave >= 50) {
                    log("$id: progress ${_done.value} of $total - added ${added.size}, not available $unavailable")
                    saveRecord(id, sheetName, level, voice, total, added, alreadyHad, unavailable)
                    sinceSave = 0
                }
            }
            if (!_isPaused.value) {
                if (missingLogged > MAX_MISSING_LOGGED) log("$id: … ${missingLogged - MAX_MISSING_LOGGED} more not available (not logged)")
                log("$id: FINISHED - $total sentences: added ${added.size}, already had ${alreadyHad.size}, not available $unavailable" +
                    if (unavailable == 0 && added.size + alreadyHad.size >= total) " -> Ready offline" else "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "$id: download stopped by an error")
            throw e
        } finally {
            withContext(NonCancellable) {
                saveRecord(id, sheetName, level, voice, total, added, alreadyHad, unavailable)
                if (!_isPaused.value) {
                    _runningPackId.value = null
                    current = null
                }
            }
        }
    }

    /** Cloud unified (iOS-matching) name first, then the legacy Android name; saved under the unified name. */
    private suspend fun fetch(text: String, voice: String): FirebaseAudioService.CloudFetch =
        FirebaseAudioService.downloadAudioForOffline(text, voice, File(context.filesDir, localName(text, voice)), DOWNLOAD_TIMEOUT_MS)

    // MARK: Network

    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        _connection.value = connectionOf(cm.getNetworkCapabilities(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = networkChanged(connectionOf(caps))
            override fun onLost(network: Network) = networkChanged(OfflineConnection.NONE)
        })
    }

    private fun connectionOf(caps: NetworkCapabilities?): OfflineConnection = when {
        caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> OfflineConnection.NONE
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> OfflineConnection.WIFI
        else -> OfflineConnection.CELLULAR
    }

    private fun connectionAllowed(c: OfflineConnection) =
        c == OfflineConnection.WIFI || (c == OfflineConnection.CELLULAR && allowCellular)

    private fun networkChanged(c: OfflineConnection) {
        if (c != _connection.value) log("network ${_connection.value} -> $c" + (_runningPackId.value?.let { " (running $it)" } ?: ""))
        _connection.value = c
        if (_runningPackId.value == null) return
        val ok = connectionAllowed(c)
        if (!_isPaused.value && !ok) {
            pause(if (c == OfflineConnection.NONE) WAITING_FOR_CONNECTION else WAITING_FOR_WIFI)
        } else if (_isPaused.value && ok && _pauseReason.value in listOf(WAITING_FOR_WIFI, WAITING_FOR_CONNECTION)) {
            resume()
        }
    }

    // MARK: Records

    private fun saveRecord(
        id: String, sheetName: String, level: String, voice: String, total: Int,
        added: Set<String>, alreadyHad: Set<String>, unavailable: Int
    ) {
        _records.value = _records.value + (id to OfflinePackRecord(
            id, sheetName, level, voice, total, added.toList(), alreadyHad.toList(), unavailable, System.currentTimeMillis()
        ))
        saveRecords()
    }

    private fun loadRecords() {
        try {
            if (!recordsFile.exists()) return
            val list = json.decodeFromString<List<OfflinePackRecord>>(recordsFile.readText())
            _records.value = list.associateBy { it.id }
        } catch (e: Exception) {
            Timber.e(e, "Offline: could not read pack records")
        }
    }

    @Synchronized
    private fun saveRecords() {
        try {
            val tmp = File(recordsFile.path + ".tmp")
            tmp.writeText(json.encodeToString(_records.value.values.toList()))
            tmp.renameTo(recordsFile)
        } catch (e: Exception) {
            Timber.e(e, "Offline: could not save pack records")
        }
    }
}
