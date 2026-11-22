# Offline AI Translator — Project Context & Technical Handoff

> Evidence-based discovery report prepared for a follow-up AI assistant. **Do not treat the owner's statements or the repository README as verified facts.** Labels used throughout: **[Verified]** = directly inspected in code/config, **[Owner claim]** = stated in the task brief or README without runtime proof, **[Inference]** = logical conclusion from inspected code, **[Unknown]** = could not be established.
>
> No application source, configuration, or git state was modified. No tests were executed because the required Android SDK is not installed (see §8). No network-enabled or paid operations were performed. Web research was read-only.

---

## 1. Project overview and owner-provided goals

### Owner-provided status (to be verified)
- A working Android APK prototype exists with a basic UI.
- No network permission, for privacy.
- A basic emotion detector exists.

### Owner-provided planned features
- Offline text-to-speech (TTS) and speech-to-text (STT).
- Translate text from an image (OCR).
- Grammar correction, including disfluencies and broken grammar.
- Hardcoded emergency phrases.
- Chat history.
- Polished UI.

### Owner-provided context
- Possible user segments: buyer-to-buyer; tourists/interstate travellers/pilgrims; disaster relief; rural and semi-urban communities; defence/intelligence/aerospace; banking and financial services; remote mining and deep-sea vessels; healthcare and pharma; legal consulting.
- Partner technology choice: **Sarvam AI**.
- Definition of done (DoD): (a) users can run the app locally without internet; (b) users can translate and hear speech across the languages provided in the app; (c) planned features work end to end.

### What the repository claims (not owner-verified)
`README.md` describes a "100% on-device neural voice and text translation engine" with Whisper Tiny ASR, Emotion2Vec, IndicTrans2 (200M) neural MT with English pivot, and Piper VITS TTS across **33 languages**, plus a table of "measured" performance benchmarks (`README.md:9-22, 44-61, 64-72, 106-118`). **[Verified]** All of those README claims were introduced in the *last* commit (`3aec433`, "docs: modernize and professionalize README…") which changed **only** `README.md` (135 insertions, 100 deletions) — no source, model, or test changes accompanied them. They are documentation claims, not independently verified measurements.

### High-level conclusion
**[Verified]** The repository contains a substantial, coherently written Android/Compose prototype plus a Python dev simulator and web dashboard. **[Inference]** As checked out, it is **not** a working offline neural translator: every ONNX model weight file is absent (gitignored), so real ASR, neural MT, Piper TTS, and emotion inference cannot execute. What remains functional is a deterministic phrasebook/word-substitution translator, audio DSP/recording, script transliteration, and a fallback to the Android system TTS. This is the single most important blocker for the DoD.

---

## 2. Repository snapshot and inspection limitations

| Item | Value |
|---|---|
| Remote | `https://github.com/Hpsctrl/offline-ai-translator.git` |
| Branch inspected | `main` |
| Commit inspected | `3aec43360e36bbe8696c0ea539244658bee7535a` (2026-10-29, docs-only README update) |
| Total commit history | **2 commits**: `4bca9b8` (initial release v1.6) and `3aec433` (README only) |
| Working tree | Clean except the untracked `PROJECT_CONTEXT.md` report requested by the owner |
| Repo size inspected | All source, config, scripts, tests, res files and model *metadata* were inspected. Large binary blobs (JNI `.so`, `.aar`, `.jar`, sample `.wav`) were enumerated by name/size, not disassembled. Tokenizer/dictionary JSON (up to ~35 MB each) was not read line-by-line; keyword searches were used. No `.onnx` files exist to inspect. |

### Inspection limitations
- **[Verified]** No `.apk` or `.aab` exists in the repository (`*.apk`/`*.aab` are gitignored, `.gitignore:5-6`). The owner's "working APK prototype" could not be run or inspected; whether a private build contains the missing weights is **[Unknown]**.
- **[Verified]** No Android SDK is installed in this workspace (`ANDROID_HOME`/`ANDROID_SDK_ROOT` unset; no SDK directories found), so the app was **not built and no tests were run**. Java 26 and Gradle 9.1.0 (cached wrapper distribution) are present; `./gradlew --version` succeeded.
- **[Verified]** Six Kotlin compiler error logs are committed at `android-app/.kotlin/errors/errors-*.log`; they contain incremental-cache/daemon failures from the original developer's machine (paths under `C:\Users\HPS\Downloads\…`), not a clean build transcript. They are evidence of past build trouble, not proof of current build failure.
- **[Verified]** A pre-existing 356 KB `PROJECT_CONTEXT.md` (a verbatim file dump titled "Generated for external UI architect review") was present and untracked. It was backed up to `C:\Users\abdul\AppData\Local\Temp\opencode\PROJECT_CONTEXT_verbatim_dump_backup_20261122.md` before being replaced by this report.

---

## 3. Architecture, data flow, and important files

### 3.1 Repository layout (verified)

```
offline-ai-translator/
├── android-app/                         Native Android client (Kotlin, Jetpack Compose, Material 3)
│   ├── app/build.gradle.kts             AGP 9.0.1, compileSdk 36, minSdk 26, arm64 only
│   ├── gradle/libs.versions.toml        Version catalog (Kotlin 2.3.20, ORT 1.19.2, Compose BOM 2026.03.01)
│   ├── app/libs/                        classes.jar + sherpa-onnx-static-link-onnxruntime-1.13.8.aar (~36.9 MB)
│   ├── app/src/main/jniLibs/            libsherpa-onnx-jni.so for 4 ABIs; libonnxruntime.so (x86)
│   ├── app/src/main/assets/models/      Tokenizers/dictionaries/espeak data ONLY — no .onnx weights
│   ├── app/src/main/java/…/audio/       AudioRecorder, AudioDspEngine, AudioUtils
│   ├── app/src/main/java/…/engine/      TranslationManager, IndicTrans2*, Sherpa*, Emotion*, AssetExtractor, …
│   ├── app/src/main/java/…/ui/main/     MainScreen, MainScreenViewModel, TruckArtComponents
│   └── app/src/test/ + androidTest/     Unit + stale instrumentation tests
├── backend/                             Python FastAPI local dev simulator / "edge engine"
│   ├── app.py                           FastAPI app (root path is app.py, NOT main.py)
│   ├── adapters/                        asr_indic_conformer, translation_indictrans2, tts_piper_expressive, emotion2vec, base
│   ├── services/                        model_manager, audio_service, benchmark_service, language_service, …
│   ├── download_*.py                    Scripts that fetch model files from Hugging Face URLs
│   ├── data/quality_test_dataset.json + data/emotion_samples/*.wav
│   └── tests/                           Python tests (require real models)
├── frontend/                            Web telemetry/dev dashboard (index.html + js/ + css/)
├── README.md, LICENSE (Apache-2.0), .gitignore
```

