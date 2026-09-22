package com.goodstadt.john.language.exams.packages.Translate

import android.content.Context
import com.goodstadt.john.language.exams.data.repository.TranslateLang
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny, self-contained local cache for the Translate sheet (§23). It remembers each translation keyed by
 * **source|target|sentence** so a repeat lookup is instant and — crucially — **works offline**, without a
 * second paid Google Translation API call.
 *
 * Deliberately isolated to the Translate feature: only [TranslateViewModel] uses it, and it does NOT touch
 * the API layer ([com.goodstadt.john.language.exams.data.repository.TranslationRepository], which stays a
 * pure network call). Persistence is a small local JSON blob (`translation_cache.json` in filesDir),
 * per install. If we later want to share hits across all users, the same key can be written to Firestore
 * without changing any caller — the VM would just consult a remote store after this local one.
 *
 * In-memory it's an access-ordered map (LRU): the least-recently-used entry is dropped once the cache
 * exceeds [MAX_ENTRIES], so it can't grow without bound. All access is serialised by a [Mutex] and runs on
 * the IO dispatcher.
 */
@Singleton
class TranslationCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false

    // accessOrder = true -> get()/put() move an entry to the most-recent end, so the eldest key (front of
    // the iteration order) is the least-recently-used and the first evicted.
    private val entries = LinkedHashMap<String, String>(0, 0.75f, true)

    /** The cached translation for this key, or null on a miss. Instant, and works with no network. */
    suspend fun get(source: TranslateLang, target: TranslateLang, text: String): String? =
        withContext(Dispatchers.IO) {
            val key = keyFor(source, target, text) ?: return@withContext null
            mutex.withLock {
                ensureLoaded()
                entries[key]
            }
        }

    /** Remember a successful translation. Blank input/output is ignored. Write-through to disk. */
    suspend fun put(source: TranslateLang, target: TranslateLang, text: String, translation: String) {
        if (translation.isBlank()) return
        val key = keyFor(source, target, text) ?: return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded()
                entries[key] = translation
                while (entries.size > MAX_ENTRIES) {
                    val eldest = entries.keys.iterator().next()
                    entries.remove(eldest)
                }
                save()
            }
        }
    }

    /** Load the blob into memory once, on first access. Must be called while holding [mutex]. */
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (file.exists()) {
                entries.putAll(json.decodeFromString<Map<String, String>>(file.readText()))
            }
        } catch (e: Exception) {
            Timber.w(e, "TranslationCache: failed to read $FILE_NAME; starting empty")
        }
    }

    /** Persist the whole map. Must be called while holding [mutex]. */
    private fun save() {
        try {
            file.writeText(json.encodeToString<Map<String, String>>(entries))
        } catch (e: Exception) {
            Timber.w(e, "TranslationCache: failed to write $FILE_NAME")
        }
    }

    /** Key = "src|tgt|trimmed sentence". Case is preserved (it can change a translation). Null if blank. */
    private fun keyFor(source: TranslateLang, target: TranslateLang, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return "${source.code}|${target.code}|$trimmed"
    }

    companion object {
        private const val FILE_NAME = "translation_cache.json"
        private const val MAX_ENTRIES = 3000
    }
}
