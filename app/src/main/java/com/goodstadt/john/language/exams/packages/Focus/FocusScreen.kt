package com.goodstadt.john.language.exams.packages.Focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.GrammarRow
import com.goodstadt.john.language.exams.data.strength.ReferenceStrength
import com.goodstadt.john.language.exams.data.strength.ReferenceStrengthLevel
import com.goodstadt.john.language.exams.packages.CategoryTab.SectionQuizContainer
import com.goodstadt.john.language.exams.packages.ReferenceQuiz.ReferenceQuizScreen
import com.goodstadt.john.language.exams.packages.ReadinessAudit.ReadinessAuditScreen
import com.goodstadt.john.language.exams.screens.shared.CollapsibleSection
import com.goodstadt.john.language.exams.ui.theme.ElevatedDarkGrey
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.getDaysSinceInstall
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val focusState by viewModel.focusState.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val stats by viewModel.auditStats.collectAsState()
    val auditVersion by viewModel.auditVersion.collectAsState()

    val practiceTarget by viewModel.practiceTarget.collectAsState()
    val grammarCatalog by viewModel.grammarCatalog.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val referenceStrengths by viewModel.referenceStrengths.collectAsState()
    val vocabQuizStrengths by viewModel.vocabQuizStrengths.collectAsState()
    val usageQuizStrengths by viewModel.usageQuizStrengths.collectAsState()
    val weakVocabSections by viewModel.weakVocabSections.collectAsState()
    val practiceVocabTarget by viewModel.practiceVocabTarget.collectAsState()
    val vocabSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showAuditSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Focus Areas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))

        when (focusState) {
            is FocusUiState.NotEnoughData ->
                // Always shown: the learner still needs to take the baseline audit.
                NotEnoughDataCard(confidence = stats.confidence, onTakeAudit = { showAuditSheet = true })

            is FocusUiState.AllCaughtUp ->
                // Reassurance card that isn't needed daily: from the day after install it starts collapsed
                // (one tap to reveal); on install day it stays open.
                CollapsibleSection(
                    title = "All caught up",
                    initiallyExpanded = getDaysSinceInstall(context) < 2
                ) {
                    AllCaughtUpCard(currentLevel = currentLevel)
                }

            // Weak areas now surface directly in Category progress below (red segments).
            is FocusUiState.Priorities -> Unit
        }

        // --- Category progress: gamification-style progress bar per category (green correct / red
        // incorrect / grey not done, out of 10), modelled on TopicProgressRow. Per CEFR level, shows
        // only categories with a coloured bar (attempted) PLUS one untouched category as a nudge (the
        // topmost not-yet-attempted at that level). Tapping a row opens that category's quiz.
        // (Later: drop levels above the learner's level.) ---
        if (grammarCatalog.isNotEmpty()) {
            fun answeredCount(e: GrammarCatalogEntry) = e.score.correct + e.score.incorrect + e.score.dontKnow

            // Show only the learner's CURRENT level (e.g. on A1, show A1 only). Fall back to all levels
            // if the current level is unknown/unset.
            val levelOrder = listOf("A1", "A2", "B1", "B2")
            val levelsToShow = if (currentLevel in levelOrder) listOf(currentLevel) else levelOrder

            Spacer(Modifier.height(8.dp))
            levelsToShow.forEach { level ->
                val levelAll = grammarCatalog.filter { it.row.level == level }
                // One nudge per level: the first not-yet-attempted category at this level.
                val nudge = levelAll.firstOrNull { answeredCount(it) == 0 }
                val levelEntries = levelAll.filter { answeredCount(it) > 0 || it === nudge }
                if (levelEntries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        level,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = orangeLight
                    )
                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            levelEntries.forEach { entry ->
                                CategoryProgressBarRow(
                                    entry = entry,
                                    onClick = { viewModel.practiceGrammar(entry.row) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Weak vocab sections: any section quiz whose last go was < 75% correct first-time. ---
        WeakVocabSectionsSection(
            sections = weakVocabSections,
            onPractice = { viewModel.practiceVocab(it) }
        )

        // --- Vocab Quiz areas: per-category Weak / OK (Strong hidden) from the vocab section quizzes,
        // shown just like the reference areas. Hidden entirely when nothing qualifies. ---
        VocabQuizAreasSection(strengths = vocabQuizStrengths)

        // --- Usage Quiz areas: per-quiz Weak / OK (Strong hidden) from the usage quizzes, shown just like
        // the reference areas. Hidden entirely when nothing qualifies. ---
        UsageQuizAreasSection(strengths = usageQuizStrengths)

        // --- Reference areas: coarse Weak / OK / Strong per reference-tab area (Prepositions,
        // Adjectives, Pronouns, Sounds Similar, Word Pairs), summarised from the reference quizzes. ---
        ReferenceAreasSection(strengths = referenceStrengths)

        // --- DEBUG: one Download button per grammar sheet, in A1..B2 sections. Tests the new
        // Format7/10 download code (result is cached to disk, not displayed). ---
        if (DEBUG && grammarCatalog.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "DEBUG — Download grammar sheet (Format 7/10)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB0B0B0)
            )
            val byLevel = grammarCatalog.groupBy { it.row.level }
            listOf("A1", "A2", "B1", "B2").forEach { level ->
                val rows = byLevel[level].orEmpty()
                if (rows.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        level,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = orangeLight
                    )
                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rows.forEach { entry ->
                                DebugGrammarDownloadRow(
                                    label = entry.row.category,
                                    status = downloadStatus[viewModel.grammarLogicalName(entry.row)],
                                    onDownload = { viewModel.debugDownloadGrammarSheet(entry.row) },
                                    onQuiz = { viewModel.practiceGrammar(entry.row) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAuditSheet = false },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(0.92f),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = ElevatedDarkGrey
        ) {
            ReadinessAuditScreen(
                initialVersion = auditVersion,
                onFinished = { showAuditSheet = false }
            )
        }
    }

    practiceTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissPractice() },
            sheetState = quizSheetState,
            modifier = Modifier.fillMaxHeight(0.92f),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = ElevatedDarkGrey
        ) {
            // Full Usage-Quiz experience (TTS, mastery filter, badges) for this one (category, level).
            ReferenceQuizScreen(category = target.category, level = target.level)
        }
    }

    // Weak vocab section quiz (launched from the "Vocab to review" list). category carries the quiz KEY.
    practiceVocabTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissVocabPractice() },
            sheetState = vocabSheetState,
            modifier = Modifier.fillMaxHeight(0.92f),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = ElevatedDarkGrey
        ) {
            SectionQuizContainer(
                categoryTitle = target.category,
                level = target.level,
                isKey = true
            )
        }
    }
}

