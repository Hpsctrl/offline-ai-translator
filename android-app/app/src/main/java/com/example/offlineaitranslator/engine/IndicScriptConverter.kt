package com.example.offlineaitranslator.engine

/**
 * Pure Kotlin isomorphic Unicode transliterator for Indic / Brahmic scripts.
 *
 * Brahmic scripts in Unicode (Devanagari, Bengali, Gurmukhi, Gujarati, Odia,
 * Tamil, Telugu, Kannada, Malayalam) are isomorphic:
 * within their standard 128-code-point blocks (0x00..0x7F), each relative offset
 * corresponds to the exact same phonetic element (consonant, vowel sign, halant, etc.).
 *
 * IndicTrans2 uses unified Devanagari internally for all Indic languages:
 * - When translating Indic -> En: Source script is transliterated to Devanagari before BPE encoding.
 * - When translating En -> Indic: Generated tokens are in Devanagari and must be transliterated
 *   to the target native script (e.g. Telugu 'tel_Telu', Bengali 'ben_Beng', Tamil 'tam_Taml', etc.).
 */
object IndicScriptConverter {

    // Unicode block base offsets
    const val BLOCK_DEVANAGARI = 0x0900 // hi, mr, sa, ne, brx, doi, gom, mai, bho
    const val BLOCK_BENGALI = 0x0980    // bn, as, mni
    const val BLOCK_GURMUKHI = 0x0A00   // pa
    const val BLOCK_GUJARATI = 0x0A80   // gu
    const val BLOCK_ODIA = 0x0B00       // or
    const val BLOCK_TAMIL = 0x0B80      // ta
    const val BLOCK_TELUGU = 0x0C00     // te
    const val BLOCK_KANNADA = 0x0C80    // kn
    const val BLOCK_MALAYALAM = 0x0D00  // ml

    private const val COORDINATED_RANGE_START = 0x01
    private const val COORDINATED_RANGE_END = 0x6F

    // Language code to Unicode block start (supporting both Flores-200 tags and ISO codes)
    private val LANG_TO_BLOCK = mapOf(
        // Devanagari block
        "hin_Deva" to BLOCK_DEVANAGARI, "hi" to BLOCK_DEVANAGARI, "hin" to BLOCK_DEVANAGARI,
        "mar_Deva" to BLOCK_DEVANAGARI, "mr" to BLOCK_DEVANAGARI, "mar" to BLOCK_DEVANAGARI,
        "san_Deva" to BLOCK_DEVANAGARI, "sa" to BLOCK_DEVANAGARI, "san" to BLOCK_DEVANAGARI,
        "npi_Deva" to BLOCK_DEVANAGARI, "ne" to BLOCK_DEVANAGARI, "npi" to BLOCK_DEVANAGARI,
        "brx_Deva" to BLOCK_DEVANAGARI, "brx" to BLOCK_DEVANAGARI,
        "doi_Deva" to BLOCK_DEVANAGARI, "doi" to BLOCK_DEVANAGARI,
        "gom_Deva" to BLOCK_DEVANAGARI, "kok" to BLOCK_DEVANAGARI, "gom" to BLOCK_DEVANAGARI,
        "mai_Deva" to BLOCK_DEVANAGARI, "mai" to BLOCK_DEVANAGARI,
        "bho_Deva" to BLOCK_DEVANAGARI, "bho" to BLOCK_DEVANAGARI,

        // Bengali / Assamese / Meitei block
        "ben_Beng" to BLOCK_BENGALI, "bn" to BLOCK_BENGALI, "ben" to BLOCK_BENGALI,
        "asm_Beng" to BLOCK_BENGALI, "as" to BLOCK_BENGALI, "asm" to BLOCK_BENGALI,
        "mni_Beng" to BLOCK_BENGALI, "mni" to BLOCK_BENGALI,

        // Gurmukhi
        "pan_Guru" to BLOCK_GURMUKHI, "pa" to BLOCK_GURMUKHI, "pan" to BLOCK_GURMUKHI,

        // Gujarati
        "guj_Gujr" to BLOCK_GUJARATI, "gu" to BLOCK_GUJARATI, "guj" to BLOCK_GUJARATI,

        // Odia
        "ory_Orya" to BLOCK_ODIA, "or" to BLOCK_ODIA, "ory" to BLOCK_ODIA,

        // Dravidian blocks
        "tam_Taml" to BLOCK_TAMIL, "ta" to BLOCK_TAMIL, "tam" to BLOCK_TAMIL,
        "tel_Telu" to BLOCK_TELUGU, "te" to BLOCK_TELUGU, "tel" to BLOCK_TELUGU,
        "kan_Knda" to BLOCK_KANNADA, "kn" to BLOCK_KANNADA, "kan" to BLOCK_KANNADA,
        "mal_Mlym" to BLOCK_MALAYALAM, "ml" to BLOCK_MALAYALAM, "mal" to BLOCK_MALAYALAM
    )

