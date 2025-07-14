// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/navigation/AppNavigation.kt
package com.goodstadt.john.language.exams.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.Filter3
import androidx.compose.ui.graphics.vector.ImageVector
import com.goodstadt.john.language.exams.R

sealed class IconResource {
    data class VectorIcon(val imageVector: ImageVector) : IconResource()
    data class DrawableIcon(@DrawableRes val id: Int) : IconResource()
}

sealed class Screen(
    val route: String,
    val title: String,
    val icon: IconResource
) {
    object Tab1 : Screen("tab1", "",IconResource.DrawableIcon(R.drawable.counter_1_24px))
    object Tab2 : Screen("tab2", "", IconResource.DrawableIcon(R.drawable.counter_2_24px))
    object Tab3 : Screen("tab3", "", IconResource.DrawableIcon(R.drawable.counter_3_24px))
    // Using a standard icon as a placeholder for the brain
    object Tab4 : Screen("tab4", "Focusing",IconResource.DrawableIcon(R.drawable.mindfulness_24px))
    object Tab5 : Screen("tab5", "Me",IconResource.DrawableIcon(R.drawable.person_24px))
}



val bottomNavItems = listOf(
        Screen.Tab1,
        Screen.Tab2,
        Screen.Tab3,
        Screen.Tab4,
        Screen.Tab5
)