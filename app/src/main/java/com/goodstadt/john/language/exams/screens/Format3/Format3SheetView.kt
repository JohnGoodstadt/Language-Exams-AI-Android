package com.goodstadt.john.language.exams.screens.Format3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.models.Format3Category
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format3Sentence

// --- Colors ---
val IosOrange = Color(0xFFFF9500)

// Strict Dark Mode Colors
val IosBackgroundDark = Color(0xFF000000)
val IosSurfaceDark = Color(0xFF1C1C1E) // Dark Grey for cards

@Composable
fun Format3SheetView(
    file: Format3File,
    onSentenceTapped: (String) -> Unit,
    vm: Format3FileViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    // CHANGED: Removed isSystemInDarkTheme() check.
    // We now hardcode these variables to the Dark values.
    val backgroundColor = IosBackgroundDark
    val cardBackgroundColor = IosSurfaceDark
    val primaryTextColor = Color.White
    val secondaryTextColor = Color.LightGray

    // Initialize
    LaunchedEffect(file) {
        vm.setFile(file)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor) // Always Black
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.errorMessage != null -> {
                Text(
                    text = state.errorMessage ?: "Error",
                    color = primaryTextColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.file != null -> {
                Format3ContentList(
                    file = state.file!!,
                    sentencesFor = { vm.sentencesFor(it) },
                    onRowTapped = onSentenceTapped,
                    cardColor = cardBackgroundColor,
                    textColor = primaryTextColor,
                    secondaryColor = secondaryTextColor
                )
            }
        }
    }
}

@Composable
private fun Format3ContentList(
    file: Format3File,
    sentencesFor: (Format3Category) -> List<Format3Sentence>,
    onRowTapped: (String) -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            TopSheetInfoView(
                file = file,
                textColor = textColor
            )
        }

        val sortedCategories = file.categories.sortedBy { it.sortOrder }

        items(sortedCategories, key = { it.sortOrder }) { category ->
            CategorySectionView(
                category = category,
                sentences = sentencesFor(category),
                onRowTapped = onRowTapped,
                cardColor = cardColor,
                textColor = textColor,
                secondaryColor = secondaryColor
            )
        }
    }
}

@Composable
private fun TopSheetInfoView(
    file: Format3File,
    textColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        //TODO: move to vm
        fun translateLanguageToTopTitle(language:String): String {
            val title = when (language.lowercase()) {
                "spanish" -> "Special note for Spanish speakers"
                else -> language
            }

            return title
        }

        if (file.targetLanguage.isNotEmpty()) {
            Text(
                text = translateLanguageToTopTitle(file.targetLanguage),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                modifier = Modifier.fillMaxWidth().padding(),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = file.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = IosOrange,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (file.subtitle.isNotEmpty()) {
            Text(
                text = file.subtitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (file.description.isNotEmpty()) {
            Text(
                text = file.description,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            color = Color.DarkGray // CHANGED: Forced Dark Gray
        )
    }
}

@Composable
private fun CategorySectionView(
    category: Format3Category,
    sentences: List<Format3Sentence>,
    onRowTapped: (String) -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color
) {
    Column {
        // --- HEADER ---
        Column(
            modifier = Modifier
                .padding(bottom = 8.dp, start = 4.dp)
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall,
                color = IosOrange,
                fontWeight = FontWeight.Bold
            )
            if (category.description.isNotEmpty()) {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        // --- CONTENT CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                sentences.forEachIndexed { index, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRowTapped(s.sentence) }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = s.sentence,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }

                    if (index < sentences.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            // CHANGED: Forced Dark Gray for the divider
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}