package com.goodstadt.john.language.exams.data.repository



import android.net.Uri
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Handles all interaction with Firebase Cloud Storage.
 * Also provides the Hashing logic for Unified Filenames.
 */
object FirebaseAudioService {

    private const val TAG = "FirebaseAudioService"

    // Point to your 'audio_cache' folder bucket
    // ID generation is local; initialize Firebase Storage only when a storage operation needs it.
    private val storageRef by lazy { Firebase.storage.reference.child("audio_cache") }

    // MARK: - 1. ID Generators (Hashing)

    /**
     * Memo cache for [generateContentID]. Content-IDs are a pure function of the sentence text, and the
     * same sentences are hashed over and over during list rendering (every recompose of every visible
     * row). Caching text -> contentID turns those repeat renders into a map lookup instead of a fresh
     * SHA-256 + Base64, which was the headline cost in the performance audit. Thread-safe because it is
     * read/written from multiple coroutine dispatchers. Keyed by the RAW text so the cached value is
     * always identical to a fresh call for that exact input. [MEMO_MAX] bounds it so transient strings
     * (e.g. live search) can never grow it without limit.
     */
    private val contentIdMemo = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val MEMO_MAX = 20_000

    /**
     * Generates the filename for Disk/Cloud storage.
     * Format: "VoicePrefix_SentenceStart_Hash(10).mp3"
     * Specific to a single voice (e.g. Google UK Male).
     * MUST match iOS generateUnifiedFilename exactly: the hash is of the TEXT ONLY (the voice is already in the
     * name), so a file either app uploads is found by the other.
     */
    fun generateUnifiedFilename(text: String, voiceName: String): String {
        val cleanText = text.trim()
        return "${voiceName}_${getSafePrefix(cleanText)}_${computeShortHash(cleanText)}.mp3"
    }

    /**
     * The name Android used before Nov 2026 (hash of "voice_text"), which iOS never finds. Only READ - as a
     * fallback for old local files and old cloud uploads - never written.
     */
    fun generateLegacyAndroidFilename(text: String, voiceName: String): String {
        val cleanText = text.trim()
        return "${voiceName}_${getSafePrefix(cleanText)}_${computeShortHash("${voiceName}_${cleanText}")}.mp3"
    }

    /**
     * Generates the ID for History/Stats.
     * Format: "CID_SentenceStart_Hash(10)"
     * Voice-Agnostic (Same ID for US Female and UK Male).
     */
    fun generateContentID(text: String): String {
        contentIdMemo[text]?.let { return it }

        val cleanText = text.trim()
        val prefix = getSafePrefix(cleanText)

        // Key includes TEXT ONLY
        val hash = computeShortHash(cleanText)

        val id = "CID_${prefix}_${hash}"
        // Guard against unbounded growth from transient inputs; the finite content set never trips this.
        if (contentIdMemo.size >= MEMO_MAX) contentIdMemo.clear()
        contentIdMemo[text] = id
        return id
    }

    // MARK: - Helper Logic

    // Compiled once; this ran on every content-ID/filename derivation.
    private val safePrefixRegex = Regex("[^a-zA-Z0-9]")

