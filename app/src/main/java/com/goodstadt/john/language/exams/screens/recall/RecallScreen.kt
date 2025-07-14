// In a new file, e.g., screens/recall/RecallScreen.kt
package com.goodstadt.john.language.exams.screens.recall

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.Filter3
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.data.RecallingItem
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.navigation.IconResource
import com.goodstadt.john.language.exams.utils.STOPS
import com.goodstadt.john.language.exams.viewmodels.RecallViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

// ===== Top-Level Screen (Replaces RecallingView) =====
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecallScreen(
    viewModel: RecallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus on these Words") },
                actions = {
                    // Add an icon button to the top right
                    IconButton(onClick = { viewModel.onClearAllClicked() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All"
                        )
                    }
                    if(BuildConfig.DEBUG) {
                        IconButton(onClick = { viewModel.onDebugPrintSummaryClicked() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Clear All"
                            )
                        }
                    }

                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.todayItems.isNotEmpty()) {
                stickyHeader {
                    ListHeader("Do Today")
                }
                items(uiState.todayItems, key = { it.id }) { item ->
                    RecallingListItem(
                        item = item,
                        viewModel = viewModel,
                        wordCount = uiState.wordCounts[item.key] ?: 0,
                        onRemove = { viewModel.onRemoveClicked(item.key) },
                        onOk = { viewModel.onOkClicked(item.key) },
                        onPlayWord = { viewModel.onPlayWord(it) },
                        onPlaySentence = { word, sentence -> viewModel.onPlaySentence(word, sentence) },
                        getSentencesForWord = { viewModel.getSentencesForWord(it) }
                    )
                }
            }

            if (uiState.laterItems.isNotEmpty()) {
                stickyHeader {
                    ListHeader("Do Later")
                }
                items(uiState.laterItems, key = { it.id }) { item ->
                    RecallingListItem(
                        item = item,
                        viewModel = viewModel,
                        wordCount = uiState.wordCounts[item.key] ?: 0,
                        onRemove = { viewModel.onRemoveClicked(item.key) },
                        onOk = { viewModel.onOkClicked(item.key) },
                        onPlayWord = { viewModel.onPlayWord(it) },
                        onPlaySentence = { word, sentence -> viewModel.onPlaySentence(word, sentence) },
                        getSentencesForWord = { viewModel.getSentencesForWord(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ListHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    )
}


// ===== List Item View (Replaces RecallingListItemView) =====
@Composable
fun RecallingListItem(
    item: RecallingItem,
    viewModel: RecallViewModel,
    wordCount: Int,
    onRemove: () -> Unit,
    onOk: () -> Unit,
    onPlayWord: (String) -> Unit,
    onPlaySentence: (String, String) -> Unit,
    getSentencesForWord: (String) -> List<String>
) {
    var isOverdue by remember { mutableStateOf(false) }
    var debugString by remember { mutableStateOf("") }

    // This effect replaces the timer logic (.onReceive)
    LaunchedEffect(key1 = item.nextEventTime) {
        while (true) {
            val now = System.currentTimeMillis()
            isOverdue = item.nextEventTime < now
            debugString = getDebugString(item) // Create a Kotlin version of this function
            delay(5000) // Ticks every 5 seconds
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RecallingItemContent(
                item = item,
                viewModel = viewModel,
                wordCount = wordCount,
                onPlayWord = onPlayWord,
                onPlaySentence = onPlaySentence,
                getSentencesForWord = getSentencesForWord,
                debugString = debugString
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onRemove) { Text("Remove") }
                if (isOverdue) {
                    Button(onClick = onOk) { Text("OK") }
                }
            }
        }
    }
}


// ===== Content View (Replaces RecallingNonChineseView) =====
@Composable
fun RecallingItemContent(
    item: RecallingItem,
    viewModel: RecallViewModel,
    wordCount: Int,
    debugString: String,
    onPlayWord: (String) -> Unit,
    onPlaySentence: (String, String) -> Unit,
    getSentencesForWord: (String) -> List<String>
) {
    // State for the sentences, loaded when the composable appears
    var sentences by remember { mutableStateOf<List<String>>(emptyList()) }
    var tabDetails by remember { mutableStateOf(TabDetails("", 0)) }

    LaunchedEffect(key1 = item.key) {
//        sentences = getSentencesForWord(item.key)
        sentences = viewModel.fetchSentencesForWord(item.key)
        tabDetails = viewModel.fetchTabDetailsForWord(item.key)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header row with Play button

        Row(
            // V V V ADD THIS MODIFIER V V V
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPlayWord(item.key) },
            // ^ ^ ^ ADD THIS MODIFIER ^ ^ ^
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onPlayWord(item.key) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play word")
            }
            Text(item.key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (wordCount > 0) {
                Text(wordCount.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }

        // Sentences
        sentences.forEach { sentence ->
            Text(
                text = sentence,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaySentence(item.key, sentence) }
                    .padding(vertical = 4.dp)
            )
        }

        // Additional Text
        if (item.additionalText.isNotEmpty()) {
            Text(item.additionalText, color = Color.Red, fontWeight = FontWeight.Bold)
        }

        // TODO: Implement translation logic for 'showTranslation'

        // Footer Info
        if (tabDetails.tabNumber != 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val iconResource: IconResource = when (tabDetails.tabNumber) {
                    1 -> IconResource.DrawableIcon(R.drawable.counter_0_24px) // Use your new drawable
                    2 -> IconResource.VectorIcon(Icons.Outlined.Filter2)
                    3 -> IconResource.VectorIcon(Icons.Outlined.Filter3)
                    else -> IconResource.VectorIcon(Icons.Default.Info) // Default is still a VectorIcon
                }
                when (iconResource) {
                    is IconResource.VectorIcon -> {
                        Icon(
                            imageVector = iconResource.imageVector,
                            contentDescription = "Tab Info",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    is IconResource.DrawableIcon -> {
                        Icon(
                            painter = painterResource(id = iconResource.id),
                            contentDescription = "Tab Info",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // TODO: Replace with your tab lookup logic
//                Text("Tab 1: Basics", style = MaterialTheme.typography.bodySmall)
            Text(
                text = tabDetails.title,
                style = MaterialTheme.typography.bodySmall
                 )
            }
        }
       // Text(debugString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

// TODO: Translate the Swift getDebugString function to Kotlin
fun getDebugString(item: RecallingItem): String {
    val durationInMillis = item.nextEventTime - System.currentTimeMillis()
    val suffix = if (durationInMillis < 0) "ago" else "left"
    val absMillis = kotlin.math.abs(durationInMillis)

    val days = TimeUnit.MILLISECONDS.toDays(absMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(absMillis) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(absMillis) % 60

    val countdown = when {
        days > 0 -> "$days days $suffix"
        hours > 0 -> "$hours hours $minutes min $suffix"
        else -> "$minutes min $suffix"
    }


    return "${item.recallState} | $countdown\n${item.currentStopNumber} of ${STOPS.size}: (${item.currentStopCode()})"
}