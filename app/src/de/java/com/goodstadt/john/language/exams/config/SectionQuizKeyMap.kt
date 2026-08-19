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
 *   is "FoodandDrink" at A2 but "Food" at B1. A flat title->key map would load the wrong quiz.
 *
 * The keys below are the EXACT category titles from de/res/raw/vocab_data_<level>.json, matched
 * to the WordQuiz<key>-de.json files that actually ship under Quizzes/SectionQuiz/<level>. Keeping
 * these in sync matters: an unmapped title falls back to deriveSectionQuizKey(), which keeps the
 * German letters (e.g. "Persönliches" -> "WordQuizPersönliches") and points at a file that doesn't
 * exist. A few short aliases are included where Firebase is known to serve a variant title.
 */
object SectionQuizKeyMap {

    private val byLevel: Map<String, Map<String, String>> = mapOf(

        // A1 files: Adjectives, Colors, Dates, Everyday, Expressions, Family, FoodandDrink, Hello,
        //   Months, Numbers, Occupations, Personal, Places, Questions, Seasons, Time, Verbs,
        //   WeekDays, upinonatby
        "A1" to mapOf(
            "Hallo" to "Hello",
            "Persönliche Informationen" to "Personal",
            "Persönliches" to "Personal",                       // Firebase variant
            "Familie" to "Family",
            "Zahlen" to "Numbers",
            "Wochentage" to "WeekDays",
            "Monate" to "Months",
            "Jahreszeiten" to "Seasons",
            "Zeit" to "Time",
            "Farben" to "Colors",
            "Essen & Trinken" to "FoodandDrink",
            "Häufige Verben" to "Verbs",
            "Gegenstände & Haushaltsartikel" to "Everyday",
            "Adjektive (häufig)" to "Adjectives",
            "Adjektive" to "Adjectives",                        // alias without the "(häufig)" suffix
            "Präpositionen" to "Prepositions",
            "Fragewörter" to "Questions",
            "Häufige Ausdrücke" to "Expressions",
        ),

        // A2 files: Adjectives, Conjunctions, DateTime, Education, Expressions, Family, FoodandDrink,
        //   Greetings, Health, Home, Jobs, Leisure, Nature, PastTense, Patterns, Personal, Places,
        //   Prepositions, Pronouns, Quantities, Shopping, Social, Technology, Travel, Verbs, Weather,
        //   inandbigquickly
        "A2" to mapOf(
            "Begrüßungen & Verabschiedungen" to "Greetings",
            "Persönliche Informationen" to "Personal",
            "Persönliches" to "Personal",                       // Firebase variant / reported title
            "Familie" to "Family",
            "Tagesablauf & Freizeit" to "Leisure",
            "Essen & Trinken" to "FoodandDrink",
            "Einkaufen" to "Shopping",
            "Zahlen & Mengen" to "Quantities",
            "Zeit & Datum" to "DateTime",
            "Orte & Richtungen" to "Places",
            "Berufe & Tätigkeiten" to "Jobs",
            "Adjektive" to "Adjectives",
            "Verben (Auswahl)" to "Verbs",
            "Häufige Ausdrücke" to "Expressions",
            "Präpositionen" to "Prepositions",
            "Konjunktionen" to "Conjunctions",
            "Pronomen" to "Pronouns",
        ),

        // B1 files: Adjectives, Adverbs, ComplexSentences, Education, Emotions, Food, Health,
        //   Leisure, Nature, Personal, Relationships, Shopping, Social, Technology, Travel, Verbs
        "B1" to mapOf(
            "Persönliches" to "Personal",
            "Persönliche Informationen" to "Personal",          // alias
            "Bildung" to "Education",
            "Reisen" to "Travel",
            "Gesundheit" to "Health",
            "Freizeit" to "Leisure",
            "Essen & Trinken" to "Food",                        // NB: "Food" at B1, "FoodandDrink" at A1/A2
            "Einkaufen" to "Shopping",
            "Technologie" to "Technology",
            "Natur" to "Nature",
            "Gesellschaft" to "Social",
            "Beziehungen" to "Relationships",
            "Emotionen" to "Emotions",
            "Verben" to "Verbs",
            "Adjektive" to "Adjectives",
            "Adverbien" to "Adverbs",
            "Komplexe Sätze" to "ComplexSentences",
        ),

        // B2 uses the SAME category titles as A1 (Hallo, Zahlen, ...), but the word content under
        // each is B2-level, so its section quizzes are generated from it exactly like the other
        // levels. Keys mirror A1. (If the B2 vocab is ever re-themed, update titles + keys together.)
        "B2" to mapOf(
            "Hallo" to "Hello",
            "Hallo (B2)" to "Hello",                            // the actual B2 title carries a suffix
            "Persönliche Informationen" to "Personal",
            "Persönliches" to "Personal",                       // Firebase variant
            "Familie" to "Family",
            "Zahlen" to "Numbers",
            "Wochentage" to "WeekDays",
            "Monate" to "Months",
            "Jahreszeiten" to "Seasons",
            "Zeit" to "Time",
            "Farben" to "Colors",
            "Essen & Trinken" to "FoodandDrink",
            "Häufige Verben" to "Verbs",
            "Verben" to "Verbs",                                // alias
            "Gegenstände & Haushaltsartikel" to "Everyday",
            "Adjektive (häufig)" to "Adjectives",
            "Adjektive" to "Adjectives",                        // alias without the "(häufig)" suffix
            "Präpositionen" to "Prepositions",
            "Fragewörter" to "Questions",
            "Häufige Ausdrücke" to "Expressions",
        ),
    )

    /** @return the WordQuiz base key for [title] at [level], or the derived key if none is mapped. */
    // German titles are real translations (e.g. "Essen & Trinken" -> "FoodandDrink"), so they're
    // looked up explicitly; anything not listed falls back to the derived key.
    fun keyFor(level: String, title: String): String =
        byLevel[level]?.get(title.trim()) ?: deriveSectionQuizKey(title)
}
