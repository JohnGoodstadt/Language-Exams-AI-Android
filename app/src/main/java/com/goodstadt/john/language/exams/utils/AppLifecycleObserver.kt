package com.goodstadt.john.language.exams.utils

// In a new file, e.g., utils/AppLifecycleObserver.kt

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import timber.log.Timber

// Your observer can take repositories as dependencies so it can do work.
class AppLifecycleObserver(
    private val onAppBackgrounded: () -> Unit,
    private val onAppForeground: () -> Unit
) : DefaultLifecycleObserver {

    // Called when the app comes to the foreground
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Timber.d("App came to FOREGROUND.")
        // You can add logic here if needed.
        onAppForeground()
    }

    // Called when the app goes to the background
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Timber.d("App went to BACKGROUND. Flushing stats...")
        // This is where you trigger the save/flush operation.
        onAppBackgrounded()
    }
}