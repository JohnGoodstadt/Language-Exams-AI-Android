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
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
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
    companion object {
        /** A tap's cloud download is given up after this (Firebase would otherwise retry for 10 minutes). */
        private const val DOWNLOAD_TIMEOUT_MS = 10_000L
        private const val RECHECK_MS = 5_000L
        private const val PROBE_URL = "https://firebasestorage.googleapis.com/"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A probe found no internet although Android reports a network (common on the emulator). */
    private val probedOffline = MutableStateFlow(false)
    private var recheckJob: Job? = null

    /** True while there's no network: Android says so, or the last probe failed. */
    val offline: StateFlow<Boolean> = combine(downloader.connection, probedOffline) { c, probed ->
        c == OfflineConnection.NONE || probed
    }.stateIn(scope, SharingStarted.Eagerly, downloader.connection.value == OfflineConnection.NONE)

    val isOffline: Boolean get() = offline.value

    private val _missed = MutableStateFlow(false)	// a tap couldn't play while offline (not on the device)
    val missed = _missed.asStateFlow()
    private val _dismissed = MutableStateFlow(false)
    val dismissed = _dismissed.asStateFlow()

    init {
        offline
            .onEach { if (!it) { _missed.value = false; _dismissed.value = false } }
            .launchIn(scope)
    }

    /** A sentence wasn't on the device and there's no connection: show the bar (again, even if closed). */
    fun noteMiss() { _missed.value = true; _dismissed.value = false }
    fun dismiss() { _dismissed.value = true }

    /**
     * Runs a tap's cloud step alongside a quick probe. Offline the probe fails within milliseconds and this
     * returns null at once (instead of the spinner staying up while Firebase retries). Online the probe costs
     * no extra time; the block is still capped at [DOWNLOAD_TIMEOUT_MS] (false = treat as not in the cloud).
     */
    suspend fun unlessOffline(block: suspend () -> Boolean): Boolean? = coroutineScope {
        val work = async { withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { block() } ?: false }
        val probe = async { probeIsOffline() }
        val result = select<Boolean?> {
            work.onAwait { probe.cancel(); it }
            probe.onAwait { offline -> if (offline) { work.cancel(); null } else work.await() }
        }
        setProbedOffline(result == null)
        result
    }

    /** True when a tiny request fails with a "no connection" error (any HTTP reply, even an error, is online). */
    private suspend fun probeIsOffline(): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL(PROBE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 4_000
            readTimeout = 4_000
            useCaches = false
        }
        try {
            connection.responseCode
            false
        } catch (e: UnknownHostException) { true
        } catch (e: ConnectException) { true
        } catch (e: NoRouteToHostException) { true
        } catch (e: SocketTimeoutException) { true
        } catch (e: IOException) { false
        } finally {
            connection.disconnect()
        }
    }

    /** While offline, re-probe every few seconds so the bar clears when the connection returns. */
    private fun setProbedOffline(value: Boolean) {
        probedOffline.value = value
        if (!value) { recheckJob?.cancel(); recheckJob = null; return }
        if (recheckJob?.isActive == true) return
        recheckJob = scope.launch {
            while (isActive) {
                delay(RECHECK_MS)
                if (!probeIsOffline()) { probedOffline.value = false; break }
            }
        }
    }
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

    val offline by notice.offline.collectAsState()
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

    // Soft red, distinct from the accent orange (dark) and button blue (light). Same values as iOS.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tint = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val band = if (dark) Color(0xFF4A1F1F) else Color(0xFFFDECEA)

    AnimatedVisibility(
        visible = offline && !dismissed && (missed || !packComplete),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(band)
                .heightIn(min = 40.dp)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (missed) "No connection. Connect to the internet to play new sentences." else "No connection. Downloaded sentences still play.",
                style = MaterialTheme.typography.bodySmall,
                color = tint,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { notice.dismiss() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
