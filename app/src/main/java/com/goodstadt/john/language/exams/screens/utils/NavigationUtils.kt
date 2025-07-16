// In utils/NavigationUtils.kt
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun String.urlEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
}
fun String.removeContentInBracketsAndTrim(): String {
    val regex = "\\s*\\([^)]*\\)".toRegex() // Matches optional spaces before and any characters within parentheses
    return this.replace(regex, "").trimEnd()
}