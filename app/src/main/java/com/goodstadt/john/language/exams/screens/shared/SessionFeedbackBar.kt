package com.goodstadt.john.language.exams.screens.shared

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "How is ExamReady doing this session?" - a one-row Bad / Fine / Good (and a small ×) prompt, shown just
 * below the top menu of the screen the user has been busy on: tabs 1-3 (CategoryTabScreen) or the Reference
 * main tab (ReferenceTabContainerScreen). Twin of iOS SessionFeedback / SessionFeedbackBar.
 *
 * Activity is counted per area from the existing tap counters (TTSStatsRepository). Rules, all local:
 *   - at most once per app session and once per day, and not in the first 2 days after first use;
 *   - only after [TAPS_NEEDED] taps in that area this session;
 *   - after an answer wait 7 days; after × wait 3 days (30 days after 3 ×'s in a row).
 * Every answer (and ×) is written to Firestore `session_feedback` (FirestoreRepository.fsUploadSessionFeedback).
 */
@Singleton
class SessionFeedbackManager @Inject constructor(
    @ApplicationContext context: Context,
    private val firestoreRepository: FirestoreRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    enum class Area(val id: String) { TABS("tabs"), REFERENCE("reference") }
    enum class Rating(val id: String) { BAD("bad"), FINE("fine"), GOOD("good"), DISMISSED("dismissed") }

    /** The area the bar is showing on (null = hidden). */
    var showingIn by mutableStateOf<Area?>(null)
        private set
    /** True for a moment after an answer, while "Thanks!" shows. */
    var isThanking by mutableStateOf(false)
        private set

    private var tabsTaps by mutableIntStateOf(0)
    private var referenceTaps by mutableIntStateOf(0)
    private var shownThisSession = false

    private val prefs = context.getSharedPreferences("session_feedback", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (!prefs.contains(KEY_FIRST_SEEN)) prefs.edit().putLong(KEY_FIRST_SEEN, System.currentTimeMillis()).apply()
    }

    /** One user interaction in an area (called from the TTSStatsRepository tap counters, any thread). */
    fun recordTap(area: Area) {
        scope.launch {
            val taps = when (area) {
                Area.TABS -> ++tabsTaps
                Area.REFERENCE -> ++referenceTaps
            }
            if (showingIn == null && taps >= TAPS_NEEDED && mayShow()) show(area)
        }
    }

    /** The user answered (or tapped ×): upload it, show "Thanks!" briefly, then hide. */
    fun answer(rating: Rating) {
        val area = showingIn ?: return
        val now = System.currentTimeMillis()
        if (rating == Rating.DISMISSED) {
            prefs.edit()
                .putLong(KEY_LAST_DISMISSED, now)
                .putInt(KEY_DISMISSES_IN_A_ROW, prefs.getInt(KEY_DISMISSES_IN_A_ROW, 0) + 1)
                .apply()
        } else {
            prefs.edit().putLong(KEY_LAST_ANSWERED, now).putInt(KEY_DISMISSES_IN_A_ROW, 0).apply()
        }

        val tabs = tabsTaps
        val reference = referenceTaps
        scope.launch {
            firestoreRepository.fsUploadSessionFeedback(
                rating = rating.id,
                area = area.id,
                exam = userPreferencesRepository.selectedExamNameFlow.first(),
                level = userPreferencesRepository.selectedSkillLevelFlow.first(),
                tabsTaps = tabs,
                referenceTaps = reference,
                daysSinceFirstUse = daysSince(KEY_FIRST_SEEN) ?: 0
            )
        }

        if (rating == Rating.DISMISSED) {
            showingIn = null
        } else {
            isThanking = true
            scope.launch {
                delay(1_500)
                showingIn = null
                isThanking = false
            }
        }
    }

    // --- Rules ---

    private fun mayShow(): Boolean {
        if (shownThisSession) return false
        daysSince(KEY_FIRST_SEEN)?.let { if (it < DAYS_BEFORE_FIRST_SHOW) return false }
        if (prefs.contains(KEY_LAST_SHOWN) && isToday(prefs.getLong(KEY_LAST_SHOWN, 0))) return false
        daysSince(KEY_LAST_ANSWERED)?.let { if (it < DAYS_AFTER_ANSWER) return false }
        daysSince(KEY_LAST_DISMISSED)?.let {
            val wait = if (prefs.getInt(KEY_DISMISSES_IN_A_ROW, 0) >= DISMISSES_BEFORE_LONG_WAIT) DAYS_AFTER_MANY_DISMISSES else DAYS_AFTER_DISMISS
            if (it < wait) return false
        }
        return true
    }

    private fun show(area: Area) {
        shownThisSession = true
        prefs.edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply()
        showingIn = area
    }

    private fun daysSince(key: String): Int? {
        if (!prefs.contains(key)) return null
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - prefs.getLong(key, 0)).toInt()
    }

    private fun isToday(millis: Long): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return day.format(Date(millis)) == day.format(Date())
    }

    // --- DEBUG (Settings > Debug rows) ---

    /** Show the bar now on [area], ignoring the rules. */
    fun debugShow(area: Area) {
        isThanking = false
        showingIn = area
    }

    /** Clear every stored date/count and this session's taps. */
    fun debugReset() {
        prefs.edit().clear().putLong(KEY_FIRST_SEEN, System.currentTimeMillis()).apply()
        tabsTaps = 0
        referenceTaps = 0
        shownThisSession = false
        showingIn = null
    }

    /** One-line read-out for the Settings debug row. */
    val debugStatus: String
        get() {
            val f = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
            fun date(key: String) = if (prefs.contains(key)) f.format(Date(prefs.getLong(key, 0))) else "-"
            return "Shown ${date(KEY_LAST_SHOWN)} · answered ${date(KEY_LAST_ANSWERED)} · × ${prefs.getInt(KEY_DISMISSES_IN_A_ROW, 0)} · taps tabs $tabsTaps / ref $referenceTaps"
        }

    private companion object {
        const val TAPS_NEEDED = 15
        const val DAYS_BEFORE_FIRST_SHOW = 2
        const val DAYS_AFTER_ANSWER = 7
        const val DAYS_AFTER_DISMISS = 3
        const val DAYS_AFTER_MANY_DISMISSES = 30
        const val DISMISSES_BEFORE_LONG_WAIT = 3

        const val KEY_FIRST_SEEN = "firstSeen"
        const val KEY_LAST_SHOWN = "lastShown"
        const val KEY_LAST_ANSWERED = "lastAnswered"
        const val KEY_LAST_DISMISSED = "lastDismissed"
        const val KEY_DISMISSES_IN_A_ROW = "dismissesInARow"
    }
}