    fun getScriptBlock(langCode: String): Int? {
        return LANG_TO_BLOCK[langCode.trim()]
    }

    /**
     * Checks if the language uses an isomorphic Brahmic script block.
     */
    fun isBrahmicScript(langCode: String): Boolean {
        return LANG_TO_BLOCK.containsKey(langCode.trim())
    }

    /**
     * Corrects phonetic offsets for Tamil script, which lacks voiced / aspirated
     * consonant distinctions in the standard varnamala.
     * Aligned with IndicNLP UnicodeIndicTransliterator._correct_tamil_mapping.
     */
    private fun correctTamilMapping(offset: Int): Int {
        var o = offset
        // For first 4 consonant rows of varnamala (k, ch, T, t)
        // Exception: ja (0x1C) has a mapping in Tamil (ஜ)
        if (o in 0x15..0x28 && o != 0x1C && !((o - 0x15) % 5 == 0 || (o - 0x15) % 5 == 4)) {
            val substChar = (o - 0x15) / 5
            o = 0x15 + 5 * substChar
        }

        // For 5th consonant row (p, ph, b, bh) -> maps to p (0x2A)
        if (o in 0x2B..0x2D) {
            o = 0x2A
        }

        // Devanagari 'sh' (0x36) -> Tamil 'Sh' (0x37, 0x0BB7)
        if (o == 0x36) {
            o = 0x37
        }

        return o
    }

    /**
     * Transliterates [text] from [srcLangCode] script to [tgtLangCode] script.
     * Both languages must be supported Brahmic scripts; if either is not, returns [text] unchanged.
     */
    fun transliterate(text: String, srcLangCode: String, tgtLangCode: String): String {
        val srcBase = LANG_TO_BLOCK[srcLangCode.trim()] ?: return text
        val tgtBase = LANG_TO_BLOCK[tgtLangCode.trim()] ?: return text

        if (srcBase == tgtBase) return text

        val sb = java.lang.StringBuilder(text.length)
        val isTargetTamil = (tgtBase == BLOCK_TAMIL)

        for (c in text) {
            val code = c.code
            val offset = code - srcBase

            // Danda (0x0964) and Double Danda (0x0965) are shared punctuation across Indic scripts
            if (offset in COORDINATED_RANGE_START..COORDINATED_RANGE_END && c != '\u0964' && c != '\u0965') {
                var mappedOffset = offset
                if (isTargetTamil) {
                    mappedOffset = correctTamilMapping(offset)
                }
                sb.append((tgtBase + mappedOffset).toChar())
            } else {
                sb.append(c)
            }
        }

        var result = sb.toString()

        // Odia specific post-processing
        val tgtClean = tgtLangCode.trim()
        if (tgtClean == "ory_Orya" || tgtClean == "or" || tgtClean == "ory") {
            result = result.replace("ଯ\u0B3C", "ୟ") // ja + nukta -> ya
        }

        return result
    }

    fun transliterate(text: String, srcLang: SupportedLanguage, tgtLang: SupportedLanguage): String {
        return transliterate(text, srcLang.code, tgtLang.code)
    }

    /**
     * Transliterates Devanagari text to the native script of [tgtLangCode].
     */
    fun fromDevanagari(text: String, tgtLangCode: String): String {
        return transliterate(text, "hin_Deva", tgtLangCode)
    }

