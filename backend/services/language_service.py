from typing import Dict, List, Optional

class LanguageService:
    """
    Manages supported Indic and Global languages, ISO codes, scripts, and Flores-200 / IndicTrans2 tags.
    Supports all 22 official scheduled Indian languages + Top 10 Global languages.
    """
    LANGUAGES: Dict[str, Dict] = {
        # --- Top Global Languages ---
        "en": {
            "name": "English",
            "native": "English",
            "tag": "eng_Latn",
            "category": "global",
            "script": "Latin"
        },
        "es": {
            "name": "Spanish",
            "native": "Español",
            "tag": "spa_Latn",
            "category": "global",
            "script": "Latin"
        },
        "ja": {
            "name": "Japanese",
            "native": "日本語",
            "tag": "jpn_Jpan",
            "category": "global",
            "script": "Japanese (Kanji/Kana)"
        },
        "zh": {
            "name": "Chinese (Simplified)",
            "native": "中文 (简体)",
            "tag": "zho_Hans",
            "category": "global",
            "script": "Han (Simplified)"
        },
        "fr": {
            "name": "French",
            "native": "Français",
            "tag": "fra_Latn",
            "category": "global",
            "script": "Latin"
        },
        "de": {
            "name": "German",
            "native": "Deutsch",
            "tag": "deu_Latn",
            "category": "global",
            "script": "Latin"
        },
        "ru": {
            "name": "Russian",
            "native": "Русский",
            "tag": "rus_Cyrl",
            "category": "global",
            "script": "Cyrillic"
        },
        "ar": {
            "name": "Arabic",
            "native": "العربية",
            "tag": "ara_Arab",
            "category": "global",
            "script": "Arabic"
        },
        "ko": {
            "name": "Korean",
            "native": "한국어",
            "tag": "kor_Hang",
            "category": "global",
            "script": "Hangul"
        },
        "pt": {
            "name": "Portuguese",
            "native": "Português",
            "tag": "por_Latn",
            "category": "global",
            "script": "Latin"
        },
        "it": {
            "name": "Italian",
            "native": "Italiano",
            "tag": "ita_Latn",
            "category": "global",
            "script": "Latin"
        },

        # --- All 22 Indian Scheduled Languages ---
        "hi": {
            "name": "Hindi",
            "native": "हिन्दी",
            "tag": "hin_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "bn": {
            "name": "Bengali",
            "native": "বাংলা",
            "tag": "ben_Beng",
            "category": "indic",
            "script": "Bengali"
        },
        "mr": {
            "name": "Marathi",
            "native": "मराठी",
            "tag": "mar_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "te": {
            "name": "Telugu",
            "native": "తెలుగు",
            "tag": "tel_Telu",
            "category": "indic",
            "script": "Telugu"
        },
        "ta": {
            "name": "Tamil",
            "native": "தமிழ்",
            "tag": "tam_Taml",
            "category": "indic",
            "script": "Tamil"
        },
        "gu": {
            "name": "Gujarati",
            "native": "ગુજરાતી",
            "tag": "guj_Gujr",
            "category": "indic",
            "script": "Gujarati"
        },
        "ur": {
            "name": "Urdu",
            "native": "اردو",
            "tag": "urd_Arab",
            "category": "indic",
            "script": "Perso-Arabic"
        },
        "kn": {
            "name": "Kannada",
            "native": "ಕನ್ನಡ",
            "tag": "kan_Knda",
            "category": "indic",
            "script": "Kannada"
        },
        "or": {
            "name": "Odia",
            "native": "ଓଡ଼ିଆ",
            "tag": "ory_Orya",
            "category": "indic",
            "script": "Odia"
        },
        "ml": {
            "name": "Malayalam",
            "native": "മലയാളം",
            "tag": "mal_Mlym",
            "category": "indic",
            "script": "Malayalam"
        },
        "pa": {
            "name": "Punjabi",
            "native": "ਪੰਜਾਬੀ",
            "tag": "pan_Guru",
            "category": "indic",
            "script": "Gurmukhi"
        },
        "as": {
            "name": "Assamese",
            "native": "অসমীয়া",
            "tag": "asm_Beng",
            "category": "indic",
            "script": "Bengali-Assamese"
        },
        "mai": {
            "name": "Maithili",
            "native": "मैथिली",
            "tag": "mai_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "sa": {
            "name": "Sanskrit",
            "native": "संस्कृतम्",
            "tag": "san_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "ne": {
            "name": "Nepali",
            "native": "नेपाली",
            "tag": "npi_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "sat": {
            "name": "Santali",
            "native": "ᱥᱟᱱᱛᱟᱲᱤ",
            "tag": "sat_Olck",
            "category": "indic",
            "script": "Ol Chiki"
        },
        "ks": {
            "name": "Kashmiri",
            "native": "کٲشُر",
            "tag": "kas_Arab",
            "category": "indic",
            "script": "Perso-Arabic"
        },
        "kok": {
            "name": "Konkani",
            "native": "कोंकणी",
            "tag": "gom_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "sd": {
            "name": "Sindhi",
            "native": "سنڌي",
            "tag": "snd_Arab",
            "category": "indic",
            "script": "Perso-Arabic"
        },
        "doi": {
            "name": "Dogri",
            "native": "डोगरी",
            "tag": "doi_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "brx": {
            "name": "Bodo",
            "native": "बर'",
            "tag": "brx_Deva",
            "category": "indic",
            "script": "Devanagari"
        },
        "mni": {
            "name": "Manipuri (Meitei)",
            "native": "মৈতৈলোন্",
            "tag": "mni_Beng",
            "category": "indic",
            "script": "Bengali-Meitei"
        }
    }

    def get_supported_languages(self) -> List[Dict]:
        return [
            {
                "code": code,
                "name": data["name"],
                "native": data["native"],
                "tag": data["tag"],
                "category": data["category"],
                "script": data["script"]
            }
            for code, data in self.LANGUAGES.items()
        ]

    def get_tag(self, code: str) -> str:
        return self.LANGUAGES.get(code, {}).get("tag", "eng_Latn")

    def is_indic(self, code: str) -> bool:
        return self.LANGUAGES.get(code, {}).get("category") == "indic"

    def is_global(self, code: str) -> bool:
        return self.LANGUAGES.get(code, {}).get("category") == "global" or code == "en"

    def is_valid_pair(self, src: str, tgt: str) -> bool:
        return src in self.LANGUAGES and tgt in self.LANGUAGES and src != tgt

    def is_pivot_required(self, src: str, tgt: str) -> bool:
        """
        Determines if an intermediate English pivot is optimal.
        Example: Hindi -> Japanese (hi -> en -> ja) or Spanish -> Tamil (es -> en -> ta).
        """
        if src == tgt or src == "en" or tgt == "en":
            return False
        # If one is Indic and the other is Global (non-English), pivot through English
        if (self.is_indic(src) and self.is_global(tgt)) or (self.is_global(src) and self.is_indic(tgt)):
            return True
        return False

languageService = LanguageService()
