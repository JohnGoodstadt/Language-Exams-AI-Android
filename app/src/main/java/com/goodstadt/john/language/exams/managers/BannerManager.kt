package com.goodstadt.john.language.exams.managers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.goodstadt.john.language.exams.screens.shared.AchievementBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class BannerManager @Inject constructor() {
    private val _bannerState = MutableStateFlow<BannerData?>(null)
    val bannerState = _bannerState.asStateFlow()

    fun showBanner(title: String, subtitle: String, seconds:Int = 5) {
        _bannerState.value = BannerData(title, subtitle, seconds)
    }

    fun dismiss() {
        _bannerState.value = null
    }
}

data class BannerData(val title: String, val subtitle: String,val seconds:Int)

@Composable
fun GlobalBannerWrapper(
    bannerManager: BannerManager,
    globalLoadingManager: GlobalLoadingManager,
    content: @Composable () -> Unit
) {
    val state by bannerManager.bannerState.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Your actual App Screens (NavHost, etc.)
        content()

        // 2. The Global Banner Overlay
        AnimatedVisibility(
            visible = state != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).zIndex(99f)
        ) {
            state?.let { data ->
                LaunchedEffect(data) {
                    globalLoadingManager.playSuccessSound(context)
                    val milliSecondsDelay = data.seconds.toLong() * 1000L
                    delay(milliSecondsDelay)
                    bannerManager.dismiss()
                }

                AchievementBanner(
                    isVisible = true,
                    title = data.title,
                    subtitle = data.subtitle,
                    onDismiss = { bannerManager.dismiss() }
                )
            }
        }
    }
}