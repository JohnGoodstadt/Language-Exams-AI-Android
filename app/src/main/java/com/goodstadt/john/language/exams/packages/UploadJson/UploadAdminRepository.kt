package com.goodstadt.john.language.exams.packages.UploadJson

/**
 * Administrative Firestore operations for the current flavour's quiz-content project (uploading /
 * verifying the bundled JSON). This is the abstraction that lives in `main`; the concrete implementation
 * lives ONLY in the `src/debug` source set (see FirestoreUploadAdminRepository), so the admin code is
 * compiled into every flavour's DEBUG variant (enDebug / deDebug / zhDebug) and nothing else - never
 * staging, never release.
 *
 * It is injected as `Optional<UploadAdminRepository>` (via [UploadAdminModule]'s @BindsOptionalOf):
 * present in any debug build, empty in staging/release.
 */
interface UploadAdminRepository {

    /**
     * Reads the `uploadDate` field from the document `/global/exam_sheets/sheets/<docName>` in the
     * current flavour's Firestore project (e.g. [docName] = "EnglishA1Vocab").
     * @return a human-readable date string, or null if the document has no such field / does not exist.
     */
    suspend fun readUploadDate(docName: String): Result<String?>

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