### 3.2 Android architecture and actual text-translation data flow

Entry point: `MainActivity.kt:17-42` requests `RECORD_AUDIO` at runtime (`MainActivity.kt:19-22, 27, 35-37`) and renders `MainNavigation` → the single `Main` destination (`Navigation.kt:20-27`; `NavigationKeys.kt:6`). `MainScreen.kt` has **exactly three tabs**: `🎙️ Voice`, `💬 Text`, `⚙️ Models` (`MainScreen.kt:167-200`). All state lives in one `MainScreenViewModel` `StateFlow<MainUiState>`; there is no repository-backed data layer (`DataRepository.kt:11` is an unused template stub emitting `listOf("Android")`).

**Text flow** (`MainScreen.kt:681-695` → `MainScreenViewModel.kt:167-197` → `TranslationManager.kt:133-214`):
```
input text
 → Tier 0 LRU cache (max 4096; pre-seeded from ALL_PHRASEBOOKS: TranslationManager.kt:64-78, 437-447)
 → if neither lang is English: two-step English pivot (L181-202)
 → executeDirectTranslation (L216-251):
      IF Indic→En and <filesDir>/models/translation/indictrans2-indic-en-dist-200m/encoder_model.onnx EXISTS
         → real IndicTrans2 ONNX inference (L222-230)
      ELSE IF En→Indic and matching encoder_model.onnx EXISTS → real inference (L231-240)
      → offlineUniversalTranslate() (L242-250): hardcoded phrasebook → keyword intent
        → Indic script transliteration → word-by-word lexicon → pass-through (L290-433)
 → result cached (L212); latency for the deterministic path is hardcoded to 1 ms (L248)
```
**[Verified]** Because no `encoder_model.onnx` exists in the repo, the real-neural branches at `TranslationManager.kt:222-240` are never taken as checked out. The deterministic path is what actually runs.

**Speech flow** (`SpeechPipelineManager.processSpeechAudio`, L127-287):
```
AudioRecorder.recordAudio() → AudioDspEngine.process() + trimSilence (L150-151)
 → silence guard (L154-168)
 → parallel: Emotion2Vec analyzeEmotion (L178-185, failures → null + neutral prosody)
             SherpaWhisper transcribe (L187-192, failures PROPAGATE — not caught here)
 → transcript → TranslationManager.translate (L230) → EmotionProsody.fromEmotion (L234)
 → Piper TTS attempt (L243-266, failures only produce a text note)
 → playback: English → AudioTrack; non-English → Android system TextToSpeech (L289-302)
```
**[Verified]** `SherpaWhisperEngine.load()` throws `NoSuchFileException` for missing `tiny-encoder.int8.onnx`/`tiny-decoder.int8.onnx` (`SherpaWhisperEngine.kt:34-44`); the await at `SpeechPipelineManager.kt:191` has no local catch, so the speech flow aborts before translation/TTS in this checkout.

**Model provisioning:** `AssetExtractor.kt:50-152` copies everything under `assets/models/` to `context.filesDir/models/` on first launch (marker `.extraction_complete_v3`, L35/141). `build.gradle.kts:51` excludes `onnx`/`data`/`bin`/`ort` from compression, consistent with large weights being intended inside the compiled APK (`.gitignore:22-28` comment says the same).

### 3.3 Important files and their roles

| Path (relative to repo root) | Role |
|---|---|
| `README.md` | Owner/docs claims; performance table and 33-language claim are unverified |
| `android-app/app/src/main/AndroidManifest.xml` | Only `RECORD_AUDIO`; no `INTERNET`; `allowBackup=true` |
| `android-app/app/build.gradle.kts` | SDK versions, ABI filter (`arm64-v8a` only), ORT dependency, test options |
| `android-app/gradle/libs.versions.toml` | Exact dependency versions |
| `…/engine/TranslationManager.kt` (1173 lines) | LRU cache, phrasebooks (`BASE_PHRASEBOOKS` L510-986), lexicons (L1006-1171), neural gate (L222-240), diagnostics (L449-494) |
| `…/engine/IndicTrans2Engine.kt` | Real ORT encoder/decoder inference code; expects `encoder_model.onnx`, `decoder_model.onnx`, `decoder_with_past_model.onnx` (L58-64); files absent |
| `…/engine/IndicTransTokenizer.kt` | Real BPE tokenizer over bundled `dict.*.json`/`piece_scores.tsv`; usable |
| `…/engine/SherpaWhisperEngine.kt` | Real sherpa-onnx ASR + text cleanup heuristics (L135-167); weights absent |
| `…/engine/SherpaPiperEngine.kt` | Real sherpa-onnx VITS TTS; weight `.onnx` absent (L42-59) |
| `…/engine/Emotion2VecEngine.kt` | Real ORT emotion inference + 18-pose→9-emotion map (L34-59); `model.onnx` absent; hardcoded neutral fallback L89-94 |
| `…/engine/SpeechPipelineManager.kt` | Orchestrates DSP → emotion/ASR → MT → TTS → playback; diagnostics L316-386 |
| `…/engine/AndroidTtsEngine.kt` | Android platform `TextToSpeech` fallback with emotion speed/pitch (L34-71) |
| `…/engine/AssetExtractor.kt` | First-launch asset copy to internal storage; no downloads |
| `…/engine/SupportedLanguage.kt` | 33 real languages + `AUTO_DETECT` (enum L3-52); UI speech list only 20 + auto (L85-89) |
| `…/ui/main/MainScreen.kt` (1018 lines) | All UI; 3 tabs; hardcoded "100% locally" claim at L801-802 |
| `…/ui/main/MainScreenViewModel.kt` (498 lines) | All flows; single-result state; no history/persistence |
| `backend/app.py`, `backend/config.py`, `backend/adapters/*`, `backend/services/*` | Dev simulator, expects a root-level `models/` dir that does not exist |
| `backend/download_*.py` | Fetch weights from Hugging Face at setup time (not part of Android runtime) |
| `frontend/index.html`, `frontend/js/*` | Telemetry/quality-test dashboard; loads Google Fonts from a CDN |
| `backend/adapters/tts_piper_expressive.py` | TTS adapter that tries **cloud `edge_tts` then cloud `gTTS` before local sherpa/Piper** (L190-294) — contradicts "zero cloud TTS fallback" docstrings |
| `backend/adapters/emotion2vec.py`, `backend/services/model_manager.py` | Backend emotion adapter strictly requires ORT + `model.onnx` (L94-109); `model_manager.py:332,177-181` loads it at import → importing `backend.app` fails in a fresh checkout |

