package com.goodstadt.john.language.exams.screens.utils

// In a new file, e.g., utils/AppLifecycleObserver.kt

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.util.Log

// Your observer can take repositories as dependencies so it can do work.
class AppLifecycleObserver(
    private val onAppBackgrounded: () -> Unit
) : DefaultLifecycleObserver {

    // Called when the app comes to the foreground
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d("AppLifecycle", "App came to FOREGROUND.")
        // You can add logic here if needed.
    }

    // Called when the app goes to the background
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d("AppLifecycle", "App went to BACKGROUND. Flushing stats...")
        // This is where you trigger the save/flush operation.
        onAppBackgrounded()
    }
}