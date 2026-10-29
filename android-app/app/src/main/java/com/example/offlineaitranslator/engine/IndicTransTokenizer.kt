package com.example.offlineaitranslator.engine

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TokenizedInput
        return inputIds.contentEquals(other.inputIds) && attentionMask.contentEquals(other.attentionMask)
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        return result
    }
}

class IndicTransTokenizer(
    private val srcVocab: Map<String, Int>,
    private val tgtIdToToken: Map<Int, String>,
    private val pieceScores: Map<String, Float>,
    val unkId: Int = 3,
    val eosId: Int = 2,
    val bosId: Int = 2
) {
    companion object {
        const val METASPACE = "\u2581"

        fun loadFromDir(modelDir: File): IndicTransTokenizer {
            val srcVocabFile = File(modelDir, "dict.SRC.json")
            val tgtVocabFile = File(modelDir, "dict.TGT.json")
            val pieceScoresFile = File(modelDir, "piece_scores.tsv")

            require(srcVocabFile.exists()) { "Missing dict.SRC.json at ${srcVocabFile.absolutePath}" }
            require(tgtVocabFile.exists()) { "Missing dict.TGT.json at ${tgtVocabFile.absolutePath}" }
            require(pieceScoresFile.exists()) { "Missing piece_scores.tsv at ${pieceScoresFile.absolutePath}" }

            val srcMap = parseJsonVocab(srcVocabFile.readText(StandardCharsets.UTF_8))
            val tgtJson = JSONObject(tgtVocabFile.readText(StandardCharsets.UTF_8))
            val tgtRev = HashMap<Int, String>(tgtJson.length())
            val keys = tgtJson.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                tgtRev[tgtJson.getInt(token)] = token
            }

            val pieceScores = HashMap<String, Float>(130000)
            pieceScoresFile.forEachLine(StandardCharsets.UTF_8) { line ->
                val tab = line.indexOf('\t')
                if (tab != -1) {
                    val piece = line.substring(0, tab)
                    val score = line.substring(tab + 1).toFloatOrNull() ?: Float.NEGATIVE_INFINITY
                    pieceScores[piece] = score
                }
            }

            return IndicTransTokenizer(
                srcVocab = srcMap,
                tgtIdToToken = tgtRev,
                pieceScores = pieceScores,
                unkId = srcMap["<unk>"] ?: 3,
                eosId = srcMap["</s>"] ?: 2,
                bosId = 2
            )
        }

        private fun parseJsonVocab(jsonStr: String): Map<String, Int> {
            val json = JSONObject(jsonStr)
            val map = HashMap<String, Int>(json.length())
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getInt(k)
            }
            return map
        }
    }

    /**
     * Preprocesses text by spacing out punctuation and prepending language tags.
     */
    fun preprocess(text: String, srcLang: String, tgtLang: String): String {
        var clean = text.trim()
        // Space out punctuation marks
        val punctuation = arrayOf("?", "!", ",", ".", "।", ":", ";", "\"", "'", "(", ")", "[", "]")
        for (p in punctuation) {
            clean = clean.replace(p, " $p ")
        }
        // Normalize whitespaces
        clean = clean.replace("\\s+".toRegex(), " ").trim()
        return "$srcLang $tgtLang $clean"
    }

    /**
     * Tokenizes preprocessed or raw sentence into input IDs and attention mask.
     */
    fun encode(text: String, srcLang: String, tgtLang: String): TokenizedInput {
        val preprocessed = preprocess(text, srcLang, tgtLang)
        val words = preprocessed.split(' ')
        val tokenIds = mutableListOf<Long>()

        for (word in words) {
            if (word.isEmpty()) continue

            // 1. Check if word is language tag or exact match in vocab
            val exactId = srcVocab[word]
            if (exactId != null) {
                tokenIds.add(exactId.toLong())
                continue
            }

            // 2. Prepend metaspace for standard words
            val metaWord = "$METASPACE$word"
            val metaId = srcVocab[metaWord]
            if (metaId != null) {
                tokenIds.add(metaId.toLong())
                continue
            }

            // 3. Greedy BPE piece merging
            val pieces = bpeTokenizeWord(metaWord)
            for (p in pieces) {
                val id = srcVocab[p] ?: unkId
                tokenIds.add(id.toLong())
            }
        }

        // Append EOS token
        tokenIds.add(eosId.toLong())

        val idsArray = tokenIds.toLongArray()
        val maskArray = LongArray(idsArray.size) { 1L }
        return TokenizedInput(idsArray, maskArray)
    }

    /**
     * Greedy BPE symbol merging according to highest piece score.
     */
    private fun bpeTokenizeWord(word: String): List<String> {
        var symbols = word.map { it.toString() }.toMutableList()

        while (symbols.size > 1) {
            var bestPairLeft: String? = null
            var bestPairRight: String? = null
            var bestMerged: String? = null
            var bestScore = Float.NEGATIVE_INFINITY

            for (i in 0 until symbols.size - 1) {
                val left = symbols[i]
                val right = symbols[i + 1]
                val merged = left + right
                val score = pieceScores[merged]
                if (score != null && score > bestScore) {
                    bestScore = score
                    bestPairLeft = left
                    bestPairRight = right
                    bestMerged = merged
                }
            }

            if (bestMerged == null || bestScore == Float.NEGATIVE_INFINITY) {
                break
            }

            val newSymbols = mutableListOf<String>()
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == bestPairLeft && symbols[i + 1] == bestPairRight) {
                    newSymbols.add(bestMerged)
                    i += 2
                } else {
                    newSymbols.add(symbols[i])
                    i += 1
                }
            }
            symbols = newSymbols
        }
        return symbols
    }

    /**
     * Decodes generated token IDs back to a readable string.
     */
    fun decode(tokenIds: List<Int>): String {
        val pieces = mutableListOf<String>()
        for (id in tokenIds) {
            if (id in listOf(0, 1, 2, 3)) continue // Skip pad, bos, eos, unk
            val token = tgtIdToToken[id]
            if (token != null) {
                pieces.add(token)
            }
        }

        val raw = pieces.joinToString("").replace(METASPACE, " ")

        // Post-process spacing around punctuation
        var cleaned = raw
        val punctAttached = arrayOf(",", ".", "!", "?", ":", ";", "।")
        for (p in punctAttached) {
            cleaned = cleaned.replace(" $p", p)
        }
        return cleaned.replace("\\s+".toRegex(), " ").trim()
    }
}
