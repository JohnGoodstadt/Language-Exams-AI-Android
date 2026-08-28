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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.goodstadt.john.language.exams.packages.MainScreen.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bannerManager: BannerManager
    @Inject lateinit var globalLoadingManager: GlobalLoadingManager

    // Activity-scoped: the same instance the composables get via hiltViewModel(activity).
    private val navViewModel: NavigationViewModel by viewModels()

    companion object {
        const val EXTRA_NAV_TARGET = "nav_target"
        const val NAV_SAVED = "me_saved"
        private const val ME_TAB_INDEX = 4 // Tab5 = the "Me" tab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNavIntent(intent) // e.g. launched from a practice-reminder notification

        setContent {
            // Ask for notification permission (Android 13+) so practice reminders can appear.
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val mainViewModel: MainViewModel = hiltViewModel()
            mainViewModel.registerLifecycleObserver(this.lifecycle) // Register the observer with the activity's lifecycle

            val isLoading by mainViewModel.isGlobalLoading.collectAsStateWithLifecycle()

            LanguageExamsAITheme(darkTheme = true) {
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
