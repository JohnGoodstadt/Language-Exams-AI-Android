// <project-root>/app/src/androidTest/java/com/goodstadt/john/language/exams/CustomTestRunner.kt
package com.goodstadt.john.language.exams

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// A custom runner to set up the Hilt Test Application
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}