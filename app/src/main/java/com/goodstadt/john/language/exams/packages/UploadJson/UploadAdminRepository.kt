package com.goodstadt.john.language.exams.packages.UploadJson

/**
 * Administrative Firestore operations for the German quiz-content project (uploading / verifying the
 * bundled JSON). This is the abstraction that lives in `main`; the concrete implementation lives ONLY
 * in the `src/deDebug` source set (see FirestoreUploadAdminRepository), so the admin code is compiled
 * into the German DEBUG variant and nothing else - never release, never the `en`/`zh` flavours.
 *
 * It is injected as `Optional<UploadAdminRepository>` (via [UploadAdminModule]'s @BindsOptionalOf):
 * present in `deDebug`, empty everywhere else.
 */
interface UploadAdminRepository {

    /**
     * Reads the `uploadDate` field from the document
     * `/global/exam_sheets/sheets/GermanA1Vocab` in the German Firestore project.
     * @return a human-readable date string, or null if the document has no such field / does not exist.
     */
    suspend fun readUploadDate(): Result<String?>
}
