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
        private const val MAX_NEW_TOKENS = 128
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

        val numCores = maxOf(4, Runtime.getRuntime().availableProcessors())
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(numCores)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
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
        var currentTokenId = tok.bosId.toLong()
        var currentPastTensors = mutableMapOf<String, OnnxTensor>()

        try {
            for (step in 0 until MAX_NEW_TOKENS) {
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
                    decInputs.putAll(currentPastTensors)
                    decPastSession.run(decInputs)
                }

                // Extract logits and pick greedy next token
                val logitsTensor = decResult.get(0) as OnnxTensor
                val logits = logitsTensor.value as Array<Array<FloatArray>>
                val nextTokenScores = logits[0][logits[0].size - 1]

                var maxIdx = 0
                var maxScore = Float.NEGATIVE_INFINITY
                for (i in nextTokenScores.indices) {
                    if (nextTokenScores[i] > maxScore) {
                        maxScore = nextTokenScores[i]
                        maxIdx = i
                    }
                }

                outputIds.add(maxIdx)
                currentTokenId = maxIdx.toLong()

                // If end-of-sequence, stop decoding
                if (maxIdx == tok.eosId) {
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
