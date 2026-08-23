package com.goodstadt.john.language.exams.packages.UploadJson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * DEBUG-only screen listing the bundled quiz JSON that should be uploaded to the German Firestore
 * project, grouped into sections (Grammar / Section Sheet / UsageQuiz), each split by CEFR level.
 * Each row has Upload and Read buttons (placeholders for now). Close to return to the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadJsonScreen(
    onClose: () -> Unit,
    viewModel: UploadJsonViewModel = hiltViewModel()
) {
    val sections by viewModel.sections.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val adminResult by viewModel.adminResult.collectAsState()

    // Show upload/read errors as a long Toast.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Expand/collapse state (default collapsed, since the list is long). Absent key = collapsed.
    // Main Vocab starts expanded so its 4 rows are visible immediately.
    val expandedSections = remember {
        mutableStateMapOf(UploadJsonViewModel.VOCAB_SECTION_TITLE to true)
    }
    val expandedGroups = remember {
        mutableStateMapOf(
            "${UploadJsonViewModel.VOCAB_SECTION_TITLE}|${UploadJsonViewModel.VOCAB_GROUP_LEVEL}" to true
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload JSON (Debug)") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Top admin action: read <flavour>A1Vocab.uploadDate from the current flavour's Firestore project.
            item(key = "admin_actions") {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(onClick = { viewModel.readVocabUploadDate() }) {
                        Text("Read ${viewModel.a1VocabDocName} uploadDate")
                    }
                    adminResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
            }

            sections.forEach { section ->
                val sectionExpanded = expandedSections[section.title] == true
                val sectionCount = section.groups.sumOf { it.files.size }

                item(key = "section_${section.title}") {
                    SectionHeader(
                        title = section.title,
                        count = sectionCount,
                        expanded = sectionExpanded,
                        onClick = { expandedSections[section.title] = !sectionExpanded }
                    )
                }

                if (sectionExpanded) {
                    section.groups.forEach { group ->
                        val groupKey = "${section.title}|${group.level}"
                        val groupExpanded = expandedGroups[groupKey] == true

                        item(key = "group_$groupKey") {
                            GroupHeader(
                                level = group.level,
                                count = group.files.size,
                                expanded = groupExpanded,
                                onClick = { expandedGroups[groupKey] = !groupExpanded }
                            )
                        }

                        if (groupExpanded) {
                            if (group.files.isEmpty()) {
                                item(key = "empty_$groupKey") {
                                    Text(
                                        text = "(no files)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 40.dp, top = 2.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            items(group.files, key = { it.assetPath }) { file ->
                                UploadJsonRow(
                                    file = file,
                                    status = statuses[file.assetPath] ?: RowStatus.NONE,
                                    onUpload = { viewModel.uploadFile(file) },
                                    onRead = { viewModel.readFile(file) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Collapsible top-level section header (Grammar / Section Sheet / UsageQuiz). */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "$title  ($count)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider()
}

/** Collapsible per-level sub-header (A1 / A2 / B1 / B2). */
@Composable
private fun GroupHeader(
    level: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "$level  ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun UploadJsonRow(
    file: UploadJsonFile,
    status: RowStatus,
    onUpload: () -> Unit,
    onRead: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = file.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        StatusIcon(status)
        OutlinedButton(
            onClick = onUpload,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Upload") }
        OutlinedButton(
            onClick = onRead,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Read") }
    }
}

/** Tick (success), cross (error), or an empty fixed-width slot (not done yet) to keep rows aligned. */
@Composable
private fun StatusIcon(status: RowStatus) {
    when (status) {
        RowStatus.SUCCESS -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Done",
            tint = Color(0xFF4CAF50) // green
        )
        RowStatus.ERROR -> Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error
        )
        RowStatus.NONE -> Spacer(modifier = Modifier.size(24.dp))
    }
}
