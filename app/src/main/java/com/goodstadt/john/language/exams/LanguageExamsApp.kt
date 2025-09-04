// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/LanguageExamsApp.kt
package com.goodstadt.john.language.exams

import android.app.Application
import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber


import android.util.Log

private class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log WARN, ERROR, WTF
        if (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT) {
            Log.println(priority, tag, message)
            // If there’s an attached exception, log it too
            t?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
        }
    }
}


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
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }else{
            Timber.plant(ReleaseTree())
        }
    }
}