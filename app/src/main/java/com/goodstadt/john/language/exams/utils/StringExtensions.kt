// In utils/StringExtensions.kt
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun String.urlEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
}
fun String.removeContentInBracketsAndTrim(): String {
    val regex = "\\s*\\([^)]*\\)".toRegex() // Matches optional spaces before and any characters within parentheses
    return this.replace(regex, "").trimEnd()
}
/**
 * Cleans a string to make it a valid Firestore field name by replacing
 * prohibited characters with an underscore.
 *
 * This function enforces the following Firestore rules:
 * - Replaces `*`, `/`, `[`, `]`, and `.` with `_`.
 * - Ensures the field is not empty, returning "_" if it is.
 * - Note: It does not handle the `__.*__` pattern or byte length, as these
 *   are less common for programmatically generated field names.
 *
 * @receiver The String to be sanitized.
 * @return A sanitized string that is safe to use as a Firestore field name.
 */
fun String.sanitizedForFirestore(): String {
    // 1. Define a regular expression that matches any of the invalid characters.
    //    The square brackets `[]` create a "character set" for the regex.
    //    Inside the set, we need to escape the special characters `[` and `]`.
    //    The period `.` also needs to be escaped.
    val invalidCharsRegex = Regex("[*/\\[\\]\\.]")

    // 2. Use the standard library's 'replace' function with the regex
    //    to replace all occurrences of the invalid characters with an underscore.
    val sanitized = this.replace(invalidCharsRegex, "_")

    // 3. Handle the edge case where the original string was empty or
    //    contained ONLY invalid characters (resulting in an empty sanitized string).
    //    Firestore field names cannot be empty.
    return if (sanitized.isEmpty()) {
        "_" // Return a default, valid field name
    } else {
        sanitized
    }
}