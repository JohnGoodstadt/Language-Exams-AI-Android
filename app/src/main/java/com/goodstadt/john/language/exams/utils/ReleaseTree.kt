package com.goodstadt.john.language.exams.utils

import android.util.Log
import timber.log.Timber

class ReleaseTreeObsolete : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log WARN, ERROR, WTF
        if (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT) {
            Log.println(priority, tag, message)

            // If there’s an attached exception, log it too
            t?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
        }
    }
}
