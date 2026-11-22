package com.example.offlineaitranslator.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer

data class TranslationResult(
    val translatedText: String,
    val srcLang: String,
    val tgtLang: String,
    val latencyMs: Long,
    val tokensGenerated: Int
)

class IndicTrans2Engine(
    val modelDir: File,
    val isIndicToEn: Boolean
) : AutoCloseable {

    companion object {
        private const val TAG = "IndicTrans2Engine"
    }

    private var env: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var decoderPastSession: OrtSession? = null
    private var tokenizer: IndicTransTokenizer? = null

    @Volatile
    var isLoaded: Boolean = false
        private set

    val modelSizeMB: Long
        get() {
            var total = 0L
            modelDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
            return total / (1024 * 1024)
        }

    fun load() {
        if (isLoaded) return
        AppLog.i(TAG, "Loading IndicTrans2 from ${modelDir.absolutePath} ...")

        val e = OrtEnvironment.getEnvironment()
        env = e

        // BASIC_OPT plus a small fixed thread pool avoids native RAM spikes that
        // trigger ColorOS o-kill on low-memory devices.
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
        }

        val encFile = File(modelDir, "encoder_model.onnx")
        val decFile = File(modelDir, "decoder_model.onnx")
        val decPastFile = File(modelDir, "decoder_with_past_model.onnx")

        require(encFile.exists()) { "Missing encoder_model.onnx at ${encFile.absolutePath}" }
        require(decFile.exists()) { "Missing decoder_model.onnx at ${decFile.absolutePath}" }
        require(decPastFile.exists()) { "Missing decoder_with_past_model.onnx at ${decPastFile.absolutePath}" }

        encoderSession = e.createSession(encFile.absolutePath, opts)
        decoderSession = e.createSession(decFile.absolutePath, opts)
        decoderPastSession = e.createSession(decPastFile.absolutePath, opts)

        tokenizer = IndicTransTokenizer.loadFromDir(modelDir)
        isLoaded = true
        AppLog.i(TAG, "IndicTrans2 sessions and tokenizer loaded successfully.")
    }

    fun translate(text: String, srcLang: String, tgtLang: String): TranslationResult {
        check(isLoaded) { "IndicTrans2Engine is not loaded" }
        val t0 = System.currentTimeMillis()

        val tok = tokenizer ?: error("Tokenizer not loaded")
        val env = env ?: error("OrtEnvironment not initialized")
        val encSession = encoderSession ?: error("Encoder session not loaded")
        val decSession = decoderSession ?: error("Decoder session not loaded")
        val decPastSession = decoderPastSession ?: error("Decoder with past session not loaded")

        // Transliterate source text into model's unified Devanagari space if needed
        val unifiedInputText = if (srcLang != "eng_Latn") {
            IndicScriptConverter.toDevanagari(text, srcLang)
        } else {
            text
        }

        // 1. Encode text
        val encoded = tok.encode(unifiedInputText, srcLang, tgtLang)
        val seqLen = encoded.inputIds.size
        val maxNewTokens = (seqLen * 2).coerceIn(12, 96)

        val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(encoded.inputIds))
        val attnMaskTensor = OnnxTensor.createTensor(env, arrayOf(encoded.attentionMask))

        // 2. Run encoder
        val encInputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attnMaskTensor
        )
        val encResult = encSession.run(encInputs)
        val encHiddenState = encResult.get(0) as OnnxTensor

        // 3. Autoregressive greedy decoding loop
        val outputIds = mutableListOf<Int>()
        val recentTokens = java.util.ArrayDeque<Int>()
        var currentTokenId = tok.bosId.toLong()
        var currentPastTensors = mutableMapOf<String, OnnxTensor>()

        // no_repeat_ngram_size = 3: reject a token whose last two-token context
        // has already been followed by that same token anywhere in the history.
        fun wouldRepeatTrigram(candidate: Int): Boolean {
            if (recentTokens.size < 2) return false
            val history = recentTokens.toList()
            val a = history[history.size - 2]
            val b = history[history.size - 1]
            for (i in 0 until history.size - 2) {
                if (history[i] == a && history[i + 1] == b && history[i + 2] == candidate) return true
            }
            return false
        }

        try {
            for (step in 0 until maxNewTokens) {
                val decInputs = mutableMapOf<String, OnnxTensor>()
                val currInputIdsTensor = OnnxTensor.createTensor(env, arrayOf(longArrayOf(currentTokenId)))

                val decResult: OrtSession.Result = if (step == 0) {
                    decInputs["input_ids"] = currInputIdsTensor
                    decInputs["encoder_hidden_states"] = encHiddenState
                    decInputs["encoder_attention_mask"] = attnMaskTensor
                    decSession.run(decInputs)
                } else {
                    decInputs["input_ids"] = currInputIdsTensor
                    decInputs["encoder_attention_mask"] = attnMaskTensor
                    // Verified: on every step after the first, the previous step's
                    // present.* outputs (renamed to past_key_values.*) are passed here.
                    decInputs.putAll(currentPastTensors)
                    decPastSession.run(decInputs)
                }

                // Extract logits, rank all candidates, then apply anti-hallucination rules.
                val logitsTensor = decResult.get(0) as OnnxTensor
                val logits = logitsTensor.value as Array<Array<FloatArray>>
                val nextTokenScores = logits[0][logits[0].size - 1]

                val rankedCandidates = nextTokenScores.indices.toList().sortedByDescending { nextTokenScores[it] }
                var nextToken = tok.eosId // fallback: terminate cleanly if nothing is allowed
                for (candidate in rankedCandidates) {
                    if (candidate == tok.eosId) {
                        nextToken = candidate // EOS is never blocked
                        break
                    }
                    if (candidate == currentTokenId.toInt()) continue // no immediate duplicate
                    if (wouldRepeatTrigram(candidate)) continue // no_repeat_ngram_size = 3
                    nextToken = candidate // highest-scoring allowed candidate
                    break
                }

                outputIds.add(nextToken)
                recentTokens.addLast(nextToken)
                currentTokenId = nextToken.toLong()

                // If end-of-sequence, stop decoding
                if (nextToken == tok.eosId) {
                    currInputIdsTensor.close()
                    decResult.close()
                    break
                }

                // Collect present KV cache and map to past_key_values.* for next step
                val nextPastTensors = mutableMapOf<String, OnnxTensor>()
                for (entry in decResult) {
                    val name = entry.key
                    if (name != "logits" && entry.value is OnnxTensor) {
                        val pastName = name.replace("present.", "past_key_values.")
                        nextPastTensors[pastName] = entry.value as OnnxTensor
                    }
                }

                // Clean up previous step's inputs
                currInputIdsTensor.close()
                for ((_, tensor) in currentPastTensors) {
                    tensor.close()
                }
                currentPastTensors = nextPastTensors
            }
        } finally {
            inputIdsTensor.close()
            attnMaskTensor.close()
            encResult.close()
            for ((_, tensor) in currentPastTensors) {
                tensor.close()
            }
        }

        val rawDecoded = tok.decode(outputIds)
        // Transliterate from unified Devanagari space to target script
        val finalOutput = if (tgtLang != "eng_Latn") {
            IndicScriptConverter.fromDevanagari(rawDecoded, tgtLang)
        } else {
            rawDecoded
        }
        val latencyMs = System.currentTimeMillis() - t0

        return TranslationResult(
            translatedText = finalOutput,
            srcLang = srcLang,
            tgtLang = tgtLang,
            latencyMs = latencyMs,
            tokensGenerated = outputIds.size
        )
    }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()
        decoderPastSession?.close()
        env?.close()
        encoderSession = null
        decoderSession = null
        decoderPastSession = null
        env = null
        tokenizer = null
        isLoaded = false
        AppLog.i(TAG, "IndicTrans2Engine closed.")
    }
}
