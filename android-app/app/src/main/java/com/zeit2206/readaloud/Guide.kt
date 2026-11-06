package com.zeit2206.readaloud

/** One IPA symbol card, ported from the extension's symbol guide (guide/guide.js). */
data class SymbolInfo(val symbol: String, val name: String, val examples: List<String>, val tip: String, val technical: String)

private fun s(sym: String, name: String, ex: List<String>, tip: String, tech: String) = SymbolInfo(sym, name, ex, tip, tech)

val GUIDE: List<Pair<String, List<SymbolInfo>>> = listOf(
    "Vowels" to listOf(
        s("i", "Long \"ee\"", listOf("see", "meet", "key"), "Like Spanish \"i\", but held a little longer.", "close front unrounded vowel"),
        s("ɪ", "Short, relaxed \"i\"", listOf("sit", "ship", "busy"), "Between Spanish \"i\" and \"e\". Not \"i\": ship ≠ sheep.", "near-close near-front unrounded vowel"),
        s("ɛ", "Short \"e\"", listOf("bed", "said", "head"), "Close to Spanish \"e\" in \"perro\".", "open-mid front unrounded vowel"),
        s("æ", "Open \"a\" with spread lips", listOf("cat", "bad", "man"), "Say Spanish \"a\" while smiling toward \"e\". bad ≠ bed.", "near-open front unrounded vowel"),
        s("ɑ", "Open \"ah\"", listOf("father", "hot", "stop"), "Like Spanish \"a\", mouth wide open.", "open back unrounded vowel"),
        s("ɔ", "Open \"aw\"", listOf("law", "thought", "all"), "Like Spanish \"o\" with the mouth more open. Many US speakers say ɑ instead.", "open-mid back rounded vowel"),
        s("ʊ", "Short, relaxed \"u\"", listOf("book", "put", "good"), "Shorter and looser than Spanish \"u\". full ≠ fool.", "near-close near-back rounded vowel"),
        s("u", "Long \"oo\"", listOf("food", "blue", "you"), "Like Spanish \"u\", held longer.", "close back rounded vowel"),
        s("ʌ", "Short \"uh\" (stressed)", listOf("cup", "love", "money"), "A short, central \"a\". Don’t say Spanish \"o\" or \"u\".", "open-mid back unrounded vowel (in US English, closer to a central vowel)"),
        s("ə", "Schwa: weak \"uh\"", listOf("about", "banana", "the"), "The most common English sound. Unstressed vowels shrink to this.", "mid central vowel (schwa)"),
        s("ɝ", "Stressed \"er\"", listOf("bird", "her", "work"), "No vowel before the r: tongue curls back and stays. Not \"beerd\".", "r-coloured open-mid central unrounded vowel (stressed)"),
        s("ɚ", "Unstressed \"er\"", listOf("teacher", "better", "doctor"), "A schwa coloured by r. Short and weak.", "r-coloured mid central vowel (unstressed)"),
        s("e", "Part of eɪ", listOf("say"), "In this dictionary e appears inside eɪ (see diphthongs).", "close-mid front unrounded vowel"),
        s("o", "Part of oʊ", listOf("go"), "In this dictionary o appears inside oʊ (see diphthongs).", "close-mid back rounded vowel"),
        s("a", "Part of aɪ / aʊ", listOf("my", "now"), "In this dictionary a appears inside aɪ and aʊ (see diphthongs).", "open front unrounded vowel"),
    ),
    "Diphthongs (gliding vowels)" to listOf(
        s("eɪ", "\"ay\"", listOf("say", "day", "great"), "Like Spanish \"ei\" in \"rey\".", "closing diphthong: close-mid front → near-close near-front, unrounded"),
        s("aɪ", "\"eye\"", listOf("my", "time", "buy"), "Like Spanish \"ai\" in \"hay\".", "closing diphthong: open front → near-close near-front, unrounded"),
        s("ɔɪ", "\"oy\"", listOf("boy", "choice", "noise"), "Like Spanish \"oy\" in \"hoy\".", "closing diphthong: open-mid back rounded → near-close near-front unrounded"),
        s("aʊ", "\"ow\"", listOf("now", "house", "out"), "Like Spanish \"au\" in \"auto\".", "closing diphthong: open front unrounded → near-close near-back rounded"),
        s("oʊ", "\"oh\"", listOf("go", "home", "know"), "Spanish \"o\" that glides to \"u\". Don’t keep it flat.", "closing diphthong: close-mid back → near-close near-back, rounded"),
    ),
    "Consonants" to listOf(
        s("p", "p", listOf("pen", "happy"), "At the start of a word, add a puff of air: \"pʰen\".", "voiceless bilabial plosive"),
        s("b", "b", listOf("bad", "baby"), "Always lips closed, even between vowels (Spanish softens it).", "voiced bilabial plosive"),
        s("t", "t", listOf("ten", "water"), "Tongue on the ridge behind the teeth, not on the teeth. Between vowels in US English it often sounds like a quick \"r\": water.", "voiceless alveolar plosive"),
        s("d", "d", listOf("dog", "ladder"), "Full stop, not the soft Spanish \"d\" of \"cada\".", "voiced alveolar plosive"),
        s("k", "k", listOf("cat", "key"), "Puff of air at the start of a word.", "voiceless velar plosive"),
        s("ɡ", "hard g", listOf("go", "bag"), "Always hard, as in Spanish \"gato\" — never \"j\".", "voiced velar plosive"),
        s("f", "f", listOf("fish", "phone"), "Same as Spanish.", "voiceless labiodental fricative"),
        s("v", "v", listOf("very", "love"), "Top teeth on lower lip, with voice. Not \"b\": very ≠ berry.", "voiced labiodental fricative"),
        s("θ", "voiceless \"th\"", listOf("think", "three", "month"), "Like Castilian \"z\" in \"zapato\": tongue between the teeth. Not \"t\" or \"s\".", "voiceless dental fricative"),
        s("ð", "voiced \"th\"", listOf("this", "mother", "the"), "Like θ with voice. Close to the soft Spanish \"d\" in \"cada\".", "voiced dental fricative"),
        s("s", "s", listOf("see", "city"), "Same as Spanish. Never add \"e\" before it: \"school\", not \"eschool\".", "voiceless alveolar fricative"),
        s("z", "z (buzzing)", listOf("zoo", "is", "dogs"), "A buzzing \"s\" with voice. Spanish doesn’t have it — feel your throat vibrate.", "voiced alveolar fricative"),
        s("ʃ", "\"sh\"", listOf("she", "nation"), "The \"shh\" for silence.", "voiceless postalveolar fricative"),
        s("ʒ", "\"zh\"", listOf("vision", "measure"), "Like ʃ with voice. Like the Argentine \"ll\" in \"calle\".", "voiced postalveolar fricative"),
        s("tʃ", "\"ch\"", listOf("church", "watch"), "Like Spanish \"ch\".", "voiceless postalveolar affricate"),
        s("dʒ", "\"j\" / soft \"g\"", listOf("job", "judge", "age"), "Like tʃ with voice. Not Spanish \"y\" or \"j\".", "voiced postalveolar affricate"),
        s("h", "h", listOf("hat", "who"), "Just breath, much softer than Spanish \"j\".", "voiceless glottal fricative"),
        s("m", "m", listOf("man", "come"), "Same as Spanish.", "bilabial nasal"),
        s("n", "n", listOf("no", "ten"), "Same as Spanish.", "alveolar nasal"),
        s("ŋ", "\"ng\"", listOf("sing", "think"), "Back of the tongue up, no hard \"g\" at the end: sing, not \"sing-g\".", "velar nasal"),
        s("l", "l", listOf("let", "feel"), "At the end of a word it is \"dark\": tongue back, as in \"feel\".", "alveolar lateral approximant"),
        s("ɹ", "English r", listOf("red", "very", "car"), "Tongue curls back and never touches the roof. Never rolled or tapped like Spanish \"r\".", "alveolar approximant"),
        s("w", "w", listOf("we", "one"), "Like Spanish \"hu\" in \"huevo\".", "voiced labial-velar approximant"),
        s("j", "\"y\"", listOf("yes", "you"), "Like Spanish \"y\" in \"yo\" said softly — not \"ll\" or \"j\".", "palatal approximant"),
    ),
    "Stress marks" to listOf(
        s("ˈ", "Primary stress", listOf("photograph", "about"), "The syllable after this mark is the loud, long one.", "primary stress mark (suprasegmental)"),
        s("ˌ", "Secondary stress", listOf("photographic", "understand"), "The syllable after this mark gets lighter stress.", "secondary stress mark (suprasegmental)"),
        s("*", "Approximate", emptyList(), "Shown after a transcription when the exact word wasn’t found and a related form was used.", "not an IPA symbol: marks an approximate match"),
    ),
)

fun symbolInfo(sym: String): SymbolInfo? = GUIDE.firstNotNullOfOrNull { (_, items) -> items.firstOrNull { it.symbol == sym } }
