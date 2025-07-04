// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/navigation/AppNavigation.kt
package com.goodstadt.john.language.exams.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.Filter3
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Tab1 : Screen("tab1", "Tab 1", Icons.Outlined.Filter1)
    object Tab2 : Screen("tab2", "Tab 2", Icons.Outlined.Filter2)
    object Tab3 : Screen("tab3", "Tab 3", Icons.Outlined.Filter3)
    // Using a standard icon as a placeholder for the brain
    object Tab4 : Screen("tab4", "Focussing", Icons.Default.Psychology)
    object Tab5 : Screen("tab5", "Me", Icons.Default.Face)
}

val bottomNavItems = listOf(
        Screen.Tab1,
        Screen.Tab2,
        Screen.Tab3,
        Screen.Tab4,
        Screen.Tab5
)