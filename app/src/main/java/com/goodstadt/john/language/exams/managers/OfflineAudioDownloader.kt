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

/**
 * Downloads the vocab tab audio (each word's first sentence, the text a vocab row plays) for one sheet in one
 * voice from cloud storage into filesDir, under the normal local name, so taps play it with no internet.
 *
 * Cloud only - it never calls Google TTS. For each sentence it tries the iOS-named file first (iOS fills the
 * cloud for the default voices), then the Android-named one. Same design as the iOS OfflineAudioDownloader.
 * Runs in its own scope, so the user can keep studying; pauses on network loss / mobile data.
 */
@Singleton
class OfflineAudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
) {
    companion object {
        private const val PARALLEL_DOWNLOADS = 5
        const val ESTIMATED_BYTES_PER_FILE = 25_000
        const val WAITING_FOR_WIFI = "Waiting for Wi-Fi"
        const val WAITING_FOR_CONNECTION = "Waiting for a connection"

        fun packId(sheetName: String, voice: String) = "vocab-$sheetName-$voice"
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
    /** (sheetName, level, voice) of the pack being downloaded, kept while paused so it can resume. */
    private var current: Triple<String, String, String>? = null

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

    private fun localName(text: String, voice: String) = FirebaseAudioService.generateUnifiedFilename(text, voice)
    private fun isOnDevice(name: String) = File(context.filesDir, name).exists()

    fun onDeviceCount(sentences: List<String>, voice: String) = sentences.count { isOnDevice(localName(it, voice)) }

    // MARK: Start / pause / remove

    fun start(sheetName: String, level: String, voice: String, allowCellular: Boolean) {
        if (job?.isActive == true || _runningPackId.value != null) return
        this.allowCellular = allowCellular
        current = Triple(sheetName, level, voice)
        _isPaused.value = false
        _pauseReason.value = ""
        launchRun()
    }

    fun pause(reason: String = "Paused") {
        if (_runningPackId.value == null || _isPaused.value) return
        _isPaused.value = true
        _pauseReason.value = reason
        job?.cancel()
    }

    fun resume() {
        if (!_isPaused.value || current == null) return
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
        Timber.d("Offline: removed $packId - deleted $deleted files")
    }

    private fun launchRun() {
        val (sheetName, level, voice) = current ?: return
        val id = packId(sheetName, voice)
        _runningPackId.value = id
        job = scope.launch { run(id, sheetName, level, voice) }
    }

    private suspend fun run(id: String, sheetName: String, level: String, voice: String) {
        val existing = _records.value[id]
        val added = existing?.added?.toMutableSet() ?: mutableSetOf()
        val alreadyHad = existing?.alreadyHad?.toMutableSet() ?: mutableSetOf()
        var unavailable = 0
        var total = existing?.total ?: 0
        try {
            val sentences = sentences(sheetName)
            total = sentences.size
            val toFetch = sentences.filter { text ->
                val name = localName(text, voice)
                if (isOnDevice(name)) {
                    if (name !in added) alreadyHad += name
                    false
                } else true
            }
            _total.value = total
            _done.value = total - toFetch.size
            saveRecord(id, sheetName, level, voice, total, added, alreadyHad, unavailable)

            var sinceSave = 0
            for (batch in toFetch.chunked(PARALLEL_DOWNLOADS)) {
                coroutineScope {
                    ensureActive()
                    val results = batch.map { text -> async { text to fetch(text, voice) } }.awaitAll()
                    for ((text, ok) in results) {
                        if (ok) added += localName(text, voice) else unavailable++
                    }
                }
                _done.value += batch.size
                sinceSave += batch.size
                if (sinceSave >= 50) {
                    saveRecord(id, sheetName, level, voice, total, added, alreadyHad, unavailable)
                    sinceSave = 0
                }
            }
            Timber.d("Offline: $id finished - $total sentences, added ${added.size}, had ${alreadyHad.size}, unavailable $unavailable")
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

    /** Cloud iOS name first, then the Android name; saved under the local (Android) name. */
    private suspend fun fetch(text: String, voice: String): Boolean {
        val dest = File(context.filesDir, localName(text, voice))
        val part = File(context.filesDir, dest.name + ".part")
        for (cloudName in listOf(FirebaseAudioService.generateIOSUnifiedFilename(text, voice), dest.name)) {
            if (FirebaseAudioService.downloadAudio(cloudName, part) && part.length() > 0 && part.renameTo(dest)) return true
        }
        part.delete()
        return false
    }

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

    private fun networkChanged(c: OfflineConnection) {
        _connection.value = c
        if (_runningPackId.value == null) return
        val ok = c == OfflineConnection.WIFI || (c == OfflineConnection.CELLULAR && allowCellular)
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
