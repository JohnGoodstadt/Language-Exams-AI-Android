package com.goodstadt.john.language.exams.utils

import android.content.Context
import com.goodstadt.john.language.exams.models.Format7or10File
import kotlinx.serialization.json.Json

fun readFormat7or10fDataFromAssets(context: Context, fileName: String): Format7or10File? {
    return try {
     //   Timber.v("reading json: $fileName")

        val jsonParser = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val jsonString = context.assets.open("Quizzes/$fileName")
            .bufferedReader()
            .use { it.readText() }

        return jsonParser.decodeFromString<Format7or10File>(jsonString)

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}