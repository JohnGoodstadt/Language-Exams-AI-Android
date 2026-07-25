package com.goodstadt.john.language.exams.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Returns the height class based on current screen height in Dp.
 * COMPACT: Short screens (< 650dp) e.g., Nexus 5, zoomed display
 * MEDIUM: Standard screens (650dp - 850dp) e.g., Samsung A10, Pixel 4
 * EXPANDED: Tall screens (> 850dp) e.g., Pixel 8 Pro
 */
@Composable
fun rememberHeightClass(): HeightClass {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return when {
        screenHeightDp < 650 -> HeightClass.COMPACT
        screenHeightDp <= 850 -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }
}