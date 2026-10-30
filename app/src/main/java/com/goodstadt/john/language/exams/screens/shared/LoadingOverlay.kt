package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp


@Composable
fun LoadingOverlay() {
    // 1. The Container (Invisible, full screen, allows clicks through)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp), // Push down to avoid Status Bar overlap
        contentAlignment = Alignment.TopCenter
    ) {
        // 2. The Indicator Card (Visible, small)
        Surface(
            shape = RoundedCornerShape(12.dp),
            // Use secondaryContainer for a nice discrete background color
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(60.dp) // Small square size
        ) {
            Box(contentAlignment = Alignment.Center) {
                LaunchSpinner(size = 40.dp) // app spinner, small enough for the card
            }
        }
    }
}
/**
 * Spinner shown OVER the current content while the next sheet loads, so switching sheets (verb / sub-tab
 * picker) never blanks the screen. Swallows taps so the outgoing sheet can't be played mid-switch.
 */
@Composable
fun SheetSwitchingOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // No card behind it: the same full-size Orbit on a clear background as a sheet's first load.
        LaunchSpinner()
    }
}
