package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Add these new imports for the modifiers
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ProgressDetailView(title: String) {
    // We'll apply the new modifiers to this root Column.
    Column(
        // --- THIS IS THE FIX ---
        modifier = Modifier
            .fillMaxWidth()
            // 1. Tell the Column to take up 90% of the available height.
            //    This leaves a small gap at the top, achieving the "almost full screen" look.
            .fillMaxHeight(fraction = 0.9f)
            // 2. (Recommended) Make the content scrollable in case it overflows this height.
            .verticalScroll(rememberScrollState())
            // 3. (Recommended) Add padding to account for the system navigation bar (the gesture bar at the bottom).
            .navigationBarsPadding()
            // 4. Add some overall padding for aesthetics.
            .padding(16.dp),
        // --- END FIX ---
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // This is a common UI pattern for a bottom sheet "drag handle".
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Details for:",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("(Content to be added later)")
    }
}