package com.goodstadt.john.language.exams.screens.UsageQuiz

import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail

enum class UsageQuizLevelsFilename(val quizzes: List<QuizDetail>) {

    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A1-de", title = "Satzbau"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A1-de", title = "Präsens"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A1-de", title = "Präteritum"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A1-de", title = "Fragen & Kurzantworten"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A1-de", title = "Präpositionen"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A1-de", title = "Bindewörter"
            )
        )
    ),

    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1A2-de", title = "Zukunftsformen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2A2-de", title = "Verlaufsform"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3A2-de", title = "Komparativ & Superlativ"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4A2-de", title = "Modalverben"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5A2-de", title = "Verbmuster"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6A2-de", title = "Konjunktionen & Konditionalsätze"
            )
        )
    ),

    UPPER(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B1-de", title = "Zeitformen"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B1-de", title = "Reale & hypothetische Situationen"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B1-de", title = "Formelle Sprache"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B1-de", title = "Indirekte Rede"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B1-de", title = "Argumentation"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B1-de", title = "Sprachliche Sicherheit"
            )
        )
    ),

    ADVANCED(
        quizzes = listOf(
            QuizDetail(
                id = 1, baseName = "UsageQuiz1B2-de", title = "Zeitformen & Aspekte"
            ), QuizDetail(
                id = 2, baseName = "UsageQuiz2B2-de", title = "Hypothetisches Denken"
            ), QuizDetail(
                id = 3, baseName = "UsageQuiz3B2-de", title = "Formale Satzstrukturen"
            ), QuizDetail(
                id = 4, baseName = "UsageQuiz4B2-de", title = "Akademisches Schreiben"
            ), QuizDetail(
                id = 5, baseName = "UsageQuiz5B2-de", title = "Argumentation"
            ), QuizDetail(
                id = 6, baseName = "UsageQuiz6B2-de", title = "Präzision & Nuancen"
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
