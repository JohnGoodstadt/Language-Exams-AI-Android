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

    /**
     * Uploads a sheet's JSON to `/global/exam_sheets/sheets/<docName>`.
     * All sheets hang off the `global/exam_sheets/sheets` collection; [docName] is the sheet name
     * (e.g. "GermanA1Vocab").
     * @param json the file's raw JSON content.
     */
    suspend fun uploadSheet(docName: String, json: String): Result<Unit>

    /**
     * @return whether the top-level sheet document already exists. Used to block a second upload
     * while there is no Delete yet.
     */
    suspend fun sheetExists(docName: String): Result<Boolean>

    /**
     * Reads the sheet document at `/global/exam_sheets/sheets/<docName>` and confirms it exists /
     * decodes, to verify a previous upload.
     * @return
     *  - `Result.success(summary)` when the sheet exists and its subcollections read cleanly;
     *  - `Result.success(null)` when the top-level document is simply NOT present (never uploaded) -
     *    the caller shows this as a blank flag, NOT an error;
     *  - `Result.failure(e)` for a real error (permission denied, bad format at a lower level, …).
     */
    suspend fun readSheet(docName: String): Result<String?>
}
