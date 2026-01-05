// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/LanguageExamsApp.kt
package com.goodstadt.john.language.exams

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber


import android.util.Log
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.utils.logging.FaultTree
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

private class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log WARN, ERROR, WTF
        if (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT) {
            Log.println(priority, tag, message)
            // If there’s an attached exception, log it too
            t?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
        }

//        if (priority == Log.WARN || priority == Log.ERROR) {
//
//            val crashlytics = FirebaseCrashlytics.getInstance()
//
//            // Add the log message as a breadcrumb to the crash report
//            crashlytics.log("$tag: $message")
//
//            // If an exception (Throwable 't') was also logged, report it as a non-fatal crash.
//            if (t != null) {
//                crashlytics.recordException(t)
//            }
//        }
    }
}


@HiltAndroidApp
class LanguageExamsApp : Application() {

    @Inject
    lateinit var rateLimiter: SimpleRateLimiter

    @Inject
    lateinit var firestore: FirebaseFirestore

    // 2. Add the onCreate method
    override fun onCreate() {
        super.onCreate() // Always call the parent class's method first

        // 3. Add the Firebase initialization line. This will fix the crash.
        FirebaseApp.initializeApp(this)

        // 2. Install App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        firebaseAppCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                // Use Debug Provider for Emulator/Local builds
                DebugAppCheckProviderFactory.getInstance()
            } else {
                // Use Play Integrity for Release builds
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        setupAppDependencies()

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


        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Crashlytics collection is DISABLED for this debug build.")
        } else {
            Timber.plant(FaultTree(firestore)) //write to fs on Fatal Error
            Timber.d("Crashlytics collection is ENABLED for this release build.")
        }

        // ✅ BEST PRACTICE: Only enable automatic crash reporting for release builds.
        // This prevents your development crashes from polluting your Crashlytics dashboard.
//        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        if (BuildConfig.DEBUG) {
            Firebase.analytics.setUserProperty("is_developer", "false")
        }
    }
}