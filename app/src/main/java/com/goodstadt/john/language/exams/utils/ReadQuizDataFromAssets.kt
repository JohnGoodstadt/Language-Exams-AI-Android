package com.goodstadt.john.language.exams.utils

import android.content.Context
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import kotlinx.serialization.json.Json
import timber.log.Timber

fun readTestMyselfDataFromAssets(context: Context, fileName: String): TestMyselfListRoot? {
    return try {
     //   Timber.v("reading json: $fileName")

        val jsonParser = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val jsonString = context.assets.open("Quizzes/$fileName")
            .bufferedReader()
            .use { it.readText() }

        return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}