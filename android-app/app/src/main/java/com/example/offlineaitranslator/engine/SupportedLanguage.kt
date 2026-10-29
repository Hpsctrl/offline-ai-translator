package com.example.offlineaitranslator.engine

enum class SupportedLanguage(
    val code: String,              // Flores-200 tag, e.g. "hin_Deva", "eng_Latn"
    val displayName: String,       // English display name, e.g. "Hindi"
    val nativeName: String,        // Native script name, e.g. "हिन्दी"
    val asrCode: String,           // Whisper / ASR language code, e.g. "hi"
    val iso639_1: String = asrCode,
    val iso639_3: String,
    val script: String,
    val isRtl: Boolean = false,
    val category: String = "indic" // "indic", "global", or "universal"
) {
    // --- Special Auto-Detect Mode ---
    AUTO_DETECT("auto", "Auto-Detect", "✨ Auto-Detect", "auto", "auto", "auto", "Universal", false, "universal"),

    // --- 22 Official Scheduled Indian Languages ---
    ASSAMESE("asm_Beng", "Assamese", "অসমীয়া", "as", "as", "asm", "Bengali-Assamese", false, "indic"),
    BENGALI("ben_Beng", "Bengali", "বাংলা", "bn", "bn", "ben", "Bengali", false, "indic"),
    BODO("brx_Deva", "Bodo", "बर'", "brx", "brx", "brx", "Devanagari", false, "indic"),
    DOGRI("doi_Deva", "Dogri", "डोगरी", "doi", "doi", "doi", "Devanagari", false, "indic"),
    GUJARATI("guj_Gujr", "Gujarati", "ગુજરાતી", "gu", "gu", "guj", "Gujarati", false, "indic"),
    HINDI("hin_Deva", "Hindi", "हिन्दी", "hi", "hi", "hin", "Devanagari", false, "indic"),
    KANNADA("kan_Knda", "Kannada", "ಕನ್ನಡ", "kn", "kn", "kan", "Kannada", false, "indic"),
    KASHMIRI("kas_Arab", "Kashmiri", "کٲشُر", "ks", "ks", "kas", "Perso-Arabic", true, "indic"),
    KONKANI("gom_Deva", "Konkani", "कोंकणी", "kok", "kok", "gom", "Devanagari", false, "indic"),
    MAITHILI("mai_Deva", "Maithili", "मैथिली", "mai", "mai", "mai", "Devanagari", false, "indic"),
    MALAYALAM("mal_Mlym", "Malayalam", "മലയാളം", "ml", "ml", "mal", "Malayalam", false, "indic"),
    MANIPURI("mni_Beng", "Manipuri (Meitei)", "মৈতৈলোন্", "mni", "mni", "mni", "Bengali-Meitei", false, "indic"),
    MARATHI("mar_Deva", "Marathi", "मराठी", "mr", "mr", "mar", "Devanagari", false, "indic"),
    NEPALI("npi_Deva", "Nepali", "नेपाली", "ne", "ne", "npi", "Devanagari", false, "indic"),
    ODIA("ory_Orya", "Odia", "ଓଡ଼ିଆ", "or", "or", "ory", "Odia", false, "indic"),
    PUNJABI("pan_Guru", "Punjabi", "ਪੰਜਾਬੀ", "pa", "pa", "pan", "Gurmukhi", false, "indic"),
    SANSKRIT("san_Deva", "Sanskrit", "संस्कृतम्", "sa", "sa", "san", "Devanagari", false, "indic"),
    SANTALI("sat_Olck", "Santali", "ᱥᱟᱱᱛᱟᱲᱤ", "sat", "sat", "sat", "Ol Chiki", false, "indic"),
    SINDHI("snd_Arab", "Sindhi", "سنڌي", "sd", "sd", "snd", "Perso-Arabic", true, "indic"),
    TAMIL("tam_Taml", "Tamil", "தமிழ்", "ta", "ta", "tam", "Tamil", false, "indic"),
    TELUGU("tel_Telu", "Telugu", "తెలుగు", "te", "te", "tel", "Telugu", false, "indic"),
    URDU("urd_Arab", "Urdu", "اردو", "ur", "ur", "urd", "Perso-Arabic", true, "indic"),

    // --- 11 Major Global Languages ---
    ENGLISH("eng_Latn", "English", "English", "en", "en", "eng", "Latin", false, "global"),
    SPANISH("spa_Latn", "Spanish", "Español", "es", "es", "spa", "Latin", false, "global"),
    JAPANESE("jpn_Jpan", "Japanese", "日本語", "ja", "ja", "jpn", "Japanese", false, "global"),
    CHINESE("zho_Hans", "Chinese (Simplified)", "中文 (简体)", "zh", "zh", "zho", "Han (Simplified)", false, "global"),
    FRENCH("fra_Latn", "French", "Français", "fr", "fr", "fra", "Latin", false, "global"),
    GERMAN("deu_Latn", "German", "Deutsch", "de", "de", "deu", "Latin", false, "global"),
    RUSSIAN("rus_Cyrl", "Russian", "Русский", "ru", "ru", "rus", "Cyrillic", false, "global"),
    ARABIC("ara_Arab", "Arabic", "العربية", "ar", "ar", "ara", "Arabic", true, "global"),
    KOREAN("kor_Hang", "Korean", "한국어", "ko", "ko", "kor", "Hangul", false, "global"),
    PORTUGUESE("por_Latn", "Portuguese", "Português", "pt", "pt", "por", "Latin", false, "global"),
    ITALIAN("ita_Latn", "Italian", "Italiano", "it", "it", "ita", "Latin", false, "global");

    val floresTag: String get() = code

    val fullDisplayName: String
        get() = if (displayName == nativeName) displayName else "$displayName ($nativeName)"

    fun isIndic(): Boolean = category == "indic"

    fun isGlobal(): Boolean = category == "global" || this == ENGLISH

    fun isAutoDetect(): Boolean = this == AUTO_DETECT

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            val clean = code.trim().lowercase()
            if (clean == "auto" || clean == "autodetect" || clean == "auto-detect") return AUTO_DETECT
            if (clean == "bho" || clean == "bho_deva") return HINDI
            return entries.find {
                it != AUTO_DETECT && (
                    it.code.equals(clean, ignoreCase = true) ||
                    it.asrCode.equals(clean, ignoreCase = true) ||
                    it.iso639_1.equals(clean, ignoreCase = true) ||
                    it.iso639_3.equals(clean, ignoreCase = true) ||
                    it.name.equals(clean, ignoreCase = true) ||
                    it.displayName.equals(clean, ignoreCase = true)
                )
            } ?: HINDI
        }

        /**
         * For Speech input, AUTO_DETECT is available at the very top.
         */
        val speechSourceLanguages: List<SupportedLanguage> = listOf(
            AUTO_DETECT,
            HINDI, ENGLISH, BENGALI, TAMIL, TELUGU, MARATHI, GUJARATI, KANNADA, MALAYALAM, PUNJABI, URDU,
            SPANISH, JAPANESE, CHINESE, FRENCH, GERMAN, RUSSIAN, ARABIC, KOREAN, PORTUGUESE, ITALIAN
        )

        /**
         * Real languages (excluding AUTO_DETECT) for translation targets and text mode.
         */
        val realLanguages: List<SupportedLanguage> = entries.filter { it != AUTO_DETECT }

        val allLanguages: List<SupportedLanguage> = realLanguages

        val indicLanguages: List<SupportedLanguage> = entries.filter { it.isIndic() }

        val globalLanguages: List<SupportedLanguage> = entries.filter { it.isGlobal() }

        val rtlLanguages: List<SupportedLanguage> = entries.filter { it.isRtl }
    }
}
