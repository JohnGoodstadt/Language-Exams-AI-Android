package com.goodstadt.john.language.exams.packages.Settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.managers.OfflineAudioDownloader
import com.goodstadt.john.language.exams.managers.OfflineConnection
import com.goodstadt.john.language.exams.managers.OfflinePackRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the offline page: a vocab sheet in the current voice. */
data class OfflineLevelRow(
    val sheetName: String,
    val level: String,
    val sentences: Int,
    val onDevice: Int,
    /** The level the user is studying now: shown as one row with the Reference pages. */
    val isCurrent: Boolean = false,
)

/** The Reference pages pack for the current language, in the current voice. */
data class OfflineReferenceRow(
    val language: String,
    val sentences: Int,
    val onDevice: Int,
)

/**
 * Backs the "Download for Offline" sheet: the current vocab level, plus any other level that still holds a
 * download (so it can be removed). Downloads run in [OfflineAudioDownloader] and outlive this ViewModel.
 */
@HiltViewModel
class OfflineDownloadViewModel @Inject constructor(
    val downloader: OfflineAudioDownloader,
    private val prefs: UserPreferencesRepository,
    private val controlRepository: ControlRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<OfflineLevelRow>?>(null)
    val rows = _rows.asStateFlow()
    private val _voice = MutableStateFlow("")
    val voice = _voice.asStateFlow()
    /** The default voices (female, male) for the current language, without any " female"/" male" suffix. */
    private val _defaultVoices = MutableStateFlow<Pair<String, String>?>(null)
    val defaultVoices = _defaultVoices.asStateFlow()

    private val _reference = MutableStateFlow<OfflineReferenceRow?>(null)
    val reference = _reference.asStateFlow()

    private val sentenceCache = mutableMapOf<String, List<String>>()

    fun load() {
        viewModelScope.launch {
            val voice = prefs.selectedVoiceNameFlow.first()
            val sheet = prefs.selectedFileNameFlow.first()
            val level = prefs.selectedSkillLevelFlow.first()
            _voice.value = voice
            controlRepository.getActiveLanguageDetails().getOrNull()?.let {
                _defaultVoices.value = it.defaultFemaleVoice.substringBefore(' ') to it.defaultMaleVoice.substringBefore(' ')
            }
            val rows = mutableListOf<OfflineLevelRow>()
            sentencesFor(sheet).takeIf { it.isNotEmpty() }?.let {
                rows += OfflineLevelRow(sheet, level, it.size, downloader.onDeviceCount(it, voice), isCurrent = true)
            }
            downloader.records.value.values
                .filter { it.voice == voice && it.sheetName != sheet && it.id.startsWith("vocab-") }
                .sortedBy { it.level }
                .forEach { r ->
                    val s = sentencesFor(r.sheetName)
                    rows += OfflineLevelRow(r.sheetName, r.level, s.size, downloader.onDeviceCount(s, voice))
                }
            _rows.value = rows

            val language = OfflineAudioDownloader.languagePrefix(sheet)
                ?.takeIf { OfflineAudioDownloader.referenceSheets.containsKey(it) }
            _reference.value = language?.let {
                val items = downloader.referenceItems(it, voice)
                timber.log.Timber.tag("OfflineDL").i("Reference pages ($it, $voice): ${items.size} sentences, ${downloader.onDeviceCount(items)} on device")
                OfflineReferenceRow(it, items.size, downloader.onDeviceCount(items))
            }
        }
    }

    /** The current level's vocab, then the Reference pages, as one download. */
    fun downloadAll(row: OfflineLevelRow, ref: OfflineReferenceRow?, allowCellular: Boolean) =
        downloader.startLevelAndReference(row.sheetName, row.level, ref?.language, _voice.value, allowCellular)

    /** Removes both the current level's vocab and the Reference pages. */
    fun removeAll(row: OfflineLevelRow, ref: OfflineReferenceRow?) {
        downloader.remove(OfflineAudioDownloader.packId(row.sheetName, _voice.value))
        ref?.let { downloader.remove(OfflineAudioDownloader.referencePackId(it.language, _voice.value)) }
        load()
    }

    private suspend fun sentencesFor(sheet: String) =
        sentenceCache[sheet] ?: downloader.sentences(sheet).also { sentenceCache[sheet] = it }

    fun download(row: OfflineLevelRow, allowCellular: Boolean) =
        downloader.start(row.sheetName, row.level, _voice.value, allowCellular)

    fun remove(row: OfflineLevelRow) {
        downloader.remove(OfflineAudioDownloader.packId(row.sheetName, _voice.value))
        load()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDownloadSheet(
    friendlyVoiceName: String,
    voices: List<VoiceOption>,
    onSwitchVoice: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: OfflineDownloadViewModel = hiltViewModel(),
) {
    val downloader = viewModel.downloader
    val rows by viewModel.rows.collectAsState()
    val voice by viewModel.voice.collectAsState()
    val defaults by viewModel.defaultVoices.collectAsState()
    val records by downloader.records.collectAsState()
    val runningPackId by downloader.runningPackId.collectAsState()
    val isPaused by downloader.isPaused.collectAsState()
    val pauseReason by downloader.pauseReason.collectAsState()
    val done by downloader.done.collectAsState()
    val total by downloader.total.collectAsState()
    val connection by downloader.connection.collectAsState()
    val reference by viewModel.reference.collectAsState()
    var cellularRow by remember { mutableStateOf<OfflineLevelRow?>(null) }
    var cellularAll by remember { mutableStateOf<OfflineLevelRow?>(null) }

    // Reload when the voice changes or a download starts / finishes, so counts and rows stay true.
    LaunchedEffect(voice, runningPackId) { viewModel.load() }
    LaunchedEffect(friendlyVoiceName) { viewModel.load() }

    // The language's default voice of the same gender, when the current voice isn't a default one.
    val defaultVoice = defaults?.let { (female, male) ->
        if (voice.isEmpty() || voice == female || voice == male) null
        else if (voices.find { it.id == voice }?.gender == Gender.MALE) male else female
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Download for Offline", style = MaterialTheme.typography.titleLarge)
                BetaBadge()
            }
            Spacer(Modifier.height(16.dp))
            LabeledRow("Voice", friendlyVoiceName)
            LabeledRow("Connection", when (connection) {
                OfflineConnection.WIFI -> "Wi-Fi"
                OfflineConnection.CELLULAR -> "Mobile data"
                OfflineConnection.NONE -> "Offline"
            })
            if (defaultVoice != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Some sentences may not be available offline in this voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onSwitchVoice(defaultVoice) }) {
                    Text("Switch to the default voice for offline", fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Keep the app open for a quicker download. You can keep studying while it downloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider()
            val current = rows
            val hasReference = OfflineAudioDownloader.languagePrefix("") != null
            // Counting the Reference sentences reads ~35 sheets, so wait for both before showing the row.
            if (current == null || (hasReference && reference == null)) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                current.firstOrNull { it.isCurrent }?.let { row ->
                    val ref = reference
                    val vocabId = OfflineAudioDownloader.packId(row.sheetName, voice)
                    val refId = ref?.let { OfflineAudioDownloader.referencePackId(it.language, voice) }
                    val packRecords = listOfNotNull(records[vocabId], refId?.let { records[it] })
                    val sentences = row.sentences + (ref?.sentences ?: 0)
                    val onDevice = row.onDevice + (ref?.onDevice ?: 0)
                    SectionHeader("${row.level} LEVEL")
                    PackRowView(
                        title = if (ref != null) "${row.level} Vocab and Reference pages" else "${row.level} Vocab",
                        notDownloadedText = "$sentences sentences · about ${sizeText(sentences - onDevice)}",
                        sentences = sentences,
                        onDevice = onDevice,
                        hasDownload = packRecords.isNotEmpty(),
                        unavailable = packRecords.sumOf { it.unavailable },
                        runningStep = when (runningPackId) {
                            vocabId -> "${row.level} Vocab"
                            refId -> "Reference pages"
                            else -> null
                        },
                        isRunning = runningPackId == vocabId || (refId != null && runningPackId == refId),
                        anyRunning = runningPackId != null,
                        isPaused = isPaused,
                        pauseReason = pauseReason,
                        done = done,
                        total = total,
                        canDownload = connection != OfflineConnection.NONE,
                        onDownload = {
                            when (connection) {
                                OfflineConnection.WIFI -> viewModel.downloadAll(row, ref, allowCellular = false)
                                OfflineConnection.CELLULAR -> cellularAll = row
                                OfflineConnection.NONE -> {}
                            }
                        },
                        onPause = { downloader.pause() },
                        onResume = {
                            if (connection == OfflineConnection.CELLULAR) downloader.resumeAllowingCellular() else downloader.resume()
                        },
                        onRemove = { viewModel.removeAll(row, ref) },
                    )
                }

                // Other levels that still hold a vocab download, so it can be removed.
                val others = current.filter { !it.isCurrent }
                if (others.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    SectionHeader("OTHER LEVELS")
                    others.forEach { row ->
                        val id = OfflineAudioDownloader.packId(row.sheetName, voice)
                        LevelRowView(
                            row = row,
                            record = records[id],
                            isRunning = runningPackId == id,
                            anyRunning = runningPackId != null,
                            isPaused = isPaused,
                            pauseReason = pauseReason,
                            done = done,
                            total = total,
                            canDownload = connection != OfflineConnection.NONE,
                            onDownload = {
                                when (connection) {
                                    OfflineConnection.WIFI -> viewModel.download(row, allowCellular = false)
                                    OfflineConnection.CELLULAR -> cellularRow = row
                                    OfflineConnection.NONE -> {}
                                }
                            },
                            onPause = { downloader.pause() },
                            onResume = {
                                if (connection == OfflineConnection.CELLULAR) downloader.resumeAllowingCellular() else downloader.resume()
                            },
                            onRemove = { viewModel.remove(row) },
                        )
                    }
                }
            }
        }
    }

    cellularAll?.let { row ->
        val ref = reference
        val remaining = row.sentences - row.onDevice + (ref?.let { it.sentences - it.onDevice } ?: 0)
        AlertDialog(
            onDismissRequest = { cellularAll = null },
            title = { Text("Download on mobile data?") },
            text = { Text("About ${sizeText(remaining)} will be downloaded.") },
            confirmButton = {
                TextButton(onClick = { viewModel.downloadAll(row, ref, allowCellular = true); cellularAll = null }) { Text("Download now") }
            },
            dismissButton = { TextButton(onClick = { cellularAll = null }) { Text("Wait for Wi-Fi") } }
        )
    }

    cellularRow?.let { row ->
        AlertDialog(
            onDismissRequest = { cellularRow = null },
            title = { Text("Download on mobile data?") },
            text = { Text("About ${sizeText(row.sentences - row.onDevice)} will be downloaded.") },
            confirmButton = {
                TextButton(onClick = { viewModel.download(row, allowCellular = true); cellularRow = null }) { Text("Download now") }
            },
            dismissButton = { TextButton(onClick = { cellularRow = null }) { Text("Wait for Wi-Fi") } }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LevelRowView(
    row: OfflineLevelRow,
    record: OfflinePackRecord?,
    isRunning: Boolean,
    anyRunning: Boolean,
    isPaused: Boolean,
    pauseReason: String,
    done: Int,
    total: Int,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) = PackRowView(
    title = "${row.level} Vocab",
    notDownloadedText = "${row.sentences} sentences · about ${sizeText(row.sentences - row.onDevice)}" +
            if (row.onDevice > 0) " · ${row.onDevice} already heard" else "",
    sentences = row.sentences, onDevice = row.onDevice,
    hasDownload = record != null, unavailable = record?.unavailable ?: 0, isRunning = isRunning, anyRunning = anyRunning,
    isPaused = isPaused, pauseReason = pauseReason, done = done, total = total, canDownload = canDownload,
    onDownload = onDownload, onPause = onPause, onResume = onResume, onRemove = onRemove,
)

/** One downloadable pack: Download / Pause / Resume, progress, status and Remove. */
@Composable
private fun PackRowView(
    title: String,
    notDownloadedText: String,
    sentences: Int,
    onDevice: Int,
    hasDownload: Boolean,
    unavailable: Int,
    isRunning: Boolean,
    anyRunning: Boolean,
    isPaused: Boolean,
    pauseReason: String,
    done: Int,
    total: Int,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    /** While running, which part is downloading (for a row made of several packs). */
    runningStep: String? = null,
) {
    val remaining = sentences - onDevice
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            when {
                isRunning -> OutlinedButton(onClick = if (isPaused) onResume else onPause) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
                remaining > 0 -> Button(onClick = onDownload, enabled = !anyRunning && canDownload) { Text("Download") }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (isRunning) {
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                (runningStep?.let { "$it · " } ?: "") +
                    if (isPaused) "$pauseReason · $done of $total" else "Downloading $done of $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            var text = when {
                remaining == 0 -> "Ready offline · $sentences sentences"
                hasDownload -> "$onDevice of $sentences on this device"
                else -> notDownloadedText
            }
            if (hasDownload && unavailable > 0) text += " · $unavailable not available"
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasDownload) {
                TextButton(onClick = onRemove) {
                    Text("Remove download", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun sizeText(files: Int): String {
    val bytes = files * OfflineAudioDownloader.ESTIMATED_BYTES_PER_FILE
    return if (bytes < 1_000_000) "${maxOf(bytes / 1000, 1)} KB" else "${bytes / 1_000_000} MB"
}

/**
 * A small filled pill marking a feature that's still being tested (e.g. "Beta" on Download for Offline).
 * Indigo, distinct from the accent orange and button blue. Same colour as iOS.
 */
@Composable
internal fun BetaBadge(text: String = "Beta") {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier
            .background(
                androidx.compose.ui.graphics.Color(0xFF5856D6),
                androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
