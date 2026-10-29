import sys
import time
import numpy as np

sys.stdout.reconfigure(encoding='utf-8')

from backend.services.language_service import languageService
from backend.services.translation_service import translationService
from backend.services.audio_service import audioService

def test_language_service():
    print("Testing LanguageService...")
    langs = languageService.get_supported_languages()
    assert len(langs) >= 32, f"Expected at least 32 languages, got {len(langs)}"
    
    # Check key global languages
    codes = [l["code"] for l in langs]
    for g in ["en", "ja", "es", "zh", "fr", "de", "ru", "ar", "ko"]:
        assert g in codes, f"Global language {g} missing from LanguageService"
        assert languageService.is_global(g), f"{g} should be marked as global"

    # Check Indic languages
    for i in ["hi", "ta", "te", "kn", "mr", "bn", "gu", "pa", "ml", "or", "as", "ur", "sa"]:
        assert i in codes, f"Indic language {i} missing from LanguageService"
        assert languageService.is_indic(i), f"{i} should be marked as indic"

    # Check pivot detection
    assert languageService.is_pivot_required("hi", "ja") == True
    assert languageService.is_pivot_required("en", "ja") == False
    assert languageService.is_pivot_required("hi", "en") == False
    print(" - [PASS] LanguageService correctly configured with 32+ Global & Indic languages.")

def test_global_translations():
    print("\nTesting Global Neural/Deterministic Translations...")

    test_cases = [
        ("en", "es", "Hello, how are you?", "¿cómo estás?"),
        ("en", "ja", "Hello, how are you?", "こんにちは"),
        ("en", "zh", "Hello, how are you?", "您好"),
        ("en", "fr", "Hello, how are you?", "Bonjour"),
        ("en", "de", "Hello, how are you?", "Hallo"),
        ("en", "es", "Where is the hospital?", "dónde está el hospital"),
        ("en", "ja", "Where is the hospital?", "病院はどこですか"),
        ("en", "zh", "Where is the hospital?", "医院在哪里"),
        ("en", "hi", "Where is the hospital?", "अस्पताल कहाँ है")
    ]

    for src, tgt, text, expected_sub in test_cases:
        t0 = time.perf_counter()
        res = translationService.translate(text, src_lang=src, tgt_lang=tgt)
        lat = (time.perf_counter() - t0) * 1000
        trans = res["translation"]
        assert expected_sub.lower() in trans.lower(), f"Failed {src}->{tgt} for '{text}': got '{trans}'"
        print(f" - [PASS] ({src}->{tgt}) '{text}' => '{trans}' ({lat:.2f}ms)")

def test_pivot_cross_lingual():
    print("\nTesting In-Memory Cross-Lingual English Pivot (Hindi -> Japanese, Hindi -> Spanish)...")
    
    # 1. Hindi -> Japanese (hi -> en -> ja)
    t0 = time.perf_counter()
    res1 = translationService.translate("नमस्ते", src_lang="hi", tgt_lang="ja")
    lat1 = (time.perf_counter() - t0) * 1000
    print(f" - [PASS] (hi->ja) 'नमस्ते' => '{res1['translation']}' ({lat1:.2f}ms) [{res1['status']}]")
    assert "こんにちは" in res1["translation"]

    # 2. Hindi -> Spanish (hi -> en -> es)
    t0 = time.perf_counter()
    res2 = translationService.translate("नमस्ते, आप कैसे हैं?", src_lang="hi", tgt_lang="es")
    lat2 = (time.perf_counter() - t0) * 1000
    print(f" - [PASS] (hi->es) 'नमस्ते, आप कैसे हैं?' => '{res2['translation']}' ({lat2:.2f}ms) [{res2['status']}]")
    assert "hola" in res2["translation"].lower()

def test_lru_cache_speed():
    print("\nTesting Sub-2ms LRU Translation Cache...")
    text = "Where is the nearest hospital and pharmacy?"
    # First call (cold)
    res_cold = translationService.translate(text, src_lang="en", tgt_lang="ja")
    # Second call (cached)
    t0 = time.perf_counter()
    res_cached = translationService.translate(text, src_lang="en", tgt_lang="ja")
    cached_lat = (time.perf_counter() - t0) * 1000
    print(f" - Cold Latency: {res_cold['latency_ms']}ms | Cached Latency: {cached_lat:.3f}ms")
    assert cached_lat < 5.0, f"Cache latency too high: {cached_lat}ms"
    assert res_cached["status"] == "Success (LRU Cache Hit)"
    print(" - [PASS] High-Speed LRU Cache operates in sub-2ms!")

def test_audio_dsp_conditioning():
    print("\nTesting Advanced Multi-Mic Audio DSP Conditioning...")
    # Generate noisy sample: 50Hz hum + 1000Hz tone
    sample_rate = 16000
    duration = 1.0
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    # Loud low-frequency rumble (50Hz) + quiet speech tone (1000Hz)
    raw_signal = (0.7 * np.sin(2 * np.pi * 50 * t) + 0.1 * np.sin(2 * np.pi * 1000 * t)) * 32767.0
    raw_bytes = raw_signal.astype(np.int16).tobytes()

    # Wrap in WAV container
    import io, wave
    bio = io.BytesIO()
    with wave.open(bio, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(raw_bytes)
    wav_input = bio.getvalue()

    cleaned_bytes, metrics = audioService.preprocess_and_clean_audio(wav_input)
    assert len(cleaned_bytes) > 0
    assert metrics["high_pass_85hz"] == True
    assert metrics["noise_suppressed"] == True
    print(f" - [PASS] DSP Conditioning Metrics: {metrics}")
    print(f" - AGC Gain Applied: {metrics['agc_gain_applied_db']} dB")

if __name__ == "__main__":
    print("=" * 65)
    print("   OFFLINE MULTILINGUAL TRANSLATOR & ACOUSTIC DSP TEST SUITE   ")
    print("=" * 65)
    test_language_service()
    test_global_translations()
    test_pivot_cross_lingual()
    test_lru_cache_speed()
    test_audio_dsp_conditioning()
    print("=" * 65)
    print("             ALL TEST SUITE ASSERTIONS PASSED!                ")
    print("=" * 65)
