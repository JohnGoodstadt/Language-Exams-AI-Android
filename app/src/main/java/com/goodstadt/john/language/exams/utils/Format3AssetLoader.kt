package com.goodstadt.john.language.exams.utils


import android.content.Context
import com.goodstadt.john.language.exams.models.Format3File
import kotlinx.serialization.json.Json
import java.io.IOException

class Format3AssetLoader(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    @Throws(IOException::class)
    fun load(context: Context, filename: String): Format3File {
        val assetName = if (filename.endsWith(".json")) filename else "$filename.json"
        val rawJson = context.assets.open(assetName).bufferedReader().use { it.readText() }
        return json.decodeFromString(Format3File.serializer(), rawJson)
    }
}
