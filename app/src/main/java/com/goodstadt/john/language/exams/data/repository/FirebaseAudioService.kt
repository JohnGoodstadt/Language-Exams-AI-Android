package com.goodstadt.john.language.exams.data.repository



import android.net.Uri
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val storageRef = Firebase.storage.reference.child("audio_cache")

    // MARK: - 1. ID Generators (Hashing)

    /**
     * Generates the filename for Disk/Cloud storage.
     * Format: "VoicePrefix_SentenceStart_Hash(10).mp3"
     * Specific to a single voice (e.g. Google UK Male).
     */
    fun generateUnifiedFilename(text: String, voiceName: String): String {
        val cleanText = text.trim()
        val prefix = getSafePrefix(cleanText)

        // Key includes Voice + Text
        val key = "${voiceName}_${cleanText}"
        val hash = computeShortHash(key)

        return "${voiceName}_${prefix}_${hash}.mp3"
    }

    /**
     * Generates the ID for History/Stats.
     * Format: "CID_SentenceStart_Hash(10)"
     * Voice-Agnostic (Same ID for US Female and UK Male).
     */
    fun generateContentID(text: String): String {
        val cleanText = text.trim()
        val prefix = getSafePrefix(cleanText)

        // Key includes TEXT ONLY
        val hash = computeShortHash(cleanText)

        return "CID_${prefix}_${hash}"
    }

    // MARK: - Helper Logic

    private fun getSafePrefix(text: String): String {
        val charsToLeave = 20
        return text
            .take(charsToLeave)
            .replace(Regex("[^a-zA-Z0-9]"), "_")
    }

    private fun computeShortHash(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)

        // Convert to Hex and take first 10 chars
        return digest.fold("") { str, it -> str + "%02x".format(it) }
            .take(10)
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