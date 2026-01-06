package com.goodstadt.john.language.exams.screens.reference.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.nonSelectedBackground
import com.goodstadt.john.language.exams.ui.theme.selectedBackground

//TODO: Should be shared amongst screens
@Composable
fun HorizontalLevelPicker(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        //horizontalArrangement = Arrangement.spacedBy(1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option
            Button(
                onClick = { onOptionSelected(option) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) selectedBackground else nonSelectedBackground, // Dark grey for selected, light grey for unselected
                    contentColor = Color.White // White text for all buttons
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 1.dp)
            ) {
                Text(
                    text = option,
                    color =  if (isSelected) accentColor else Color.LightGray,
                    maxLines = 1,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}