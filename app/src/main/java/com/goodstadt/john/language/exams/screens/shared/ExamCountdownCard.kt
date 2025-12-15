package com.goodstadt.john.language.exams.screens.shared


import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.managers.XPManager
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamCountdownCard(
    xpManager: XPManager,
    totalWords: Int,
    masteredWords: Int,
    currentLevelName: String
) {
    val xpState by xpManager.state.collectAsState()

    // Local UI State
    var isEditing by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Date Picker State
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + (86400000L * 30) // Default 30 days
    )

    // Determine Border Color based on state
    val borderColor = if (xpState.targetExamLevel != null && !isEditing) {
        Color(0xFFFF9800).copy(alpha = 0.5f) // Active Orange
    } else {
        Color(0xFF2196F3).copy(alpha = 0.3f) // Setup Blue
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize() // Smooth expand/collapse
            // Border
            .padding(1.dp) // Gap for border
    ) {
        // We use a Box to draw the custom border stroke if CardDefaults doesn't support dynamic borders easily
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(borderColor, RoundedCornerShape(16.dp))
                .padding(1.dp) // Border width
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // ---------------------------------------------------------
                // LOGIC SWITCH: Active vs Setup
                // ---------------------------------------------------------

                if (xpState.targetExamLevel == null || isEditing) {
                    // === SETUP MODE ===
                    SetupHeader(currentLevelName = currentLevelName, isEditing = isEditing, onCancel = {
                        isEditing = false
                        showDatePicker = false
                    })

                    if (showDatePicker) {
                        // --- CALENDAR VIEW ---
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false,
                            modifier = Modifier.height(350.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Back")
                            }
                            Button(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    xpManager.setExactGoal(millis, currentLevelName)
                                    isEditing = false
                                    showDatePicker = false
                                }
                            }) {
                                Text("Confirm Date")
                            }
                        }
                    } else {
                        // --- BUTTONS VIEW ---
                        Text(
                            "Set a target date to get a daily study plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickGoalButton(
                                label = "3 Months",
                                icon = Icons.Default.DirectionsRun, // Hare/Run
                                modifier = Modifier.weight(1f)
                            ) {
                                xpManager.setDurationGoal(3, currentLevelName)
                                isEditing = false
                            }

                            QuickGoalButton(
                                label = "6 Months",
                                icon = Icons.Default.Hiking, // Tortoise/Hike
                                modifier = Modifier.weight(1f)
                            ) {
                                xpManager.setDurationGoal(6, currentLevelName)
                                isEditing = false
                            }
                        }

                        // Custom Date Button
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pick a specific date...")
                        }

                        // Clear Goal (Only if editing existing)
                        if (xpState.targetExamLevel != null) {
                            TextButton(
                                onClick = {
                                    xpManager.clearExamGoal()
                                    isEditing = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Clear Goal")
                            }
                        }
                    }

                } else {
                    // === ACTIVE MODE ===
                    ActiveModeView(
                        xpManager = xpManager,
                        totalWords = totalWords,
                        masteredWords = masteredWords,
                        currentLevelName = currentLevelName,
                        onEdit = { isEditing = true }
                    )
                }
            }
        }
    }
}

// MARK: - Subcomponents

@Composable
fun SetupHeader(currentLevelName: String, isEditing: Boolean, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.EditCalendar,
            contentDescription = null,
            tint = Color(0xFF2196F3) // Blue
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if(isEditing) "Update Goal?" else "Set Goal for $currentLevelName?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))

        if (isEditing) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ActiveModeView(
    xpManager: XPManager,
    totalWords: Int,
    masteredWords: Int,
    currentLevelName: String,
    onEdit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "MISSION: $currentLevelName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Formatted Countdown
                Text(
                    text = xpManager.getFormattedCountdown(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Edit Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune, // Sliders
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Advice / Velocity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.School, // Student Desk
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Calculate Advice Text
            val pace = xpManager.getExamPace(totalWords, masteredWords)
            val adviceText = if (pace != null) {
                if (pace.remaining <= 0) "Level Complete! Review to maintain."
                else "Learn ${pace.dailyRate} words/day to finish."
            } else {
                "Exam date passed!"
            }

            Text(
                text = adviceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Reusable Quick Button
@Composable
fun QuickGoalButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2196F3).copy(alpha = 0.1f),
            contentColor = Color(0xFF2196F3)
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}