/** The take-the-audit prompt: same copy/controls as the Progress header, minus the rings and the
 *  Change Level / New Audit buttons. */
@Composable
private fun NotEnoughDataCard(confidence: Int, onTakeAudit: () -> Unit) {
    val nextPartName = when {
        confidence == 0 -> "Part 1: Baseline"
        confidence < 40 -> "Part 1: Baseline (Resume)"
        confidence < 65 -> "Part 2: Logic"
        confidence < 85 -> "Part 3: Lexis"
        else -> "Part 4: Core"
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("📋 Let's get you set up", color = orangeLight, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please take the audit so we can configure the app to match your level.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }
        }

        Button(
            onClick = onTakeAudit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = orangeLight)
        ) {
            Text("Verify $nextPartName", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Text(
            "Continue the audit to increase our confidence to 98%",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

/** Shown once the learner has been assessed and has no weak areas at/below their level. */
@Composable
private fun AllCaughtUpCard(currentLevel: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🎉 You're all caught up", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "No weak areas at or below your level ($currentLevel). Keep practising to stay sharp — new gaps will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
        }
    }
}

/**
 * One category as a gamification-style progress bar (modelled on TopicProgressRow). Out of [total]
 * questions: green = correct, red = incorrect, grey = not done. If the learner has answered more
 * than [total] over repeated attempts, the bar scales to that larger total so it never overflows.
 */
@Composable
private fun CategoryProgressBarRow(entry: GrammarCatalogEntry, onClick: () -> Unit, total: Int = 10) {
    val s = entry.score
    val answered = s.correct + s.incorrect + s.dontKnow
    val denom = maxOf(total, answered).toFloat()
    val greenFrac = s.correct / denom
    val redFrac = s.incorrect / denom
    val greyFrac = (1f - greenFrac - redFrac).coerceAtLeast(0f)

    // Whole row is tappable and opens the same quiz as the Play button above.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.row.category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (answered > 0) Color.White else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (answered > 0) "${s.correct}/$total" else "-",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        // Segmented bar: grey track with green + red segments drawn from the left.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.25f))
        ) {
            if (greenFrac > 0f) Box(Modifier.fillMaxHeight().weight(greenFrac).background(Color(0xFF4CAF50)))
            if (redFrac > 0f) Box(Modifier.fillMaxHeight().weight(redFrac).background(Color(0xFFE53935)))
            if (greyFrac > 0f) Box(Modifier.fillMaxHeight().weight(greyFrac))
        }
    }
}

/**
 * Vocab sections whose latest go was weak (< 75% correct first-time). Each row: section title (left) +
 * first-try score like "12/18" (right) + a green/red flood bar. Hidden when there are no weak sections.
 */
