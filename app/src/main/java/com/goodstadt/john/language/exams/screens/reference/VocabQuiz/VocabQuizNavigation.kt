package com.goodstadt.john.language.exams.screens.reference.VocabQuiz

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun VocabQuizNavigation() {
    val navController = rememberNavController()

    // In a real app, these would come from your Hilt ViewModel
    val mockCategories = remember { getMockCategories() }

    NavHost(
        navController = navController,
        startDestination = QuizRoute.Dashboard.route
    ) {
        // --- SCREEN 1: DASHBOARD ---
        composable(QuizRoute.Dashboard.route) {
            VocabQuizDashboard(
                weakWordCount = 12, // From DB
                categories = mockCategories,
                onCategoryClick = { category ->
                    // Navigate to detail and pass the ID
                    navController.navigate(QuizRoute.Detail.createRoute(category.id))
                },
                onWeakWordsClick = {
                    // Logic for Smart Review
                }
            )
        }

        // --- SCREEN 2: CATEGORY DETAIL ---
        composable(
            route = QuizRoute.Detail.route,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val catId = backStackEntry.arguments?.getString("categoryId") ?: ""
            val category = mockCategories.find { it.id == catId }

            // Generate mock sets for this specific category
            val sets = remember(catId) { getMockSetsForCategory(catId) }

            CategoryDetailScreen(
                categoryTitle = category?.title ?: "Category",
                quizSets = sets,
                onBackClick = { navController.popBackStack() },
                onStartSet = { selectedSet ->
                    // Navigate to actual Quiz Screen (Next Step)
                    println("Starting ${selectedSet.setTitle}")
                }
            )
        }
    }
}
private fun getMockCategories() = listOf(
    QuizCategory("edu", "Education", Icons.Default.School, 40, 12),
    QuizCategory("soc", "Social", Icons.Default.Groups, 30, 30),
    QuizCategory("work", "Work", Icons.Default.Work, 50, 0),
    QuizCategory("trav", "Travel", Icons.Default.Flight, 20, 5)
)

private fun getMockSetsForCategory(catId: String) = listOf(
    QuizSet("1", "Set 1 of 3", "Ambitious, Reliable...", 10, 10),
    QuizSet("2", "Set 2 of 3", "Sustainable, Capable...", 4, 10),
    QuizSet("3", "Set 3 of 3", "Efficient, Logic...", 0, 10)
)