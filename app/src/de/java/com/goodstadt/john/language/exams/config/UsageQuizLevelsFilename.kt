package com.goodstadt.john.language.exams.screens.UsageQuiz

import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail

enum class UsageQuizLevelsFilename(val quizzes: List<QuizDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A1-de",
                title = "Sentence Structure (DE)"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A1-en",
                title = "Present Simple"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3A1-en",
                title = "Past Simple"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4A1-en",
                title = "Questions & Short Answers"
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5A1-en",
                title = "Prepositions"
            ),
            QuizDetail(
                id = 6,
                baseName = "UsageQuiz6A1-en",
                title = "Connectors"
            )
        )
    ),
    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A2-en",
                title = "Future Forms"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A2-en",
                title = "Present Continuous"
            ),
            QuizDetail(3, "UsageQuiz3A2-en", "Comparatives & Superlatives"),
            QuizDetail(4, "UsageQuiz4A2-en", "Modal Verbs"),
            QuizDetail(5, "UsageQuiz5A2-en", "Verb Patterns"),
            QuizDetail(6, "UsageQuiz6A2-en", "Linking Words & If Clauses"),
        )
    ),
    UPPER(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B1-en", title = "Tense Mastery"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B1-en",
                title = "Real & Hypothetical Situations"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3B1-en",
                title = "Formal & Official Language"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4B1-en",
                title = "Reporting & Communication",
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5B1-en",
                title = "Structured Arguments"
            ),
            QuizDetail(6, "UsageQuiz6B1-en", "Functional Fluency")
        )
    ),
    ADVANCED(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B2-en", title = "Aspect & Time Control"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B2-en",
                title = "Hypothetical Reasoning"
            ),
            QuizDetail(3, "UsageQuiz3B2-en", "Formal Structural Control"),
            QuizDetail(4, "UsageQuiz4B2-en", "Academic Expression"),
            QuizDetail(5, "UsageQuiz5B2-en", "Argument Development"),
            QuizDetail(6, "UsageQuiz6B2-en", "Precision & Nuance")
        )
    );


    val description: String
        get() = when(this) {
            ELEMENTARY -> "Beginner"
            INTER -> "Elementary" // Explicitly string match if needed
            UPPER -> "Inter"
            ADVANCED -> "Advanced"
        }
    /** Compact label for tight horizontal pickers on small screens */
    val shortLabel: String
        get() = when(this) {
            ELEMENTARY -> "Begin."
            INTER -> "Elem."
            UPPER -> "Inter."
            ADVANCED -> "Adv."
        }
    val ESOL: String
        get() = when(this) {
            ELEMENTARY -> "A1"
            INTER -> "A2"
            UPPER -> "B1"
            ADVANCED -> "B2"
        }
}
