package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.OfflineAudioDownloader
import com.goodstadt.john.language.exams.managers.OfflineConnection
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app works offline for any sentence already on the device. Instead of one global "no internet" warning,
 * a tap whose MP3 isn't on the device while offline shows a slim bar on that screen (tabs 1-3 or the Reference
 * main tab); cached sentences keep playing. While offline the bar also shows if the screen's "Download for
 * Offline" pack isn't complete, and stays hidden when it is (everything there should play). It goes away as
 * soon as the connection returns. Twin of iOS ConnectionNotice / NoConnectionBar.
 */
@Singleton
class ConnectionNotice @Inject constructor(
    private val downloader: OfflineAudioDownloader,
) {
    /** True while there's no network (from the downloader's network watcher). */
    val isOffline: Boolean get() = downloader.connection.value == OfflineConnection.NONE

    private val _missed = MutableStateFlow(false)	// a tap couldn't play while offline (not on the device)
    val missed = _missed.asStateFlow()
    private val _dismissed = MutableStateFlow(false)
    val dismissed = _dismissed.asStateFlow()

    init {
        downloader.connection
            .onEach { if (it != OfflineConnection.NONE) { _missed.value = false; _dismissed.value = false } }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    /** A sentence wasn't on the device and there's no connection: show the bar (again, even if closed). */
    fun noteMiss() { _missed.value = true; _dismissed.value = false }
    fun dismiss() { _dismissed.value = true }
}

/** Lets composables reach the singletons without a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConnectionNoticeEntryPoint {
    fun connectionNotice(): ConnectionNotice
    fun offlineAudioDownloader(): OfflineAudioDownloader
    fun userPreferencesRepository(): UserPreferencesRepository
}

/** Add one per screen next to SessionFeedbackBar; it only appears while offline (see [ConnectionNotice]). */
@Composable
fun NoConnectionBar(area: SessionFeedbackManager.Area, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, ConnectionNoticeEntryPoint::class.java)
    }
    val notice = remember(entry) { entry.connectionNotice() }
    val downloader = remember(entry) { entry.offlineAudioDownloader() }
    val prefs = remember(entry) { entry.userPreferencesRepository() }

    val connection by downloader.connection.collectAsState()
    val missed by notice.missed.collectAsState()
    val dismissed by notice.dismissed.collectAsState()
    val records by downloader.records.collectAsState()
    val runningPackId by downloader.runningPackId.collectAsState()
    val sheet by prefs.selectedFileNameFlow.collectAsState(initial = "")
    val voice by prefs.selectedVoiceNameFlow.collectAsState(initial = "")

    // This screen's offline pack (current level's vocab, or the language's Reference pages, in the current
    // voice) has every file on the device.
    val packId = when (area) {
        SessionFeedbackManager.Area.TABS -> OfflineAudioDownloader.packId(sheet, voice)
        SessionFeedbackManager.Area.REFERENCE ->
            OfflineAudioDownloader.languagePrefix(sheet)?.let { OfflineAudioDownloader.referencePackId(it, voice) }
    }
    val record = packId?.let { records[it] }
    val packComplete = record != null && runningPackId != packId && record.total > 0 &&
        record.added.size + record.alreadyHad.size >= record.total

    AnimatedVisibility(
        visible = connection == OfflineConnection.NONE && !dismissed && (missed || !packComplete),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (missed) "No connection. That sentence isn't on this device yet." else "No connection. Downloaded sentences still play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { notice.dismiss() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
