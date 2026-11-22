package com.example.offlineaitranslator.engine

/**
 * Lightweight, dependency-free cleanup for raw speech-to-text transcripts.
 *
 * Pure Kotlin: no Android framework classes and no network access, so this
 * can be used from any app layer (and directly from JVM unit tests).
 */
object GrammarCorrectionEngine {

    /** Spoken fillers removed case-insensitively (single words and phrases). */
    private val fillerRegex = Regex(
        """(?i)(?<!\p{L})(?:uh|um|erm|ah|you\s+know|i\s+mean|मतलब)(?!\p{L})"""
    )

    /** Common apostrophe-less contractions produced by ASR output. */
    private val contractions = mapOf(
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "doesnt" to "doesn't",
        "didnt" to "didn't",
        "shouldnt" to "shouldn't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "aint" to "ain't",
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "youre" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "youd" to "you'd",
        "thats" to "that's",
        "theres" to "there's",
        "whats" to "what's",
        "wheres" to "where's",
        "whos" to "who's",
        "hows" to "how's",
        "lets" to "let's",
        "its" to "it's",
        "hes" to "he's",
        "shes" to "she's",
        "theyre" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "theyd" to "they'd",
        "weve" to "we've",
        "wed" to "we'd",
        "wouldve" to "would've",
        "couldve" to "could've",
        "shouldve" to "should've",
        "mustve" to "must've"
    )

    private val wordRegex = Regex("""(?i)\b[a-z]+\b""")
    private val phraseStutterRegex =
        Regex("""(?i)(?<!\p{L})(\p{L}+\s+\p{L}+)\s+\1(?!\p{L})""")
    private val wordStutterRegex =
        Regex("""(?i)(?<!\p{L})(\p{L}+)\s+\1(?!\p{L})""")
    private val whitespaceRegex = Regex("""\s+""")
    private val leadingJunkRegex = Regex("""^[\s,;:.!?\-]+""")
    private val terminalPunctuation = setOf('.', '!', '?', '।', '؟', '…')

    /**
     * Cleans [text] by removing fillers, collapsing immediate stutters,
     * repairing common broken contractions, normalising whitespace,
     * capitalising the first letter and adding terminal punctuation.
     */
    fun clean(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result

        // 1. Remove spoken fillers (e.g. "uh", "you know", "मतलब").
        result = fillerRegex.replace(result, " ")

        // 2. Repair contractions, preserving the original leading case.
        result = wordRegex.replace(result) { match ->
            val original = match.value
            val fixed = contractions[original.lowercase()]
            when {
                fixed == null -> original
                original.firstOrNull()?.isUpperCase() == true ->
                    fixed.replaceFirstChar { it.uppercaseChar() }
                else -> fixed
            }
        }

        // 3. Collapse immediate repeated words and short phrases until stable.
        //    "I need need medicine" -> "I need medicine", "the the" -> "the".
        var previous: String
        do {
            previous = result
            result = phraseStutterRegex.replace(result, "$1")
            result = wordStutterRegex.replace(result, "$1")
        } while (result != previous)

        // 4. Normalise whitespace and tidy punctuation left behind by fillers.
        result = whitespaceRegex.replace(result, " ").trim()
        result = leadingJunkRegex.replace(result, "")
        if (result.isEmpty()) return result

        // 5. Capitalise the first letter and guarantee terminal punctuation.
        result = result.replaceFirstChar { it.uppercaseChar() }
        if (result.last() !in terminalPunctuation) result += "."

        return result
    }
}