/** Lets composables reach the singleton [SessionFeedbackManager] without a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionFeedbackEntryPoint {
    fun sessionFeedbackManager(): SessionFeedbackManager
}

@Composable
fun rememberSessionFeedbackManager(): SessionFeedbackManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SessionFeedbackEntryPoint::class.java)
            .sessionFeedbackManager()
    }
}

/** The bar itself. Add one per screen, just below the top menu; it only appears on its own [area]. */
@Composable
fun SessionFeedbackBar(area: SessionFeedbackManager.Area, modifier: Modifier = Modifier) {
    val feedback = rememberSessionFeedbackManager()
    AnimatedVisibility(
        visible = feedback.showingIn == area,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (feedback.isThanking) {
                Text(
                    "Thanks!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "How is ExamReady doing this session?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                FeedbackPill("Bad") { feedback.answer(SessionFeedbackManager.Rating.BAD) }
                FeedbackPill("Fine") { feedback.answer(SessionFeedbackManager.Rating.FINE) }
                FeedbackPill("Good") { feedback.answer(SessionFeedbackManager.Rating.GOOD) }
                IconButton(
                    onClick = { feedback.answer(SessionFeedbackManager.Rating.DISMISSED) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

@Composable
private fun FeedbackPill(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 30.dp).heightIn(max = 30.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
