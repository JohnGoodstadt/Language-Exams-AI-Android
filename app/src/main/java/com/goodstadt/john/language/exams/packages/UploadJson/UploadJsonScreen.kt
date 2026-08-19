package com.goodstadt.john.language.exams.packages.UploadJson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            sections.forEach { section ->
                item(key = "section_${section.title}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                    )
                    HorizontalDivider()
                }

                section.groups.forEach { group ->
                    item(key = "group_${section.title}_${group.level}") {
                        Text(
                            text = group.level,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    if (group.files.isEmpty()) {
                        item(key = "empty_${section.title}_${group.level}") {
                            Text(
                                text = "(no files)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(group.files, key = { it.assetPath }) { file ->
                        UploadJsonRow(
                            file = file,
                            status = statuses[file.assetPath],
                            onUpload = { viewModel.uploadFile(file) },
                            onRead = { viewModel.readFile(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadJsonRow(
    file: UploadJsonFile,
    status: String?,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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
