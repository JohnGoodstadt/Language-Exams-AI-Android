package com.goodstadt.john.language.exams.packages.Format9

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.SubTabDefinition
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip

/** Keeps the podcast selector visible while the chosen script loads or plays. */
@Composable
fun Format9GroupedScreen(
    subTabs: List<SubTabDefinition>,
    fallbackDocumentId: String = "",
    modifier: Modifier = Modifier,
    viewModel: Format9ViewModel = hiltViewModel()
) {
    val podcasts = subTabs.filter { it.firestoreDocumentId.isNotBlank() }
        .distinctBy { it.firestoreDocumentId }
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val documentId = selectedDocumentId?.takeIf { selected ->
        podcasts.any { it.firestoreDocumentId == selected }
    } ?: podcasts.firstOrNull()?.firestoreDocumentId ?: fallbackDocumentId

    Column(modifier.fillMaxSize()) {
        if (podcasts.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(podcasts, key = { it.firestoreDocumentId }) { podcast ->
                    MenuItemChip(
                        text = podcast.title,
                        isSelected = podcast.firestoreDocumentId == documentId,
                        filledWhenSelected = true,
                        onClick = {
                            if (podcast.firestoreDocumentId != documentId) {
                                // Stop audio and clear the old script immediately, before recomposition.
                                viewModel.load(podcast.firestoreDocumentId)
                                selectedDocumentId = podcast.firestoreDocumentId
                            }
                        }
                    )
                }
            }
            HorizontalDivider()
        }
        if (documentId.isNotBlank()) {
            // Reset scroll position and open dialogs when switching to another story.
            key(documentId) {
                Format9ContainerScreen(
                    documentId = documentId,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    viewModel = viewModel
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No podcasts available")
            }
        }
    }
}
