// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/MainActivity.kt
package com.goodstadt.john.language.exams

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.goodstadt.john.language.exams.ui.LocalIsExpanded
import com.goodstadt.john.language.exams.ui.isExpanded
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.GlobalBannerWrapper
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.packages.MainScreen.MainScreen
import com.goodstadt.john.language.exams.packages.reference.NavigationViewModel
import com.goodstadt.john.language.exams.screens.shared.LoadingOverlay
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme
import com.goodstadt.john.language.exams.ui.theme.ThemeMode
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.packages.MainScreen.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bannerManager: BannerManager
    @Inject lateinit var globalLoadingManager: GlobalLoadingManager
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    // Activity-scoped: the same instance the composables get via hiltViewModel(activity).
    private val navViewModel: NavigationViewModel by viewModels()

    companion object {
        const val EXTRA_NAV_TARGET = "nav_target"
        const val NAV_SAVED = "me_saved"
        private const val ME_TAB_INDEX = 4 // Tab5 = the "Me" tab
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNavIntent(intent) // e.g. launched from a practice-reminder notification

        setContent {
            // Notification permission is requested lazily, at first use (saving a word for practice on the
            // vocab tabs), rather than up front here - see CategoryTabScreen's swipe-to-save.

            val mainViewModel: MainViewModel = hiltViewModel()
            mainViewModel.registerLifecycleObserver(this.lifecycle) // Register the observer with the activity's lifecycle

            val isLoading by mainViewModel.isGlobalLoading.collectAsStateWithLifecycle()

            // Tablet adaptation (V4_tablet): compute the window size class once here and publish
            // `isExpanded` via CompositionLocal so any screen can gate its tablet layout on it.
            // Window-based, so it recomposes on resize / split-screen / foldable fold.
            val windowSizeClass = calculateWindowSizeClass(this)
            val isExpanded = windowSizeClass.widthSizeClass.isExpanded()

            // Appearance (Phase 3): follow the user's Settings choice, defaulting to System. Read the
            // persisted mode here at the app root so a change in Settings re-themes the whole app.
            val themeModeName by userPreferencesRepository.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM.name)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (ThemeMode.fromName(themeModeName)) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            LanguageExamsAITheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalIsExpanded provides isExpanded) {
                    Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                    ) {
                        GlobalBannerWrapper(bannerManager = bannerManager,globalLoadingManager = globalLoadingManager) {
                            MainScreen()
                            if (isLoading) {
                                LoadingOverlay()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent(intent) // app already running - deep link into Me > Saved
    }

    /** If launched/reopened from a practice reminder, route to the Me tab's "Saved" sub-tab. */
    private fun handleNavIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_NAV_TARGET) == NAV_SAVED) {
            navViewModel.pendingMeSubTabTitle = MeScreen.Saved.title
            navViewModel.requestNavigation(SideQuestNavTarget.MainTab(ME_TAB_INDEX))
        }
    }
}
