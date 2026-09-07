package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.utils.AppSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** The languages this app translates between (extend later if needed). */
enum class TranslateLang(val code: String, val display: String) {
    GERMAN("de", "German"),
    ENGLISH("en", "English")
}

/**
 * The single place that performs translations. Calls the Google Cloud Translation API (v2 REST) with the
 * per-flavour Google Cloud API key ([BuildConfig.GOOGLE_API_KEY]) - the same key the flavour uses for
 * Text-to-Speech, from that language's Google Cloud project; the "Cloud Translation API" must be enabled
 * on that project. Mirrors [com.goodstadt.john.language.exams.data.api.GoogleCloudTTS].
 *
 * Kept UI-agnostic so it can back the Translate sheet now and anything else later.
 */
@Singleton
class TranslationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val apiKey = BuildConfig.GOOGLE_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TranslateRequest(
        val q: String,
        val source: String,
        val target: String,
        val format: String = "text"
    )

    @Serializable private data class TranslateResponse(val data: TranslateData = TranslateData())
    @Serializable private data class TranslateData(val translations: List<Translation> = emptyList())
    @Serializable private data class Translation(val translatedText: String = "")

    /**
     * Translate [text] from [source] to [target]. Returns the translated string on success (empty for blank
     * input), or a failure with a readable message.
     */
    suspend fun translate(
        text: String,
        source: TranslateLang,
        target: TranslateLang
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")

        val urlString = "https://translation.googleapis.com/language/translate/v2?key=$apiKey"
        var connection: HttpURLConnection? = null
        try {
            val requestBody = json.encodeToString(
                TranslateRequest(q = text, source = source.code, target = target.code)
            )

            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // Identify the app so an API key restricted to "Android apps" accepts the call. Harmless if
                // the key isn't app-restricted.
                setRequestProperty("X-Android-Package", context.packageName)
                AppSignature.certSha1NoColons(context)?.let { setRequestProperty("X-Android-Cert", it) }
                doOutput = true
            }
            OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = json.decodeFromString<TranslateResponse>(responseBody)
                val translated = parsed.data.translations.firstOrNull()?.translatedText.orEmpty()
                Result.success(htmlUnescape(translated))
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Unknown error"
                Timber.e("Translate API Error: $responseCode - $errorBody")
                Result.failure(RuntimeException("Translation failed ($responseCode)"))
            }
        } catch (e: Exception) {
            Timber.e(e, "TranslationRepository.translate failed")
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /** The Translation API HTML-escapes a few characters even in text format; undo the common ones. */
    private fun htmlUnescape(s: String): String =
        s.replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