### 3.4 Backend / web dashboard reality (verified)

- The Python component is a **FastAPI dev simulator**, not part of the Android app: no `INTERNET` permission, no `localhost`/`:8000`/HTTP client references anywhere in Android source; Android extracts its own models from APK assets. **[Verified]** Removing the backend/frontend would not affect Android functionality.
- `backend/app.py:59-169` exposes the main `POST /api/pipeline/translate-audio` pipeline and 20+ other endpoints. `backend/config.py:18-21` sets `OFFLINE_MODE_STRICT = True` and `127.0.0.1:8000`, but **[Verified]** `OFFLINE_MODE_STRICT` is never read and the `offline_state` flags (`backend/app.py:36-42`) are only reported/mutated by status/toggle endpoints — they enforce nothing. `frontend/js/offlineMode.js:53-79` prints hard-coded `[PASS]` strings without probing the network.
- **[Verified]** With no weights present, backend ASR falls back to a **duration-based canned-phrase generator** (`backend/adapters/asr_indic_conformer.py:195-196, 326-424`) and translation to a phrasebook/lexicon (`translation_indictrans2.py:275-737`). The emotion adapter is the one strict adapter and raises without ORT/`model.onnx` (`emotion2vec.py:94-109`) — combined with eager loading at `model_manager.py:177-181`, **the backend cannot be imported as checked out** (inference from code, not executed).
- **[Verified]** The quality-test API writes results back into the tracked `backend/data/quality_test_dataset.json` (`backend/app.py:417-418, 453-454`). Model "import" posts metadata only, no file upload (`frontend/js/modelManager.js:185-205`). The frontend's default sample `sample_hi_phr1_pharmacy.wav` (`frontend/js/translator.js:394`) is not in the backend sample directories → 404 (`backend/app.py:95`).
- **[Verified]** Download scripts are standalone (`if __name__ == "__main__"`), fetch only from Hugging Face, and are never called automatically at startup.

---

## 4. Feature-status table (evidence and test status)

| Feature | Owner claim / request | Actually in code | Status | Offline? | Verification done |
|---|---|---|---|---|---|
| **Working APK prototype** | Exists | Code exists; no `.apk` in repo; build not run (no SDK); committed Kotlin cache-error logs | **Unclear / unverified** | Unknown | Inspected repo only; could not run |
| **Basic UI** | Exists | Jetpack Compose 3 tabs, language selectors, waveform, result cards, extraction progress, model-status cards (`MainScreen.kt:167-853`) | **Complete (basic)** | Yes (UI only) | Code inspection; UI subagent |
| **No network permission** | Exists | Manifest has only `RECORD_AUDIO` (`AndroidManifest.xml:4`); no HTTP client classes in Android source | **Complete for Android app** | Yes | Verified in manifest + grep |
| **Emotion detector** | "Basic" | Real Emotion2Vec ORT code but `model.onnx` absent → exception caught → `emotionResult=null` → neutral prosody; silent/short audio returns hardcoded neutral 0.90 (`Emotion2VecEngine.kt:62-94`) | **Partial / effectively stubbed out** | Intended local; not verifiable | Code inspection |
| **Offline STT (speech-to-text)** | Planned | Real Whisper sherpa-onnx code; both INT8 ONNX files absent → `NoSuchFileException` → speech pipeline aborts | **Missing (code only)** | Intended local; non-functional | Code inspection |
| **Offline TTS** | Planned | Real Piper VITS code (weights absent) + Android system `TextToSpeech` fallback in text mode (`MainScreenViewModel.kt:440-468`); speech-pipeline Piper failure only sets a note (`SpeechPipelineManager.kt:243-266`) | **Partial** | System-TTS offline behavior device-dependent | Code inspection |
| **Text translation** | Planned / DoD | Works via pre-seeded phrasebook + keyword routing + word lexicon + transliteration; neural IndicTrans2 path unreachable without weights. Many languages pass through unchanged | **Partial (deterministic, not neural)** | Yes | Code inspection + tests read (not run) |
| **Translation across 33 languages** | DoD / README | 33 real languages enumerated (`SupportedLanguage.kt:18-52`); lexicons cover ~20; speech source list only 20+auto; many pairs degrade to word substitution or pass-through | **Partial / claim overstated** | Yes | Code inspection |
| **Translate text from image (OCR)** | Planned | No image picker, no camera permission, no bitmap/OCR code anywhere in Android source; the only `GetContent()` is `audio/*` (`MainScreen.kt:90, 257`) | **Missing** | n/a | Verified by grep + UI inspection |
| **Grammar correction / disfluencies** | Planned | No feature, no "disfluency" references. Closest: Whisper hallucination/repetition cleanup regex (`SherpaWhisperEngine.kt:135-167`) | **Missing** | n/a | Verified |
| **Hardcoded emergency phrases** | Planned | No UI or data structure; only single words/phrases inside the translation dictionaries (`TranslationManager.kt:517-520, 1013-1103`) | **Missing as a feature / partial as data** | Yes (dictionary) | Verified |
| **Chat history** | Planned | No message list, no DataStore/Room/SharedPreferences/file writes of user text; each result overwrites one state slot; `DataRepository.kt` unused stub; backup rules empty and unwired | **Missing** | n/a | Verified |
| **Polished UI** | Planned | Basic 3-tab UI exists; truck-art palette and 5 decorative composables defined but unused (`Color.kt`, `TruckArtComponents.kt`); theme uses a different minimal palette | **Partial** | Yes | Verified |
| **Sarvam AI integration** | Partner technology | **Zero** occurrences of "sarvam" in any source, config, test, or doc file | **Missing** | n/a | Verified by repo-wide case-insensitive grep |

