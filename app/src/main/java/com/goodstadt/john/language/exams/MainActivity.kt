// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/MainActivity.kt
package com.goodstadt.john.language.exams

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.goodstadt.john.language.exams.screens.MainScreen
import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
//        enableEdgeToEdge(
//            navigationBarStyle = SystemBarStyle.dark(
//                Color.argb(
//                    0x80,
//                    0x1b,
//                    0x1b,
//                    0x1b
//                )
//            )
//        )
        setContent {
            LanguageExamsAITheme(darkTheme = true) {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}