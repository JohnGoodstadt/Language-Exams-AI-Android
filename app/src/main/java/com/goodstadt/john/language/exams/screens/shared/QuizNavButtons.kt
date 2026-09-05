package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.buttonColor

/**
 * Shared nav-row buttons for the quiz screens (Vocab, Grammar, Usage), so the Info ("i") and Auto-advance
 * ("A") buttons look identical everywhere: a single bold letter in a thin circle.
 */

/** A single letter drawn in a thin circle - the shared look for the Info and Auto-advance buttons. */
@Composable
fun LetterInCircle(letter: String, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(2.dp, tint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Auto-advance toggle: an "A" in a circle. Grey = off, blue = on. When on, the quiz automatically moves to
 * the next question after a correct answer (once its audio has finished), saving the user a tap.
 */
@Composable
fun AutoAdvanceToggleButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) buttonColor else Color.Gray // blue when on, grey when off
    IconButton(onClick = onClick) {
        LetterInCircle(letter = "A", tint = tint)
    }
}

/**
 * Info button drawn as an "i" in a circle, matching the Auto-advance "A". Grey when disabled, blue
 * otherwise. Used on the quiz screens in place of the Material info icon so the two buttons match exactly.
 */
@Composable
fun InfoCircleButton(infoDisabled: Boolean, onClick: () -> Unit) {
    val tint = if (infoDisabled) Color.Gray else buttonColor
    IconButton(onClick = onClick, enabled = !infoDisabled) {
        LetterInCircle(letter = "i", tint = tint)
    }
}