### Test status (read, not executed)
**[Verified]** ~41 unit tests across 10 files under `android-app/app/src/test/`. Self-contained and likely passing (inference): `IndicScriptConverterTest` (7), `TranslationManagerTest` (6), `UniversalTranslationAdversarialTest` (10), `AudioDspEngineTest` (5), `AudioDspAdversarialStressTest` (5), `MainScreenViewModelTest` (1, only tests the data class), `SherpaWhisperSanitizationTest` (5 — tests *private copies* of production functions, not production code), `ArtisticIndianUiStressTest` (9 — several test removed algorithms or self-invented constants). **Known-broken:** `IndicTrans2EngineTest.kt:16,35,67` and `Emotion2VecEngineTest.kt:16,27,30` assert existence of `../../models/...` paths that do not exist → will fail; `ArtisticIndianUiStressTest.kt:222-250` asserts `versionCode=5`/`versionName="1.4-artistic-indian"` while `build.gradle.kts:15-16` has `7`/`"1.6"` → will fail. A stale instrumentation test asserts UI strings no longer present. None were run (no Android SDK).

---

## 5. Languages, models, dependencies, and device requirements

### Languages (verified code)
- **33 real languages** + `AUTO_DETECT` in `SupportedLanguage.kt:3-52`: 22 Indian scheduled languages (L18-39) and 11 global languages — English, Spanish, Japanese, Chinese (Simplified), French, German, Russian, Arabic, Korean, Portuguese, Italian (L42-52). Note the README lists "Mandarin Chinese"; the enum uses `zho_Hans`.
- Speech source picker exposes only `AUTO_DETECT` + 10 Indic + 10 global (L85-89); text mode uses all 33 (L94).
- Lexicons exist for only ~20 languages (`TranslationManager.kt:1007-1157`); for missing languages (e.g. Assamese, Odia, Nepali, Sanskrit, Sindhi, Kashmiri, Santali, Bodo, Dogri, Maithili, Manipuri) word-level translation returns the original word unchanged (`L394, L421`). Unknown/auto language codes fall back to Hindi (`SupportedLanguage.kt:66-80`).
- **[Inference]** "33 supported languages" is an enum/UI claim; functional translation quality differs radically by pair.

### Models and assets (verified)
- Asset tree: **388 files, ~93.21 MB total** — translation 74.96 MB (tokenizer/dictionary JSON, `model.SRC`/`model.TGT` are SentencePiece vocab protobufs, not weights), TTS 17.17 MB (espeak-ng data + tokens), ASR 0.86 MB (tokens only), emotion ~0 MB (meta.json + tokens), 3 sample WAVs (0.23 MB).
- **No `.onnx` files anywhere.** `.gitignore:22-28` excludes `*.onnx`, `*.onnx.data`, `*.data`, `*.bin`, `*.pt`, `*.tflite`, `*.safetensors` with the comment "Large neural weights (bundled inside compiled APK)". Expected-but-absent weights: `encoder_model.onnx`/`decoder_model.onnx`/`decoder_with_past_model.onnx` (×2 translation dirs), `tiny-encoder.int8.onnx` + `tiny-decoder.int8.onnx`, `hi_IN-pratham-medium.onnx` (or any `.onnx` in TTS dir), `emotion/…/model.onnx` (meta.json also references `out/distill-student-v4.fused.onnx` and a sha256, which the Kotlin code does not verify).
- Native libs present: `app/libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar` (38,691,998 bytes ≈ 36.9 MB) and `classes.jar` (238,706 B); `jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsherpa-onnx-jni.so` plus `x86/libonnxruntime.so`. **[Verified]** `app/build.gradle.kts:75` only adds `*.jar` from `libs/`; the `.aar` is not referenced by any dependency declaration; `jniLibs` are added via sourceSets (L54-58). **[Inference]** This may cause class/packaging confusion on a clean build and should be checked by the implementer.

### Build/runtime dependencies (verified)
- Android: AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, compileSdk/targetSdk 36, minSdk 26, Java 17 source/target, Compose BOM 2026.03.01, Material3, Navigation3, lifecycle, `com.microsoft.onnxruntime:onnxruntime-android:1.19.2`, JUnit 4.13.2, coroutines-test. `abiFilters` = `arm64-v8a` only (`app/build.gradle.kts:23-25`).
- Gradle settings use `FAIL_ON_PROJECT_REPOS`, repositories google() + mavenCentral() (`settings.gradle.kts:1-21`); a clean build requires network access to Maven for AndroidX/Compose/ORT (build-time only, not runtime).
- Backend: FastAPI, Pydantic, CORS wildcard (`allow_origins=["*"]`), uvicorn on `127.0.0.1:8000` (`backend/app.py:9-34`, `backend/config.py:18-21`). **[Verified]** There is **no `requirements.txt`** anywhere in the repo, despite `README.md:173` instructing `pip install -r requirements.txt`, and **no Python versions are pinned**. Implied imports (versions unknown): `fastapi`, `pydantic`, `uvicorn`, `numpy`, `psutil`, `sherpa-onnx`, `onnxruntime`, `soundfile`, `ctranslate2`, `sentencepiece`, `pyttsx3`+`pythoncom` (Windows-only), `indic-transliteration`, `scipy`, **`edge-tts` and `gTTS` (cloud TTS libraries)**, `playwright` (test helper); bundled `translate.py` also expects `tokenizers`, `huggingface-hub`, `IndicTransToolkit`. Download scripts reference Hugging Face model URLs (`backend/download_models.py:55,71,86`; `download_emotion2vec.py:11-13`; `download_indictrans2.py:10`; `download_en_indic.py:10`).
- Frontend: plain HTML/JS/CSS dashboard, loads Google Fonts from `fonts.googleapis.com` (`frontend/index.html:10`) — a network dependency for the *dev dashboard*, not the Android app.

### Device requirements (established / inferred)
- **[Verified]** minSdk 26 → Android 8.0+; packaged native code is arm64-v8a only → requires an ARM64 device/emulator.
- **[Inference]** Install size at least ~93 MB assets + ~24 MB arm64 JNI (plus compression effects), likely >100 MB; on-device RAM during extraction/inference could be significant. The README's "cold start 420 ms / ~380 MB RAM / RTF 0.42" figures (**`README.md:110-118`**) are **unverified claims** — no benchmark was run.
- **[Verified]** `AndroidTtsEngine` requires device-installed voice data; `setLanguage()` returning `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED` only logs a warning (`AndroidTtsEngine.kt:48-50`), so audible output depends on the device/system TTS engine.
- **[Unknown]** Minimum RAM, thermal behavior, supported Android versions beyond API 26, and performance on budget devices.

