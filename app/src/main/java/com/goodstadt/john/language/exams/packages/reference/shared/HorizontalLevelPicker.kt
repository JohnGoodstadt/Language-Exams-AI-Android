package com.goodstadt.john.language.exams.packages.reference.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.ui.theme.LocalIsDarkTheme

//TODO: Should be shared amongst screens
@Composable
fun HorizontalLevelPicker(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    lockedOptions: Set<String> = emptySet(),
    // Light mode only: show the selected option as a standard button (button blue, white text).
    filledWhenSelected: Boolean = false,
    // Light mode only, with filledWhenSelected (e.g. Readiness Audit, iOS-style): unlocked options are
    // black text on the off-white grouped page colour; locked ones dark grey text on white.
    lightUnselectedOnPageColor: Boolean = false,
    fontSize: TextUnit = 12.sp
) {
    val filled = filledWhenSelected && !LocalIsDarkTheme.current
    val pageStyle = filled && lightUnselectedOnPageColor
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
            val isLocked = option in lockedOptions
            Button(
                onClick = { onOptionSelected(option) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isSelected && filled -> LocalExtendedColors.current.buttonBlue
                        isSelected -> LocalExtendedColors.current.rowSelected // adaptive: selected more prominent
                        pageStyle && isLocked -> LocalExtendedColors.current.groupedPanel
                        pageStyle -> LocalExtendedColors.current.groupedPage
                        else -> LocalExtendedColors.current.rowUnselected
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                // Material3 Buttons default to 24.dp horizontal content padding, which wastes a lot of
                // width and pushes the last chip off screen for long labels (e.g. German "Fortgeschritten").
                // Tighten the internal padding (and the inter-chip gap) so all chips fit on one row.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.padding(horizontal = 1.dp)
            ) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked - complete the previous level first",
                        tint = if (pageStyle) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = option,
                    color = when {
                        isSelected && filled -> Color.White
                        pageStyle && isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
                        pageStyle -> MaterialTheme.colorScheme.onSurface
                        isLocked -> Color.Gray
                        isSelected -> LocalExtendedColors.current.screenAccent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    fontSize = fontSize,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}