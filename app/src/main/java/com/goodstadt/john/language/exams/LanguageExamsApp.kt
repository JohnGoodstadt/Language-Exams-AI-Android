// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/LanguageExamsApp.kt
package com.goodstadt.john.language.exams

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber


import android.util.Log
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import javax.inject.Inject

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

    @Inject
    lateinit var rateLimiter: SimpleRateLimiter

    // 2. Add the onCreate method
    override fun onCreate() {
        super.onCreate() // Always call the parent class's method first

        // 3. Add the Firebase initialization line. This will fix the crash.
        FirebaseApp.initializeApp(this)

        setupAppDependencies()

      //  initializeRateLimiter()



    }
    private fun setupAppDependencies() {
        // 2. Configure the injected rateLimiter instance.
        //    By the time onCreate() runs, Hilt has already injected the 'rateLimiter' property.
        if (BuildConfig.TEST_RATE_LIMITING) {
            // We need to move the logic from the old 'updateToMinimalScheme' here.
            rateLimiter.hourlyLimit = 10
            rateLimiter.dailyLimit = 20
            rateLimiter.schemeID = "MinimalScheme"
            rateLimiter.name = "Minimal Test Rate Limiter"
            rateLimiter.description = "Test Rate Limiter using smallest values"
        }

        // 3. Timber setup is still correct here.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }
}