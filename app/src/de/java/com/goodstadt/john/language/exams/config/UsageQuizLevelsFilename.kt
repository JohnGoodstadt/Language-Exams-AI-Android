package com.goodstadt.john.language.exams.screens.UsageQuiz

import com.goodstadt.john.language.exams.packages.reference.shared.QuizDetail

enum class UsageQuizLevelsFilename(val quizzes: List<QuizDetail>) {

    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A1-de", title = "Sätze bauen", titleEn = "Building sentences"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A1-de", title = "Verben in der Gegenwart", titleEn = "Verbs in the present"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A1-de", title = "Über gestern sprechen", titleEn = "Talking about yesterday"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A1-de", title = "Fragen & Kurzantworten", titleEn = "Questions & short answers"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A1-de", title = "in, auf, mit & Co.", titleEn = "in, on, with & co."
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A1-de", title = "Sätze verbinden", titleEn = "Joining sentences"
            )
        )
    ),

    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A2-de", title = "Über morgen sprechen", titleEn = "Talking about tomorrow"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A2-de", title = "Was gerade passiert", titleEn = "What's happening now"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A2-de", title = "Vergleichen: größer, am größten", titleEn = "Comparing: bigger, biggest"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A2-de", title = "können, müssen, wollen …", titleEn = "can, must, want …"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A2-de", title = "Wem oder wen? Verben", titleEn = "Wem or wen? Verbs"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A2-de", title = "weil, dass, wenn", titleEn = "because, that, if"
            )
        )
    ),

    UPPER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B1-de", title = "Von früher erzählen", titleEn = "Telling about the past"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B1-de", title = "Was wäre, wenn …?", titleEn = "What would happen if …?"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B1-de", title = "Was gemacht wird", titleEn = "What is being done"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B1-de", title = "Sagen, was andere sagen", titleEn = "Saying what others say"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B1-de", title = "Der Mann, der …", titleEn = "The man who …"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B1-de", title = "warten auf, denken an", titleEn = "wait for, think of"
            )
        )
    ),

    ADVANCED(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B2-de", title = "Die richtige Zeit wählen", titleEn = "Choosing the right tense"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B2-de", title = "Was wäre gewesen, wenn …?", titleEn = "What would have happened if …?"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B2-de", title = "Formell formulieren", titleEn = "Phrasing formally"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B2-de", title = "Sachlich schreiben", titleEn = "Writing factually"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B2-de", title = "Meinung begründen", titleEn = "Giving reasons for an opinion"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B2-de", title = "Das genaue Wort", titleEn = "The exact word"
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
