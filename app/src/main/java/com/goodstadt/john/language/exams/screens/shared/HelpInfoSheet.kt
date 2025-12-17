package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.R // Import your resources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpInfoSheet(
    onDismiss: () -> Unit
) {
    val sheetBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = sheetBackgroundColor,
        contentColor = contentColor,

        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Drag row left or right", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    // ✅ CHANGE: Make TopBar transparent so it matches the new background
                    containerColor = Color.Transparent,
                    titleContentColor = contentColor,
                    actionIconContentColor = contentColor
                ),
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Intro
            Text(
                text = "Tips to get extra out of your vocab",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary
            )

            // ITEM 1
            HelpItem(
                imageRes = R.drawable.bottom_sheet_image_recall,
                text = "Drag row to the left to FOCUS on certain words. See the list on the 'Me' tab",
                        imageHeight = 90.dp
            )

            // ITEM 2
            HelpItem(
                imageRes = R.drawable.bottom_sheet_image_more,
                text = "Swipe Right to access extra info on the word e.g. pronunciation and extra sentences",
                imageHeight = 90.dp
            )

            // ITEM 3
            HelpItem(
                imageRes = R.drawable.bottom_sheet_image_more_definition,
                text = "",
                imageHeight = 300.dp
            )

            // Bottom Spacer
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun HelpItem(
    imageRes: Int,
    text: String,
    imageHeight: Dp = 100.dp // Default, but overrideable
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                // ✅ Use FillBounds or Crop + Top Alignment to fit taller images better
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight) // ✅ Dynamic Height
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}