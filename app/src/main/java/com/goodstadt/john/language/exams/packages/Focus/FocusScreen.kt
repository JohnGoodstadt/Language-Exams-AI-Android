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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.data.GrammarRow
import com.goodstadt.john.language.exams.packages.GrammarQuiz.GrammarQuizScreen
import com.goodstadt.john.language.exams.packages.ReadinessAudit.ReadinessAuditScreen
import com.goodstadt.john.language.exams.ui.theme.ElevatedDarkGrey
import com.goodstadt.john.language.exams.ui.theme.orangeLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val focusState by viewModel.focusState.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val stats by viewModel.auditStats.collectAsState()
    val auditVersion by viewModel.auditVersion.collectAsState()

    val practiceTarget by viewModel.practiceTarget.collectAsState()
    val grammarCatalog by viewModel.grammarCatalog.collectAsState()

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

        when (val state = focusState) {
            is FocusUiState.NotEnoughData ->
                NotEnoughDataCard(confidence = stats.confidence, onTakeAudit = { showAuditSheet = true })

            is FocusUiState.AllCaughtUp ->
                AllCaughtUpCard(currentLevel = currentLevel)

            is FocusUiState.Priorities -> {
                Text(
                    "Your top priorities at or below $currentLevel:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(8.dp))
                FocusTable(state.rows, onPractice = { viewModel.practiceCategory(it) })
            }
        }

        // --- Category progress: gamification-style progress bar per category (green correct / red
        // incorrect / grey not done, out of 10), modelled on TopicProgressRow. Per CEFR level, shows
        // only categories with a coloured bar (attempted) PLUS one untouched category as a nudge (the
        // topmost not-yet-attempted at that level). Tapping a row opens that category's quiz.
        // (Later: drop levels above the learner's level.) ---
        if (grammarCatalog.isNotEmpty()) {
            fun answeredCount(e: GrammarCatalogEntry) = e.score.correct + e.score.incorrect + e.score.dontKnow

            Spacer(Modifier.height(20.dp))
            Text(
                "Category progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            listOf("A1", "A2", "B1", "B2").forEach { level ->
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
            GrammarQuizScreen(category = target.category, level = target.level)
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

@Composable
private fun FocusTable(rows: List<FocusRow>, onPractice: ((FocusRow) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        // The priority list (onPractice != null) is a clean to-do list: category + level + Play.
        // The count columns live only in the debug table below, so they stay off here.
        val showCounts = onPractice == null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Area", Modifier.weight(1f), TextAlign.Start)
            HeaderCell("Lvl", Modifier.width(36.dp))
            if (showCounts) {
                HeaderCell("✓", Modifier.width(32.dp))
                HeaderCell("✗", Modifier.width(32.dp))
                HeaderCell("?", Modifier.width(32.dp))
            }
            if (onPractice != null) Spacer(Modifier.width(40.dp)) // column for the Practice button
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                DataCell(row.level, Modifier.width(36.dp))
                if (showCounts) {
                    DataCell("${row.score.correct}", Modifier.width(32.dp), Color(0xFF4CAF50))
                    DataCell("${row.score.incorrect}", Modifier.width(32.dp), Color(0xFFE53935))
                    DataCell("${row.score.dontKnow}", Modifier.width(32.dp), orangeLight)
                }
                if (onPractice != null) {
                    IconButton(onClick = { onPractice(row) }, modifier = Modifier.width(40.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Practice ${row.category}", tint = orangeLight)
                    }
                }
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 0.5.dp)
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

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Center) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        textAlign = align,
        fontSize = 13.sp
    )
}

@Composable
private fun DataCell(text: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center
    )
}