    fun fromDevanagari(text: String, tgtLang: SupportedLanguage): String {
        return fromDevanagari(text, tgtLang.code)
    }

    /**
     * Transliterates native script text of [srcLangCode] to unified Devanagari.
     */
    fun toDevanagari(text: String, srcLangCode: String): String {
        val hasBrahmic = text.any { it.code in 0x0900..0x0D7F }
        if (hasBrahmic) {
            return transliterate(text, srcLangCode, "hin_Deva")
        }
        val cleanLang = srcLangCode.trim().lowercase()
        if (cleanLang != "eng_latn" && cleanLang != "en" && cleanLang != "eng" && text.isNotBlank()) {
            return romanToDevanagari(text)
        }
        return text
    }

    fun toDevanagari(text: String, srcLang: SupportedLanguage): String {
        return toDevanagari(text, srcLang.code)
    }

    /**
     * Converts Romanized Hinglish / Latin script phonetically to Devanagari Unicode.
     */
    fun romanToDevanagari(text: String): String {
        if (text.isBlank()) return text
        if (text.any { it.code in 0x0900..0x0D7F }) return text

        val vowels = listOf(
            "aa" to "आ", "ee" to "ई", "ii" to "ई", "oo" to "ऊ", "uu" to "ऊ", "ai" to "ऐ", "au" to "औ",
            "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए", "o" to "ओ"
        )
        val matras = listOf(
            "aa" to "ा", "ee" to "ी", "ii" to "ी", "oo" to "ू", "uu" to "ू", "ai" to "ै", "au" to "ौ",
            "a" to "", "i" to "ि", "u" to "ु", "e" to "े", "o" to "ो"
        )
        val consonants = listOf(
            "chh" to "छ", "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ",
            "th" to "थ", "dh" to "ध", "bh" to "भ", "ph" to "फ", "sh" to "श",
            "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द",
            "n" to "न", "p" to "प", "b" to "ब", "m" to "म", "y" to "य",
            "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
            "h" to "ह", "f" to "फ़", "z" to "ज़"
        )

        val words = text.split(" ")
        val outWords = mutableListOf<String>()

        for (word in words) {
            if (word.isBlank()) {
                outWords.add("")
                continue
            }
            val w = word.lowercase()
            var idx = 0
            val n = w.length
            val sb = StringBuilder()

            while (idx < n) {
                val c = w[idx]
                if (!c.isLetter()) {
                    sb.append(word[idx])
                    idx++
                    continue
                }

                var matchedConsonant: String? = null
                var matchedConsonantDev: String? = null
                for ((cons, dev) in consonants) {
                    if (w.startsWith(cons, idx)) {
                        matchedConsonant = cons
                        matchedConsonantDev = dev
                        break
                    }
                }

                if (matchedConsonant != null && matchedConsonantDev != null) {
                    sb.append(matchedConsonantDev)
                    idx += matchedConsonant.length

                    var matchedMatra: String? = null
                    var matchedMatraDev: String? = null
                    for ((vow, dev) in matras) {
                        if (w.startsWith(vow, idx)) {
                            matchedMatra = vow
                            matchedMatraDev = dev
                            break
                        }
                    }

                    if (matchedMatra != null && matchedMatraDev != null) {
                        sb.append(matchedMatraDev)
                        idx += matchedMatra.length
                    } else {
                        if (idx < n && w[idx].isLetter()) {
                            sb.append("्")
                        }
                    }
                } else {
                    var matchedVowel: String? = null
                    var matchedVowelDev: String? = null
                    for ((vow, dev) in vowels) {
                        if (w.startsWith(vow, idx)) {
                            matchedVowel = vow
                            matchedVowelDev = dev
                            break
                        }
                    }

                    if (matchedVowel != null && matchedVowelDev != null) {
                        sb.append(matchedVowelDev)
                        idx += matchedVowel.length
                    } else {
                        sb.append(word[idx])
                        idx++
                    }
                }
            }
            outWords.add(sb.toString())
        }
        return outWords.joinToString(" ")
    }
}
