// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/LanguageExamsApp.kt
package com.goodstadt.john.language.exams

import android.app.Application
import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LanguageExamsApp : Application() {

    // 2. Add the onCreate method
    override fun onCreate() {
        super.onCreate() // Always call the parent class's method first

        // 3. Add the Firebase initialization line. This will fix the crash.
        FirebaseApp.initializeApp(this)

        initializeRateLimiter()
    }
    private fun initializeRateLimiter() {
        RateLimiterManager.initialize(this)
        if (BuildConfig.TEST_RATE_LIMITING) {
            RateLimiterManager.updateToMinimalScheme()
        }
    }
}