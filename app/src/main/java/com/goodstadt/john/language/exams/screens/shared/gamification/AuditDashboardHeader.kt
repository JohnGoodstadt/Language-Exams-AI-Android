package com.goodstadt.john.language.exams.screens.shared.gamification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.AuditStats
import com.goodstadt.john.language.exams.viewmodels.ReadinessAuditLevels


@Composable
fun AuditDashboardHeader(
    stats: AuditStats,
    currentLevel: String,
    unlockedLevels: Set<ReadinessAuditLevels>,
    onNavigateToAudit: () -> Unit,
    onAdjustLevel: () -> Unit,
    onNewAudit: () -> Unit
) {
    // Logic to determine the "Next Step" text
    val nextPartName = when {
        stats.confidence == 0 -> "Part 1: Baseline"
        stats.confidence < 40 -> "Part 1: Baseline (Resume)"
        stats.confidence < 65 -> "Part 2: Logic"
        stats.confidence < 85 -> "Part 3: Lexis"
        else -> "Part 4: Core"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. THE GRAPHIC AREA ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuditCircularGauge(
                label = "Calibration",
                value = stats.confidence,
                color = blueBright2,
                description = "Data Certainty"
            )
            AuditCircularGauge(
                label = "Readiness",
                value = stats.readiness,
                color = if (stats.readiness > 70) Color.Green else orangeLight,
                description = "Exam Score"
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- 2. THE DYNAMIC PRIMARY ACTION ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onNavigateToAudit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = orangeLight)
            ) {
                Text("Verify $nextPartName", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Continue the audit to increase calibration to 98%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- 3. THE SECONDARY DECISIONS ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Level Adjustment
            AuditActionButton(
                modifier = Modifier.weight(1f),
                title = "Change Level",
                subtitle = "Currently $currentLevel",
                icon = Icons.Default.SwapHoriz,
                onClick = onAdjustLevel
            )

            // New Test / Reset
            AuditActionButton(
                modifier = Modifier.weight(1f),
                title = "New Audit",
                subtitle = "Fresh Questions",
                icon = Icons.Default.Refresh,
                onClick = onNewAudit
            )
        }
    }
}

@Composable
private fun AuditCircularGauge(label: String, value: Int, color: Color, description: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { value / 100f },
                modifier = Modifier.size(80.dp),
                color = color,
                strokeWidth = 8.dp,
                trackColor = Color.DarkGray,
                strokeCap = StrokeCap.Round
            )
            Text("$value%", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun AuditActionButton(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = orangeLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}