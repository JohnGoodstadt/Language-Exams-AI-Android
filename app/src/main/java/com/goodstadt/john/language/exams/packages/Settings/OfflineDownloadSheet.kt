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
                rows += OfflineLevelRow(sheet, level, it.size, downloader.onDeviceCount(it, voice))
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

    fun downloadReference(row: OfflineReferenceRow, allowCellular: Boolean) =
        downloader.startReference(row.language, _voice.value, allowCellular)

    fun removeReference(row: OfflineReferenceRow) {
        downloader.remove(OfflineAudioDownloader.referencePackId(row.language, _voice.value))
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
    var cellularReference by remember { mutableStateOf<OfflineReferenceRow?>(null) }

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
            Text(
                "VOCAB TABS 1-3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            val current = rows
            if (current == null) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                current.forEach { row ->
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

            if (OfflineAudioDownloader.languagePrefix("") != null) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    "REFERENCE PAGES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                // Counting the Reference sentences reads ~35 sheets, so show progress until it's done.
                if (reference == null) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            reference?.let { ref ->
                val id = OfflineAudioDownloader.referencePackId(ref.language, voice)
                PackRowView(
                    title = "Reference pages",
                    notDownloadedText = "Conjugations, prepositions, adjectives, pronouns, podcasts, pairs · " +
                            "${ref.sentences} sentences · about ${sizeText(ref.sentences - ref.onDevice)}",
                    sentences = ref.sentences,
                    onDevice = ref.onDevice,
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
                            OfflineConnection.WIFI -> viewModel.downloadReference(ref, allowCellular = false)
                            OfflineConnection.CELLULAR -> cellularReference = ref
                            OfflineConnection.NONE -> {}
                        }
                    },
                    onPause = { downloader.pause() },
                    onResume = {
                        if (connection == OfflineConnection.CELLULAR) downloader.resumeAllowingCellular() else downloader.resume()
                    },
                    onRemove = { viewModel.removeReference(ref) },
                )
            }
        }
    }

    cellularReference?.let { ref ->
        AlertDialog(
            onDismissRequest = { cellularReference = null },
            title = { Text("Download on mobile data?") },
            text = { Text("About ${sizeText(ref.sentences - ref.onDevice)} will be downloaded.") },
            confirmButton = {
                TextButton(onClick = { viewModel.downloadReference(ref, allowCellular = true); cellularReference = null }) { Text("Download now") }
            },
            dismissButton = { TextButton(onClick = { cellularReference = null }) { Text("Wait for Wi-Fi") } }
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
    sentences = row.sentences, onDevice = row.onDevice, record = record, isRunning = isRunning, anyRunning = anyRunning,
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
                if (isPaused) "$pauseReason · $done of $total" else "Downloading $done of $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            var text = when {
                remaining == 0 -> "Ready offline · $sentences sentences"
                record != null -> "$onDevice of $sentences on this device"
                else -> notDownloadedText
            }
            if (record != null && record.unavailable > 0) text += " · ${record.unavailable} not available"
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record != null) {
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
