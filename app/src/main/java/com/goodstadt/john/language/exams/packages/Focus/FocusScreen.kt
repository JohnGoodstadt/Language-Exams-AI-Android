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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.goodstadt.john.language.exams.packages.ReadinessAudit.ReadinessAuditScreen
import com.goodstadt.john.language.exams.ui.theme.ElevatedDarkGrey
import com.goodstadt.john.language.exams.ui.theme.orangeLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val focusRows by viewModel.focusRows.collectAsState()
    val allRows by viewModel.allRows.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val stats by viewModel.auditStats.collectAsState()
    val auditVersion by viewModel.auditVersion.collectAsState()

    var showAuditSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Focus Areas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))

        if (focusRows.isEmpty()) {
            // Not enough data (or nothing weak at/below their level) -> nudge them to the audit.
            NotEnoughDataCard(confidence = stats.confidence, onTakeAudit = { showAuditSheet = true })
        } else {
            Text(
                "Areas to improve at or below your level ($currentLevel):",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )
            Spacer(Modifier.height(8.dp))
            FocusTable(focusRows)
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

@Composable
private fun FocusTable(rows: List<FocusRow>) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Area", Modifier.weight(1f), TextAlign.Start)
            HeaderCell("Lvl", Modifier.width(40.dp))
            HeaderCell("✓", Modifier.width(40.dp))
            HeaderCell("✗", Modifier.width(40.dp))
            HeaderCell("?", Modifier.width(40.dp))
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                DataCell(row.level, Modifier.width(40.dp))
                DataCell("${row.score.correct}", Modifier.width(40.dp), Color(0xFF4CAF50))
                DataCell("${row.score.incorrect}", Modifier.width(40.dp), Color(0xFFE53935))
                DataCell("${row.score.dontKnow}", Modifier.width(40.dp), orangeLight)
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
