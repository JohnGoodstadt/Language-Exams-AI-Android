package com.goodstadt.john.language.exams.packages.Focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Focus Areas: a raw dump of the shared (category, level) tally, weakest first. Deliberately a
 * plain list for now - we'll shape it into a proper priority view later.
 */
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Focus Areas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Where to improve, tallied from your audit and quizzes. Weakest first.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))

        // Column headers
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Area", Modifier.weight(1f), TextAlign.Start)
            HeaderCell("Lvl", Modifier.width(40.dp))
            HeaderCell("✓", Modifier.width(40.dp))
            HeaderCell("✗", Modifier.width(40.dp))
            HeaderCell("?", Modifier.width(40.dp))
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

        if (rows.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "No data yet — take the Audit or a Vocab quiz and your weak areas will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows) { row ->
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
                        DataCell("${row.score.dontKnow}", Modifier.width(40.dp), Color(0xFFFFA500))
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
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
