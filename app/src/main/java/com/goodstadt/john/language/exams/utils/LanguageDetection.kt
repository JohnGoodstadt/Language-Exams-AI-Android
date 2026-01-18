package com.goodstadt.john.language.exams.utils

fun isSystemLanguageSpanish(): Boolean {
    val locales = android.os.LocaleList.getDefault()

    for (i in 0 until locales.size()) {
        val locale = locales[i]
        val language = locale.language.lowercase()

        if (language.startsWith("es")) {
            return true
        }
    }
    return false
}
/*
if (isSystemLanguage("es")) {
    // Show Spanish learner content
}

if (isSystemLanguage("ar")) {
    // Show Arabic learner content
}

 */
fun isSystemLanguage(languagePrefix: String): Boolean {
    val prefix = languagePrefix.lowercase()
    val locales = android.os.LocaleList.getDefault()

    for (i in 0 until locales.size()) {
        val locale = locales[i]
        if (locale.language.lowercase().startsWith(prefix)) {
            return true
        }
    }
    return false
}