---

## 6. Offline, privacy, and security findings

### Verified positives
1. `AndroidManifest.xml:2-25` declares only `android.permission.RECORD_AUDIO` — no `INTERNET`, no storage permissions. Runtime request is implemented (`MainActivity.kt:19-22, 35-37`). Audio files are picked via SAF `GetContent()` (`MainScreen.kt:89-95`), so no storage permission is needed.
2. No network client classes/URLs/sockets in Android source (grep for `HttpURLConnection|OkHttp|Retrofit|java.net.URL|Socket|ConnectivityManager|INTERNET` → no matches in `app/src/main/java`). `usesCleartextTraffic="false"` (`AndroidManifest.xml:8`) is set.
3. Model/resources are copied from APK assets into `context.filesDir/models/` by `AssetExtractor.kt:50-152` — no in-app downloader or updater.
4. The only file writes are asset extraction and an extraction marker (`AssetExtractor.kt:99, 141`); user text/audio is not persisted anywhere (no chat history).
5. Audio DSP/recognition inputs stay in process memory; no recording is written to disk by the app.

### Caveats — absence of INTERNET permission is not sufficient proof
1. **[Verified]** No model weights are bundled in the source repo; therefore the "speech runs 100% locally" claim cannot be validated from this checkout. A private APK might include them (owner claim) — this must be verified by inspecting and running that APK.
2. **[Verified]** `AndroidTtsEngine` hands target-language text to Android's platform `TextToSpeech` service (`AndroidTtsEngine.kt:21, 67`). It does not select an offline-only engine or verify that voice data is local. On some devices the chosen TTS engine may be cloud-backed or may have its own network permission; text confidentiality and offline behavior therefore depend on the device. This needs a real-device check.
3. **[Verified]** `allowBackup="true"` (`AndroidManifest.xml:7`); `backup_rules.xml` and `data_extraction_rules.xml` are untouched templates with everything commented out and are **not referenced** by the manifest (no `android:fullBackupContent` / `android:dataExtractionRules`). Today there is no user data to back up, but if chat history is added, this must be addressed (e.g. exclude the history store or disable backup).
4. **[Verified]** Model metadata contains a `sha256` (`emotion/…/meta.json:3`) but `AssetExtractor` validates only file existence/size (`AssetExtractor.kt:111-117`) — no integrity check of extracted weights.
5. **[Verified]** The backend/web dashboard is a separate dev tool, but it is **not offline in practice**: CORS `allow_origins=["*"]` (`backend/app.py:28-34`); hardcoded "offline state" flags (`backend/app.py:36-42`) that enforce nothing, with `frontend/js/offlineMode.js:53-79` printing hard-coded `[PASS]` audit strings; Google Fonts CDN references (`frontend/index.html:8-10`); and, most seriously, the backend TTS adapter tries **Microsoft `edge_tts` first and Google `gTTS` second** before local sherpa TTS (`backend/adapters/tts_piper_expressive.py:190-294`) — sending text to cloud services despite docstrings claiming "Zero cloud TTS fallback" (e.g. `backend/services/tts_service.py:10`). These are not part of the Android app, but the whole repository must not be described as "100% offline" without this qualification.
6. **[Verified]** No API keys, passwords, signing keys, or private conversations were found in the repo (targeted grep). No `local.properties` or keystore files are present.
7. **[Verified]** The Android app is fully decoupled from the backend/frontend: no `localhost`, `127.0.0.1`, `:8000`, `http`, `fetch`, `Retrofit`, `OkHttp` or WebSocket references in `android-app/app/src/main/java/**`. The cloud-TTS caveat above applies to the dev simulator only.