@Composable
private fun WeakVocabSectionsSection(
    sections: List<com.goodstadt.john.language.exams.data.repository.VocabQuizRepository.WeakVocabSection>,
    onPractice: (com.goodstadt.john.language.exams.data.repository.VocabQuizRepository.WeakVocabSection) -> Unit
) {
    if (sections.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Vocab to review",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Sections where you got under 75% right first time — worth another go.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.LightGray
    )
    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            sections.forEach { WeakVocabRow(it, onClick = { onPractice(it) }) }
        }
    }
}

/** Turn a camel-case quiz key into a display label, e.g. "ComplexSentences" -> "Complex Sentences". */
private fun humanizeVocabKey(key: String): String =
    key.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim()

/** One weak vocab section: title + "firstTryCorrect/total" + a green/red flood bar. Tap launches the quiz. */
@Composable
private fun WeakVocabRow(
    section: com.goodstadt.john.language.exams.data.repository.VocabQuizRepository.WeakVocabSection,
    onClick: () -> Unit
) {
    val denom = maxOf(section.total, 1).toFloat()
    val greenFrac = section.firstTryCorrect / denom
    val redFrac = maxOf(0, section.total - section.firstTryCorrect) / denom
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = humanizeVocabKey(section.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${section.firstTryCorrect}/${section.total}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.25f))
        ) {
            if (greenFrac > 0f) Box(Modifier.fillMaxHeight().weight(greenFrac).background(Color(0xFF4CAF50)))
            if (redFrac > 0f) Box(Modifier.fillMaxHeight().weight(redFrac).background(Color(0xFFE53935)))
        }
    }
}

/**
 * The "Vocab Quiz" section: per-category Weak / OK from the vocab section quizzes (Strong is not a
 * weakness, so it's filtered out in the ViewModel). Rendered exactly like the Reference areas, reusing
 * [ReferenceAreaRow]. The whole section is hidden when nothing qualifies - Focus stays about what needs work.
 */
@Composable
private fun VocabQuizAreasSection(strengths: List<ReferenceStrength>) {
    if (strengths.isEmpty()) return

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Vocab Quiz",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Vocab topics worth another look, from their quiz — anything marked Weak or OK isn't solid yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.LightGray
    )

    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            strengths.forEach { ReferenceAreaRow(it) }
        }
    }
}

/**
 * The "Usage Quiz" section: per-quiz Weak / OK from the usage quizzes (Strong is not a weakness, so it's
 * filtered out in the ViewModel). Rendered exactly like the Reference / Vocab Quiz areas, reusing
 * [ReferenceAreaRow]. The whole section is hidden when nothing qualifies.
 */
@Composable
private fun UsageQuizAreasSection(strengths: List<ReferenceStrength>) {
    if (strengths.isEmpty()) return

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Usage Quiz",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Usage topics worth another look, from their quiz — anything marked Weak or OK isn't solid yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.LightGray
    )

    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            strengths.forEach { ReferenceAreaRow(it) }
        }
    }
}

/**
 * The "Reference areas" section: a coarse Weak / OK / Strong per reference-tab area, summarised from the
 * reference quizzes' per-pattern marks. Areas with too little data to rate are omitted; when none can be
 * rated yet, only the header + explanation show (so the learner knows how to reveal weak areas).
 */
@Composable
private fun ReferenceAreasSection(strengths: List<ReferenceStrength>) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Reference areas",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "How strong you are in each reference topic, from its quiz. Take a reference quiz (the Q button) " +
            "to reveal weak spots — anything marked Weak is worth another look.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.LightGray
    )

    if (strengths.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No reference quizzes taken yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        return
    }

    Spacer(Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            strengths.forEach { ReferenceAreaRow(it) }
        }
    }
}

/** One reference area: its name + a coarse stat line, and a coloured Weak/OK/Strong pill. */
@Composable
private fun ReferenceAreaRow(strength: ReferenceStrength) {
    val (label, color) = when (strength.level) {
        ReferenceStrengthLevel.WEAK -> "Weak" to Color(0xFFE53935)
        ReferenceStrengthLevel.OK -> "OK" to Color(0xFFFF9800)
        ReferenceStrengthLevel.STRONG -> "Strong" to Color(0xFF4CAF50)
        ReferenceStrengthLevel.UNTESTED -> "—" to Color.Gray
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strength.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = "${(strength.mark * 100).roundToInt()}% · " +
                    "${strength.attempts} ${if (strength.attempts == 1) "quiz" else "quizzes"}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/** DEBUG row: a grammar sheet name + Download button + a Quiz button (opens the quiz, same as the
 *  rows above) + last download status (OK/ERROR). */
@Composable
private fun DebugGrammarDownloadRow(
    label: String,
    status: String?,
    onDownload: () -> Unit,
    onQuiz: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.startsWith("ERROR")) Color(0xFFE53935) else Color(0xFF9E9E9E)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onDownload,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Download") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onQuiz,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Quiz") }
    }
}