    private fun getSafePrefix(text: String): String {
        val charsToLeave = 20
        return text
            .take(charsToLeave)
            .replace(safePrefixRegex, "_")
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    // MessageDigest is not thread-safe; a per-thread instance avoids both the data race and the
    // repeated getInstance() allocation. digest() resets the instance after use.
    private val sha256 = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    private fun computeShortHash(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = sha256.get()!!.digest(bytes)

        // We only keep the first 10 hex chars, i.e. the first 5 bytes - so only hex-encode those
        // (not all 32) and build via a char lookup instead of 32 "%02x".format() allocations.
        val sb = StringBuilder(10)
        for (i in 0 until 5) {
            val b = digest[i].toInt() and 0xFF
            sb.append(HEX_CHARS[b ushr 4])
            sb.append(HEX_CHARS[b and 0x0F])
        }
        return sb.toString()
    }

    // MARK: - 2. Cloud Operations

    /**
     * Checks if the file exists in the cloud.
     * Returns true/false. Does not throw.
     */
    suspend fun checkFileExists(filename: String): Boolean {
        return try {
            storageRef.child(filename).metadata.await()
            true
        } catch (e: Exception) {
            // 404 Not Found or Network Error
            false
        }
    }

    /**
     * Downloads the file from Cloud to the specified Local File.
     * Throws exception if not found or network fails.
     */
    suspend fun downloadAudioOriginal(filename: String, destFile: File) {
        val fileRef = storageRef.child(filename)
        // Download directly to the file destination
        fileRef.getFile(destFile).await()
        Timber.tag(TAG).d("☁️ Downloaded: $filename to ${destFile.name}")
    }
    suspend fun downloadAudio(filename: String, destFile: File) : Boolean {
        val fileRef = storageRef.child(filename)

        return try {
            // Attempt download
            fileRef.getFile(destFile).await()
            Timber.tag(TAG).d("☁️ Downloaded: $filename to ${destFile.name}")

            true
        } catch (e: Exception) {
            // Check if it's a "Not Found" error (StorageException)
            // We expect this to happen often (Cache Miss), so we don't crash.
            val msg = e.message ?: ""
            if (msg.contains("Object does not exist") || msg.contains("404")) {
                // This is normal. It just means we need to use Google TTS.
                Timber.tag(TAG).d("☁️ Cloud cache miss: $filename") // Verbose log only
            } else {
                // Real network error
                Timber.tag(TAG).d("☁️ Cloud download failed: $filename")
            }
            false
        }
    }
    /**
     * Gets [text] in [voiceName] into [destFile] (its unified name) without TTS: first an old local file under the
     * legacy Android name (renamed, no network), then Cloud under the unified name, then Cloud under the legacy
     * Android name. Downloads via a ".part" file so a failed attempt never leaves a half-written mp3 under the
     * real name.
     */
    suspend fun downloadAudioAnyPlatform(text: String, voiceName: String, destFile: File): Boolean {
        if (adoptLegacyLocalFile(text, voiceName, destFile)) return true

        val part = File(destFile.path + ".part")
        val names = listOf(generateUnifiedFilename(text, voiceName), generateLegacyAndroidFilename(text, voiceName))
        for (cloudName in names) {
            if (downloadAudio(cloudName, part) && part.length() > 0 && part.renameTo(destFile)) return true
        }
        part.delete()
        return false
    }

    enum class CloudFetch { OK, NOT_FOUND, FAILED }

    /**
     * For offline downloads: like [downloadAudioAnyPlatform], but each cloud attempt gives up after [timeoutMs]
     * (with no network Firebase otherwise retries one file for up to 10 minutes) and the result says WHY it
     * failed, so the caller can tell "not in the cloud" from "no connection". A network failure on the
     * unified name skips the legacy name.
     */
    suspend fun downloadAudioForOffline(text: String, voiceName: String, destFile: File, timeoutMs: Long): CloudFetch {
        if (adoptLegacyLocalFile(text, voiceName, destFile)) return CloudFetch.OK

        val part = File(destFile.path + ".part")
        var result = CloudFetch.NOT_FOUND
        for (cloudName in listOf(generateUnifiedFilename(text, voiceName), generateLegacyAndroidFilename(text, voiceName))) {
            result = downloadWithTimeout(cloudName, part, timeoutMs)
            if (result == CloudFetch.OK) {
                if (part.length() > 0 && part.renameTo(destFile)) return CloudFetch.OK
                result = CloudFetch.FAILED
            }
            if (result == CloudFetch.FAILED) break
        }
        part.delete()
        return result
    }

    /** One cloud download that is cancelled (not left retrying) on timeout or when the caller is cancelled. */
    private suspend fun downloadWithTimeout(filename: String, destFile: File, timeoutMs: Long): CloudFetch {
        val task = storageRef.child(filename).getFile(destFile)
        return try {
            withTimeout(timeoutMs) { task.await() }
            CloudFetch.OK
        } catch (e: TimeoutCancellationException) {
            task.cancel()
            Timber.tag(TAG).d("☁️ Cloud download timed out: $filename")
            CloudFetch.FAILED
        } catch (e: CancellationException) {
            task.cancel()
            throw e
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) CloudFetch.NOT_FOUND else CloudFetch.FAILED
        } catch (e: Exception) {
            CloudFetch.FAILED
        }
    }

    /**
     * If [destFile] is missing but the same audio is on disk under the legacy Android name (cached before the
     * naming matched iOS), rename it to [destFile]. No network. Returns true if [destFile] now exists.
     */
    fun adoptLegacyLocalFile(text: String, voiceName: String, destFile: File): Boolean {
        if (destFile.exists()) return true
        val legacyLocal = File(destFile.parentFile, generateLegacyAndroidFilename(text, voiceName))
        if (legacyLocal.exists() && legacyLocal.length() > 0 && legacyLocal.renameTo(destFile)) {
            Timber.tag(TAG).d("Renamed legacy local file to ${destFile.name}")
            return true
        }
        return false
    }

    /**
     * Uploads a file to Cloud in the background (Fire & Forget).
     * Adds custom metadata so you can read the text in the Console.
     */
    fun uploadAudio(localFile: File, filename: String, text: String) {
        // Use IO Scope for background upload
        CoroutineScope(Dispatchers.IO).launch {
            val fileRef = storageRef.child(filename)

            try {
                // Check if exists first to save bandwidth?
                // Usually cheaper to just try overwrite or rely on client-side logic.

                val metadata = StorageMetadata.Builder()
                    .setContentType("audio/mpeg")
                    .setCustomMetadata("sentence_text", text)
                    .setCustomMetadata("uploaded_platform", "Android")
                    .build()

                fileRef.putFile(Uri.fromFile(localFile), metadata).await()
                Timber.tag(TAG).v("☁️ Uploaded to cloud storage:$filename")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to upload to cloud storage:$filename")
            }
        }
    }
}