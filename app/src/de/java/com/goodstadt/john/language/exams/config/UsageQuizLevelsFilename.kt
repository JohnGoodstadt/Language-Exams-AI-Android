package com.goodstadt.john.language.exams.screens.UsageQuiz

import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail

enum class UsageQuizLevelsFilename(val quizzes: List<QuizDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A1-de",
                title = "Sentence Structure"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A1-de",
                title = "Present Simple"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3A1-de",
                title = "Past Simple"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4A1-de",
                title = "Questions & Short Answers"
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5A1-de",
                title = "Prepositions"
            ),
            QuizDetail(
                id = 6,
                baseName = "UsageQuiz6A1-de",
                title = "Connectors"
            )
        )
    ),
    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A2-de",
                title = "Future Forms"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A2-de",
                title = "Present Continuous"
            ),
            QuizDetail(3, "UsageQuiz3A2-de", "Comparatives & Superlatives"),
            QuizDetail(4, "UsageQuiz4A2-de", "Modal Verbs"),
            QuizDetail(5, "UsageQuiz5A2-de", "Verb Patterns"),
            QuizDetail(6, "UsageQuiz6A2-de", "Linking Words & If Clauses"),
        )
    ),
    UPPER(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B1-de", title = "Tense Mastery"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B1-de",
                title = "Real & Hypothetical Situations"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3B1-de",
                title = "Formal & Official Language"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4B1-de",
                title = "Reporting & Communication",
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5B1-de",
                title = "Structured Arguments"
            ),
            QuizDetail(6, "UsageQuiz6B1-de", "Functional Fluency")
        )
    ),
    ADVANCED(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B2-de", title = "Aspect & Time Control"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B2-de",
                title = "Hypothetical Reasoning"
            ),
            QuizDetail(3, "UsageQuiz3B2-de", "Formal Structural Control"),
            QuizDetail(4, "UsageQuiz4B2-de", "Academic Expression"),
            QuizDetail(5, "UsageQuiz5B2-de", "Argument Development"),
            QuizDetail(6, "UsageQuiz6B2-de", "Precision & Nuance")
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
