// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/MainActivity.kt
package com.goodstadt.john.language.exams

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.GlobalBannerWrapper
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.packages.MainScreen.MainScreen
import com.goodstadt.john.language.exams.screens.shared.LoadingOverlay
import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme
import com.goodstadt.john.language.exams.packages.MainScreen.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bannerManager: BannerManager
    @Inject lateinit var globalLoadingManager: GlobalLoadingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
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
}
