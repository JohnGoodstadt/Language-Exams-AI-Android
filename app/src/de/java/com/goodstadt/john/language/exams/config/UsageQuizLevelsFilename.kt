package com.goodstadt.john.language.exams.screens.UsageQuiz

import com.goodstadt.john.language.exams.packages.reference.shared.QuizDetail

enum class UsageQuizLevelsFilename(val quizzes: List<QuizDetail>) {

    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A1-de", title = "Sätze bauen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A1-de", title = "Verben in der Gegenwart"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A1-de", title = "Über gestern sprechen"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A1-de", title = "Fragen & Kurzantworten"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A1-de", title = "in, auf, mit & Co."
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A1-de", title = "Sätze verbinden"
            )
        )
    ),

    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A2-de", title = "Über morgen sprechen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A2-de", title = "Was gerade passiert"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A2-de", title = "Vergleichen: größer, am größten"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A2-de", title = "können, müssen, wollen …"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A2-de", title = "Wem oder wen? Verben"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A2-de", title = "weil, dass, wenn"
            )
        )
    ),

    UPPER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B1-de", title = "Von früher erzählen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B1-de", title = "Was wäre, wenn …?"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B1-de", title = "Was gemacht wird"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B1-de", title = "Sagen, was andere sagen"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B1-de", title = "Der Mann, der …"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B1-de", title = "warten auf, denken an"
            )
        )
    ),

    ADVANCED(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B2-de", title = "Die richtige Zeit wählen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B2-de", title = "Was wäre gewesen, wenn …?"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B2-de", title = "Formell formulieren"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B2-de", title = "Sachlich schreiben"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B2-de", title = "Meinung begründen"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B2-de", title = "Das genaue Wort"
            )
        )
    );

    val description: String
        get() = when (this) {
            ELEMENTARY -> "Anfänger"
            INTER -> "Grundstufe"
            UPPER -> "Mittelstufe"
            ADVANCED -> "Fortgeschritten"
        }

    /** Compact label for tight horizontal pickers on small screens */
    val shortLabel: String
        get() = when (this) {
            ELEMENTARY -> "Anf."
            INTER -> "Grund."
            UPPER -> "Mittel."
            ADVANCED -> "Fortg."
        }

    val ESOL: String
        get() = when (this) {
            ELEMENTARY -> "A1"
            INTER -> "A2"
            UPPER -> "B1"
            ADVANCED -> "B2"
        }
}
