package com.goodstadt.john.language.exams.packages.Focus

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
    val allRows by viewModel.allRows.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val stats by viewModel.auditStats.collectAsState()
    val auditVersion by viewModel.auditVersion.collectAsState()

    val practiceTarget by viewModel.practiceTarget.collectAsState()
    val grammarCatalog by viewModel.grammarCatalog.collectAsState()

    var showAuditSheet by remember { mutableStateOf(false) }
    var answeredOnly by remember { mutableStateOf(false) }
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

        // --- All grammar categories: browse & practise anything, not just tested-weak areas. ---
        // Shows the learner's running score per category. (Starting point for the "weak -> all
        // categories" filter; keep or drop once decided.)
        if (grammarCatalog.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "All grammar categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Toggle: when selected, show only categories the learner has answered (non-zero
                // score); when unselected, show the whole grid. FilterChip so the on/off state is
                // visually obvious.
                FilterChip(
                    selected = answeredOnly,
                    onClick = { answeredOnly = !answeredOnly },
                    label = { Text("Answered only") },
                    leadingIcon = if (answeredOnly) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
            Spacer(Modifier.height(8.dp))
            val shownEntries = if (answeredOnly) {
                grammarCatalog.filter { it.score.correct + it.score.incorrect + it.score.dontKnow > 0 }
            } else {
                grammarCatalog
            }
            if (shownEntries.isEmpty()) {
                Text(
                    "No categories answered yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                GrammarCatalogTable(
                    entries = shownEntries,
                    onPractice = { viewModel.practiceGrammar(it) }
                )
            }
        }

        // --- Debug section: the whole tally, unfiltered ---
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Color.DarkGray)
        Spacer(Modifier.height(8.dp))
        Text(
            "All Focus data (debug)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        if (allRows.isEmpty()) {
            Text("(no data yet)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            FocusTable(allRows)
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

/** The full canonical grammar grid (category × level), each row showing the learner's running score
 *  and a Play button. */
@Composable
private fun GrammarCatalogTable(entries: List<GrammarCatalogEntry>, onPractice: (GrammarRow) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Area", Modifier.weight(1f), TextAlign.Start)
            HeaderCell("Lvl", Modifier.width(32.dp))
            HeaderCell("✓", Modifier.width(28.dp))
            HeaderCell("✗", Modifier.width(28.dp))
            // The audit feeds these categories and does have a "Don't Know" button, so ? can be > 0.
            HeaderCell("?", Modifier.width(28.dp))
            Spacer(Modifier.width(40.dp))
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        entries.forEach { entry ->
            val row = entry.row
            val s = entry.score
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Short label is primary, full name a dim subtitle beneath.
                Column(Modifier.weight(1f)) {
                    Text(
                        row.shortLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = orangeLight
                    )
                    Text(
                        row.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                DataCell(row.level, Modifier.width(32.dp))
                DataCell("${s.correct}", Modifier.width(28.dp), Color(0xFF4CAF50))
                DataCell("${s.incorrect}", Modifier.width(28.dp), Color(0xFFE53935))
                // Blank out a zero "?" so the occasional non-zero don't-know stands out.
                DataCell(if (s.dontKnow == 0) "" else "${s.dontKnow}", Modifier.width(28.dp), orangeLight)
                IconButton(onClick = { onPractice(row) }, modifier = Modifier.width(40.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Practice ${row.category}", tint = orangeLight)
                }
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 0.5.dp)
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
