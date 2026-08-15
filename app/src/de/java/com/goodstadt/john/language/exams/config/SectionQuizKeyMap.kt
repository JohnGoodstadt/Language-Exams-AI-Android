package com.goodstadt.john.language.exams.screens.CategoryTab

/**
 * Maps a German vocab category title (exactly as it appears in vocab_data_*.json /
 * Firebase) to the language-independent WordQuiz base key used to build the quiz
 * filename, e.g. "Essen & Trinken" -> "Food" -> WordQuizFood1-de.json, WordQuizFood2-de.json …
 *
 * WHY a per-flavour object instead of editing the JSON:
 *   The vocab_data files live in Firebase, so we keep the mapping in code. Each flavour
 *   ships its own SectionQuizKeyMap with the same shape (an explicit level -> title -> key
 *   lookup); Chinese returns null (no quizzes). Adding Italian later means adding one more
 *   SectionQuizKeyMap — no shared code changes.
 *
 * WHY keyed by LEVEL first:
 *   The same German title can map to a different quiz per level — e.g. "Essen & Trinken"
 *   is "FoodandDrink" at A2 but "Food" at B1. A flat title→key map would load the wrong quiz.
 *
 * keyFor() returns null when a category has no quiz at that level; the ViewModel then
 * shows no quiz (and the Quiz button can be hidden on null).
 *
 * Unambiguous matches are pre-filled. Confirm/complete the // TODO lines as the German
 * quiz content for each level is finalised.
 */
object SectionQuizKeyMap {

    private val byLevel: Map<String, Map<String, String>> = mapOf(

        // Shipped A1 quiz keys: Adjectives, Colors, Dates, Everyday, Family, Hello,
        //                       Numbers, Occupations, Places, Verbs, upinonatby
        "A1" to mapOf(
          //  "PerHallo" to "Hello",
            "Hallo" to "Hello",//
            "Familie" to "Family",//
            "Zahlen" to "Numbers",//
            "Farben" to "Colors",//
            "Häufige Verben" to "Verbs",//
            "Gegenstände & Haushaltsartikel" to "Everyday",//
            "Adjektive" to "Adjectives",//
            "Präpositionen" to "upinonatby", //
            "Fragewörter" to "Questions",//
             "Persönliche Informationen" to "Personal",//
             "Wochentage" to "WeekDays",//
             "Monate" to "Months",//
             "Jahreszeiten" to "Seasons",
             "Zeit" to "Time",//
             "Essen & Trinken" to "FoodandDrink",
             "Häufige Ausdrücke" to "Expressions",
        ),

        // Shipped A2 quiz keys: Education, FoodandDrink, Health, Home, Jobs, Leisure,
        //   Nature, PastTense, Patterns, Personal, Shopping, Social, Technology,
        //   Travel, Weather, inandbigquickly
        "A2" to mapOf(
            "Persönliche Informationen" to "Personal",
            "Tagesablauf & Freizeit" to "Leisure",
            "Essen & Trinken" to "FoodandDrink",
            "Einkaufen" to "Shopping",
            "Berufe & Tätigkeiten" to "Jobs",
            // TODO — confirm (unused keys: Education, Health, Home, Nature, PastTense,
            //   Patterns, Social, Technology, Travel, Weather, inandbigquickly):
            // "Begrüßungen & Verabschiedungen" to "Social",        // ? greetings/farewells
            // "Familie" to "",
            "Zahlen & Mengen" to "Quantities",
            "Zeit & Datum"    to "DateTime",
            "Pronomen"        to "Pronouns",
            "Orte & Richtungen" to "Places",
            "Adjektive"         to "Adjectives",
            "Verben (Auswahl)"  to "Verbs",
            "Präpositionen"     to "Prepositions",
            "Konjunktionen"     to "Conjunctions",
            "Häufige Ausdrücke" to "Expressions",
        ),

        // Shipped B1 quiz keys: Adjectives, Adverbs, ComplexSentences, Education, Emotions,
        //   Food, Health, Leisure, Nature, Personal, Relationships, Shopping, Social,
        //   Technology, Travel, Verbs
        "B1" to mapOf(
            "Persönliche Informationen" to "Personal",//
            "Essen & Trinken" to "Food",//
            "Häufige Verben" to "Verbs",//
            "Adjektive" to "Adjectives",//
            // TODO — the current German B1 vocab still reuses the A1 category list, so most
            //   B1 quiz themes (Adverbs, ComplexSentences, Education, Emotions, Health,
            //   Leisure, Nature, Relationships, Shopping, Social, Technology, Travel) have
            //   no matching category yet. Fill in once B1 categories are curated:
             "Hallo" to "",
             "Familie" to "Relationships",
             "Fragewörter" to "Questions",
             "Häufige Ausdrücke" to "",
            // "Farben" to "",
        ),

        // Shipped B2 quiz keys: Adjectives, Adverbs, Education, Emotions, Entertainment,
        //   Health, Nature, PersonalDevelopment, Relationships, SocialIssues, Technology,
        //   Travel, Verbs, Work
        "B2" to mapOf(
            "Häufige Verben" to "Verbs",
            "Adjektive" to "Adjectives",
            // TODO — as with B1, the German B2 vocab currently mirrors the A1 list; fill in
            //   once B2 categories are curated (PersonalDevelopment, Emotions, Work, …).
        ),
    )

    /** @return the WordQuiz base key for [title] at [level], or null if none is mapped. */
    // German titles are real translations (e.g. "Essen & Trinken" -> "FoodandDrink"), so they're
    // looked up explicitly; anything not listed falls back to the derived key.
    fun keyFor(level: String, title: String): String =
        byLevel[level]?.get(title.trim()) ?: deriveSectionQuizKey(title)
}