### What must be tested in airplane mode on a real device
1. Cold start + first-launch model extraction with Wi‑Fi/mobile/Bluetooth off; confirm no errors and check `filesDir/models/`.
2. Text translation for representative pairs in each language category; verify output is meaningful and not pass-through.
3. Live mic recording, sample audio chips, and uploaded audio through the full speech pipeline; confirm transcript appears (it currently cannot, without weights).
4. Emotion badge behavior with real speech vs silence (currently defaults to neutral).
5. "Play Audio" in text mode (system TTS) and voice mode (Piper) — confirm audio is produced and that no network traffic appears in a network profiler/logcat.
6. Deny/revoke `RECORD_AUDIO` and verify graceful UI behavior.
7. Confirm no unexpected outbound connections at OS level (Android's own network stats / packet capture), because a missing permission is not a full guarantee for system services.

---

## 7. Sarvam AI findings, sources, and unresolved questions

### 7.1 In-repo integration
**[Verified]** Repo-wide case-insensitive search for "sarvam" across all `.kt`, `.py`, `.js`, `.html`, `.json`, `.kts`, `.toml`, `.xml`, `.md`, `.txt` files returned **no matches**. There is no SDK dependency, API client, endpoint, or key provisioning for Sarvam anywhere. The Android app uses IndicTrans2/Whisper/Piper/Emotion2Vec and Android platform TTS; the backend uses its own adapters and Hugging Face download scripts.

### 7.2 Official sources checked (date: 2026-11-22)
| Topic | Source |
|---|---|
| Docs home / model lineup / language counts | https://docs.sarvam.ai/ |
| API quickstart — API key, `https://api.sarvam.ai/...` endpoints, SDK shape | https://docs.sarvam.ai/api/getting-started/quickstart |
| Mayura translation model (11 languages: 10 Indian + English; 1,000-char limit; hosted `/translate`) | https://docs.sarvam.ai/api/api-guides-tutorials/models/mayura |
| Open `sarvamai/sarvam-translate` model card (4B, GPL-3.0, 23 languages, EN↔Indic only, Gemma3-4B-IT base) | https://huggingface.co/sarvamai/sarvam-translate |
| Sarvam Edge (on-device; Saaras 294 MB + Mayura 334 MB + Bulbul 60 MB; 22 Indic ASR/MT, 11 TTS; commercial partnership) | https://www.sarvam.ai/products/edge |
| Self-hosted deployments (AWS SageMaker in your VPC) | https://docs.sarvam.ai/api/self-hosted/introduction |
| Managed vs self-hosted comparison | https://docs.sarvam.ai/api/self-hosted/hosted-vs-self-hosted |

### 7.3 Evidence-based findings
1. **Hosted APIs are not an offline solution.** Saaras v4 (STT, 23 languages), Bulbul v3/v4 (TTS, 11 languages), Mayura (MT, 11 languages), Sarvam-105B (chat) and Sarvam Vision (OCR/document) are delivered over HTTPS to `api.sarvam.ai` with an `api-subscription-key` ([quickstart](https://docs.sarvam.ai/api/getting-started/quickstart)). Using them would require adding `INTERNET` permission and sending user audio/text off-device — a **direct conflict** with the owner's privacy requirement and DoD.
2. **Self-hosted SageMaker** keeps data in the customer's AWS VPC and supports network isolation/air-gapped endpoints ([source](https://docs.sarvam.ai/api/self-hosted/introduction)), but it is cloud infrastructure, not an on-device phone solution. It still needs infrastructure and is not compatible with "run the app locally without internet" on a phone.
3. **Sarvam Edge genuinely is on-device** — the product page states inference runs entirely on-device, no network call, 22 Indian languages for ASR/MT, 11 for TTS, total stack <1 GB ([source](https://www.sarvam.ai/products/edge)). This aligns with the offline goal. **However:** the same page says the runtime, chipset-optimised variants and enterprise tooling are available "through a commercial partnership", aimed at OEMs; no public Android SDK/Maven artifact was found during this research. Availability, pricing, licensing, and integration path for an independent app developer are **[Unknown]** and require contacting Sarvam.
4. **Open checkpoint `sarvamai/sarvam-translate`**: 4B parameters (BF16), GPL-3.0 license, based on Gemma3-4B-IT; supports 23 languages; the open checkpoint handles **English↔Indian only** and instructs pivot-via-English for Indic→Indic ([model card](https://huggingface.co/sarvamai/sarvam-translate)). It is a server/GPU model (Transformers/vLLM examples); even with community quantizations, running 4B on a phone is a major engineering challenge. GPL-3.0 also raises a licensing question for an Apache-2.0 project (copyleft compatibility) and Gemma's own terms may apply. **[Inference]** Not a practical plug-in replacement for the current 200M IndicTrans2 model.
5. **[Verified conflict]** Sarvam's Edge product page mentions "Cloud overflow — when device capacity is reached, inference overflows to Sarvam's India-hosted cloud", while its FAQ on the same page states "There is no fallback to a cloud API and no dependency on network availability." This contradiction must be clarified with Sarvam before claiming strict offline operation.
6. **Coverage gap:** Sarvam's Indian-language focus does not cover the README's 11 global languages (Spanish, French, German, Chinese, Japanese, Russian, Arabic, Portuguese, Korean, Italian) for ASR/MT. If those remain in scope, Sarvam alone cannot satisfy the full language list.
7. **No grammar-correction, disfluency-removal, or dedicated emergency-phrase product** was identified in the checked Sarvam docs; the chat model (Sarvam-105B) could theoretically do correction but is hosted, not offline. **[Unknown]** whether Sarvam Edge offers any text-correction capability.

### 7.4 Unresolved questions for Sarvam / owner
- Is the owner's "partner technology choice" a signed commercial agreement, an exploratory contact, or just a preference?
- Can Sarvam Edge be licensed/embedded in a third-party Android app (not only OEM devices)? What are the SDK, model distribution, OTA-update, and per-chipset terms?
- Which languages and which models (Saaras/Mayura/Bulbul/Vision) would be licensed, and does the stack fit the target device storage/RAM?
- How is the "cloud overflow" behavior controlled/disabled for a zero-network product?
- If Sarvam is only available as a hosted API, is the owner willing to relax the no-internet requirement (owner decision, not for this agent to make)?

---

## 8. Setup, build, run, and test instructions

### Documented instructions (README.md:147-176, quoted as owner/docs claims)
```bash
# Android
cd android-app
./gradlew assembleDebug
./gradlew assembleRelease
# APK: android-app/app/build/outputs/apk/release/app-release.apk

# Backend simulator
cd backend
python -m venv venv
.\venv\Scripts\activate          # Windows
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```
**[Verified] Corrections needed to those instructions:**
- There is **no `backend/requirements.txt`**; `pip install -r requirements.txt` would fail.
- The FastAPI app object is in **`backend/app.py`**, not `main.py`. Because `backend/app.py:15` imports `from backend.config import …`, it should be launched **from the repository root** as `uvicorn backend.app:app --host 127.0.0.1 --port 8000`. `frontend/js/api.js:3-5` states "Communicates ONLY with 127.0.0.1" but actually sets `API_BASE = window.location.origin`, so the dashboard must be **served from the same origin as the backend**; opening `frontend/index.html` directly as a `file://` page (as `README.md:176` suggests) would not provide a usable API origin.
- README says Android Studio "Koala / Ladybug or newer, Android SDK 26+, NDK 25+" (`README.md:150`); the Gradle files require compileSdk 36 and Gradle 9.1.0.

### Exact toolchain (verified from build files)
- Gradle wrapper 9.1.0 with pinned SHA-256 (`android-app/gradle/wrapper/gradle-wrapper.properties:1-8`); AGP 9.0.1; Kotlin 2.3.20; Java 17 language level; `compileSdk=36`, `targetSdk=36`, `minSdk=26` (`app/build.gradle.kts:7-44`); `onnxruntime-android:1.19.2`; Compose BOM `2026.03.01`.
- `android-app/gradle.properties:1` sets `-Xmx5120m` for the Gradle JVM → build host needs ≥5–6 GB RAM headroom.
- `settings.gradle.kts:15-21` uses `FAIL_ON_PROJECT_REPOS`, google() + mavenCentral().

### Checks actually run in this environment (non-destructive)
| Command | Result |
|---|---|
| `git rev-parse …`, `git status`, `git log`, `git show` | Passed — snapshot recorded above |
| Recursive file enumeration, `.gitignore`, manifest, gradle files, asset-size totals | Passed |
| `java -version` | Java 26 (Oracle) present; `JAVA_HOME` unset |
| `.\gradlew --version` (in `android-app`) | **Passed** — Gradle 9.1.0, daemon JVM Java 26, Windows 11 amd64 |
| Search for Android SDK | **Not found** (`ANDROID_HOME`/`ANDROID_SDK_ROOT` unset; common install paths absent) |
| `./gradlew assembleDebug` / `./gradlew test` | **Not run** — would require installing an Android SDK (API 36) and downloading Maven dependencies; not permitted without asking |
| Python/backend tests | **Not run** — no requirements.txt, expected root `models/` directory absent |
| APK installation / device testing | **Not possible** — no APK and no device in this environment |

### Safe commands for a future implementer (once the SDK exists)
```bash
cd android-app
./gradlew :app:testDebugUnitTest --tests 'com.example.offlineaitranslator.engine.TranslationManagerTest'
./gradlew :app:testDebugUnitTest --tests 'com.example.offlineaitranslator.audio.AudioDspEngineTest'
./gradlew :app:assembleDebug
```
Expect the model-dependent tests (`IndicTrans2EngineTest`, `Emotion2VecEngineTest`) and the version-parity test to fail as checked out. Start with the self-contained suites above.

**Python tests (not run):** `backend/tests/test_global_multilingual_and_audio.py` exercises the dictionary/DSP paths and needs no models; `test_offline_pipeline.py` imports `modelManager` and is therefore subject to the eager emotion-load failure; `test_real_models.py` and `test_indictrans2_real.py` require the absent root `models/` tree (the latter fails at import of `translate.py`). `backend/tests/capture_screens.py` is a Playwright screenshot helper with a hard-coded `C:\Users\HPS\...` output path, not a test. **[Verified from code; not executed]**

---

## 9. Gaps and technical blockers for the definition of done

| Blocker | Evidence | Impact on DoD |
|---|---|---|
| **All ONNX weights missing from source repo** | `.gitignore:22-28`; zero `.onnx` files; mandatory existence checks at `IndicTrans2Engine.kt:58-64`, `SherpaWhisperEngine.kt:34-44`, `SherpaPiperEngine.kt:42-59`, `Emotion2VecEngine.kt:64-67` | No neural ASR/MT/TTS/emotion; speech-to-speech flow aborts; DoD (b) impossible from source |
| **No APK artifact to validate owner's prototype claim** | No `.apk`/`.aab` in repo | Cannot confirm "working APK" or that it contains weights |
| **Model distribution strategy unresolved** | `.gitignore` implies weights shipped inside compiled APK; no downloader in app; no LFS/submodule config | A clean checkout cannot build a functional app; need owner decision on bundling vs first-run download (latter conflicts with "no internet") |
| **Translation is dictionary-based for most pairs** | `TranslationManager.kt:216-250, 290-433`; lexicons cover ~20 languages | "Translate and hear speech across the languages provided" would be misleading/low quality |
| **Speech pipeline broken without weights** | `SpeechPipelineManager.kt:187-192` (ASR exception uncaught) | No transcript → no translation → no neural TTS |
| **TTS offline dependency on device** | `AndroidTtsEngine.kt:21-28, 48-50` | Audible output varies by device; privacy of text handed to system TTS not guaranteed |
| **Planned features absent** | No OCR/image code, no grammar/disfluency code, no emergency-phrases UI/data, no history/persistence (§4) | DoD (c) not met |
| **Sarvam alignment unresolved** | No integration; hosted APIs conflict with offline; Edge commercial; open model GPL-3.0/4B (§7) | Partner choice currently contributes nothing to DoD |
| **Tests broken/stale; no build verification** | §4 test summary; no Android SDK; Kotlin cache-error logs committed | No reliable regression safety net |
| **README overclaims** | 33 languages, benchmark table, "100% local" — docs-only commit `3aec433` | Planning risk; must not be used as acceptance criteria without measurement |
| **Empty backup rules + `allowBackup=true`** | `AndroidManifest.xml:7`; empty res/xml rule stubs | Future history feature could leak user text via backup unless fixed |
| **Build wiring questions** | `.aar` not referenced in Gradle; two `AudioUtils.kt` copies; dead code | Clean-build behavior unknown |
| **Backend simulator cannot start as checked out** | `model_manager.py:177-181,332` eagerly loads emotion adapter, which raises without ORT/`model.onnx` (`emotion2vec.py:94-109`) | Dev/simulator validation impossible without provisioning models |
| **Backend violates its own "zero cloud" claims** | `edge_tts`/`gTTS` attempted before local TTS (`tts_piper_expressive.py:190-294`); cosmetic offline flags; hard-coded audit strings in `offlineMode.js:53-79` | If the owner intends backend/frontend as part of the product, privacy/offline claims are false; Android itself is unaffected |
| **Backend setup instructions broken** | No `requirements.txt`; app file is `app.py` not `main.py`; no pinned Python versions | README quickstart cannot be followed verbatim |

---

## 10. Suggested implementation dependencies and milestones

> **These are suggestions for planning only, not decisions or priorities chosen by this agent.** The owner must decide order and scope.

**Suggested dependencies (ordering rationale, not commitments):**
1. **M0 — Make the checkout buildable and verify the prototype.** Obtain the missing model weights or the private APK; set up Android SDK 36; fix the `.aar`/JNI wiring if needed; establish a clean `assembleDebug` + unit-test run. *Everything else depends on this.*
2. **M1 — Decide the model-distribution contract.** Bundle weights (repo/LFS or APK), or allow an initial download with explicit owner approval. This determines whether DoD (a) is achievable.
3. **M2 — Decide the technology route.** Options to evaluate: keep/fix IndicTrans2+Whisper+Piper; negotiate Sarvam Edge; use other offline Indic models. Hosted Sarvam APIs are only an option if the no-internet requirement is formally relaxed.
4. **M3 — Stabilize text translation + language scope** (owner picks target languages/pairs) with real quality evaluation, then add history, emergency phrases, grammar correction, and OCR as separate increments — each with its own offline verification.
5. **M4 — UI polish and device validation** (airplane-mode matrix from §6), only after M0–M3.

**Suggested milestones with acceptance ideas (all suggestions):**
- MS1: clean checkout builds; self-contained unit tests pass; broken/stale tests fixed or removed.
- MS2: weights present with documented provenance/licence; app extracts and loads them without network.
- MS3: text translation works for the owner's chosen language pairs with measured quality (not dictionary pass-through).
- MS4: mic → transcript → translation → synthesized speech works end to end on an ARM64 device in airplane mode.
- MS5: emotion inference actually runs (not neutral fallback) when the model is present.
- MS6: each planned feature (history, emergency phrases, grammar correction, OCR) shipped behind a clear offline contract, with privacy-safe storage for history.
- MS7: published measured benchmarks replacing the unverified README table.

---

## 11. Most important questions for the owner

1. **Can you provide the private APK and/or the missing model weights** (with provenance and licences), or should we treat model provisioning as new work?
2. **Is an initial one-time model download acceptable**, or must models ship inside the APK/from factory image? (This is the central DoD tension.)
3. **Feature priorities:** of OCR/image translation, grammar correction/disfluency handling, hardcoded emergency phrases, chat history, and polished UI — which must ship first, and which can be dropped for v1?
4. **Target languages/pairs:** are the 33 enum languages real requirements, or is a smaller Indic-focused set acceptable? Which pairs must have neural-quality translation?
5. **Target devices:** specific Android versions, RAM/storage floors, ARM64-only acceptable? Budget phones? Tablets/rugged devices? Any iOS requirement?
6. **Sarvam:** is there a signed partnership or commercial Edge access? Are you willing to relax "no internet" if only hosted APIs are available? If Edge is available, which models/languages and what are the licence terms?
7. **User segments:** for defence/intelligence, healthcare, banking and legal use, are there compliance requirements (data residency, audit, encryption at rest, no cloud TTS) that change the storage/security design for chat history?
8. **History privacy:** should history be ephemeral (in-memory only), on-device encrypted, or user-deletable? Should Android backup be disabled?
9. **TTS:** is Android system TTS acceptable as a fallback (device-dependent and not provably private), or is a bundled offline neural TTS mandatory?
10. **Who owns the build/signing/Release pipeline**, and is the repository expected to become the single source of truth for the shipping APK?
11. **What does "basic emotion detector" mean to you today** — a label that is always neutral (as this checkout would produce) or real inference from audio?
12. **Should the Python backend/web dashboard remain in scope**, or is it only a dev tool? (It currently has no requirements.txt and expects a root `models/` directory.)

---

## 12. Concise handoff summary for the next AI assistant

**Bottom line:** This is a well-structured Android/Compose prototype with a deterministic offline text translator and real inference *code* for Whisper/IndicTrans2/Piper/Emotion2Vec — but **every ONNX weight file is gitignored and absent**, so the neural features cannot run from this checkout. There is no OCR, grammar correction, emergency-phrases feature, chat history, or Sarvam integration. The manifest genuinely has no `INTERNET` permission, but system TTS and device-dependent behavior still need on-device verification.

**Start here (most relevant paths):**
- `android-app/app/src/main/AndroidManifest.xml` — permission truth (only `RECORD_AUDIO`).
- `android-app/app/build.gradle.kts` + `android-app/gradle/libs.versions.toml` — exact SDK/dependency versions, arm64-only ABI.
- `android-app/app/src/main/java/com/example/offlineaitranslator/engine/TranslationManager.kt` — the de-facto translation engine; `translateInternal()` L149-214, `executeDirectTranslation()` L216-251, `offlineUniversalTranslate()` L290-381.
- `…/engine/SpeechPipelineManager.kt:127-287` — speech orchestration and the uncaught ASR failure point.
- `…/engine/{SherpaWhisperEngine,IndicTrans2Engine,SherpaPiperEngine,Emotion2VecEngine,AndroidTtsEngine}.kt` — real code gated by missing weights.
- `…/engine/AssetExtractor.kt:50-152` — first-launch asset copy to `filesDir/models`.
- `…/engine/SupportedLanguage.kt:3-102` — language enum/lists.
- `…/ui/main/MainScreen.kt:167-853` and `…/ui/main/MainScreenViewModel.kt:25-497` — entire UX/state (three tabs, single result slot, no history).
- `.gitignore:22-28` — the model-weight exclusion that explains the whole blocker.
- `backend/app.py`, `backend/config.py`, `backend/adapters/tts_piper_expressive.py:190-294` (cloud TTS first), `backend/services/model_manager.py:177-181` (eager load → startup failure), `backend/download_*.py`, `frontend/index.html` — decoupled dev simulator only; no requirements.txt; not offline.

**Small code excerpts that explain the project:**
```kotlin
// TranslationManager.kt:221-243 — neural MT only if weights exist; otherwise dictionary
if (srcLang.isIndic() && tgtLang == SupportedLanguage.ENGLISH) {
    if (indicToEnDir.exists() && File(indicToEnDir, "encoder_model.onnx").exists()) {
        try { return getOrLoadIndicToEn().translate(text, srcLang.code, tgtLang.code) }
        catch (e: Exception) { AppLog.e("TranslationManager", "… falling back to deterministic engine", e) }
    }
}
val translation = offlineUniversalTranslate(text, srcLang, tgtLang) // phrasebook/lexicon
```
```kotlin
// SherpaWhisperEngine.kt:34-44 — ASR hard-fails in this checkout
val encoderFile = File(modelDir, "tiny-encoder.int8.onnx")
val decoderFile = File(modelDir, "tiny-decoder.int8.onnx")
if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
    throw NoSuchFileException(encoderFile, null, "Whisper INT8 ONNX model files missing …")
}
```
```kotlin
// SpeechPipelineManager.kt:187-192 — ASR failure is not caught here (emotion failure is)
val emotionDeferred = async { try { … } catch (t: Throwable) { null } }
val asrDeferred = async { whisper.transcribe(...) }
val asr = asrDeferred.await()   // exception propagates → no transcript/TTS
```
```kotlin
// AndroidTtsEngine.kt:48-50 — missing offline voices only log a warning
if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
    AppLog.w("AndroidTTS", "Language $locale is not supported or missing data on this device")
}
// … engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) still runs (L67)
```
```kotlin
// MainScreenViewModel.kt:440-468 — the one real TTS fallback (text mode)
try { piper.synthesize(phoneticText) … } catch (t: Throwable) { androidTtsEngine.speak(text, lang, prosody) }
```
```python
# backend/adapters/tts_piper_expressive.py:190-294 — backend tries CLOUD TTS first
# Option 1: edge_tts.Communicate(...)   ← Microsoft cloud (L226, 241/243)
# Option 2: gTTS(...)                   ← Google cloud (L269)
# Option 3: sherpa_onnx.OfflineTts(...) ← local (L281-294)
```

**Rules for the next assistant:** do not treat README benchmarks/language claims as measured; do not assume a cloud API is offline; do not add `INTERNET` without an explicit owner decision; do not invent test results or device support; ask the owner the questions in §11 before choosing priorities. Re-run a fresh discovery if the owner supplies a new APK or model files — the findings above are tied to commit `3aec433`.
