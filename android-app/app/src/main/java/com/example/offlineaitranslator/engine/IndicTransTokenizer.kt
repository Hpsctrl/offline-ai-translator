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

        // Every punctuation symbol the model expects to see as its own BPE token.
        // Using one regex keeps encoding deterministic for both ASCII and
        // Indic/Arabic marks instead of relying on a char-by-char allow list.
        private val PUNCTUATION_SPACING = Regex("([.,!?;:()\"'\\-।؟،])")

        // Decode side: punctuation never keeps a leading space.
        private val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([.,!?;:।؟،])")

        // Covers ASCII whitespace plus NBSP, narrow NBSP, ideographic and line separators.
        private val WHITESPACE_REGEX =
            Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")

        /** Smart quotes and the many Unicode dash forms are normalized before BPE. */
        private fun normalizeQuotesAndDashes(text: String): String {
            var out = text
            for (c in charArrayOf('\u2018', '\u2019', '\u201A', '\u201B', '\u02BC', '`', '\u00B4')) {
                out = out.replace(c, '\'')
            }
            for (c in charArrayOf('\u201C', '\u201D', '\u201E', '\u201F')) {
                out = out.replace(c, '"')
            }
            for (c in charArrayOf('\u2011', '\u2013', '\u2014', '\u2015', '\u2212', '\uFE58', '\uFE63', '\uFF0D')) {
                out = out.replace(c, '-')
            }
            return out
        }

        /**
         * Devanagari is the model's unified internal script. Only other Indic scripts
         * need normalization before BPE lookup; English and Devanagari languages do not.
         */
        private fun needsDevanagariNormalization(srcLang: String): Boolean {
            val code = srcLang.trim()
            if (code.equals("eng_Latn", ignoreCase = true) ||
                code.equals("en", ignoreCase = true) ||
                code.equals("eng", ignoreCase = true)
            ) return false
            val block = IndicScriptConverter.getScriptBlock(code)
            if (block != null) return block != IndicScriptConverter.BLOCK_DEVANAGARI
            // Sinhala is outside the converter's Brahmic block table but is still an Indic script.
            return code.equals("sin_Sinh", ignoreCase = true) ||
                code.equals("sin", ignoreCase = true) ||
                code.equals("si", ignoreCase = true)
        }

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
     * Normalizes whitespace/script, spaces out attached punctuation and prepends
     * the required `<src_lang> <tgt_lang>` tags before BPE lookup.
     */
    fun preprocess(text: String, srcLang: String, tgtLang: String): String {
        // Normalize every kind of Unicode whitespace (including NBSP) first.
        var clean = WHITESPACE_REGEX.replace(text.trim(), " ").trim()

        if (needsDevanagariNormalization(srcLang)) {
            clean = try {
                IndicScriptConverter.toDevanagari(clean, srcLang)
            } catch (t: Throwable) {
                // Unmapped script or converter error: fall back to the original text.
                clean
            }
        }

        // Normalize typographic quotes/dashes, then split every punctuation mark
        // into its own token so a known word + mark never collapses into <unk>.
        clean = normalizeQuotesAndDashes(clean)
        clean = PUNCTUATION_SPACING.replace(clean, " $1 ")
        clean = WHITESPACE_REGEX.replace(clean, " ").trim()

        return "$srcLang $tgtLang $clean"
    }

    /**
     * English vocabulary is case sensitive in dict.SRC.json. A capitalized word
     * that is absent while its lowercase form is present must fold to lowercase
     * before BPE lookup (e.g. "Hello" -> "hello").
     */
    private fun resolveEnglishCase(word: String, srcLang: String): String {
        if (!srcLang.equals("eng_Latn", ignoreCase = true)) return word
        if (word.isEmpty() || !word[0].isUpperCase()) return word
        val lower = word.lowercase()
        if (lower == word) return word
        if (srcVocab.containsKey(word) || srcVocab.containsKey("$METASPACE$word")) return word
        if (srcVocab.containsKey(lower) || srcVocab.containsKey("$METASPACE$lower")) return lower
        return word
    }

    /**
     * Tokenizes preprocessed or raw sentence into input IDs and attention mask.
     */
    fun encode(text: String, srcLang: String, tgtLang: String): TokenizedInput {
        val preprocessed = preprocess(text, srcLang, tgtLang)
        val words = preprocessed.split(' ')
        val tokenIds = mutableListOf<Long>()

        for (rawWord in words) {
            if (rawWord.isEmpty()) continue

            // English: fall back to the lowercase dictionary entry when the
            // capitalized surface form is unknown.
            val word = resolveEnglishCase(rawWord, srcLang)

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
        val pieces = ArrayList<String>(tokenIds.size)
        for (id in tokenIds) {
            if (id == 0 || id == 1 || id == 2 || id == 3) continue // Skip pad, bos, eos, unk
            val token = tgtIdToToken[id]
            if (token != null) {
                pieces.add(token)
            }
        }
        return SPACE_BEFORE_PUNCTUATION.replace(detokenize(pieces.joinToString("")), "$1")
    }

    /**
     * SentencePiece detokenization: "▁" starts a word (a leading one is dropped),
     * punctuation attaches to the preceding word and an opening quote attaches to
     * the following word.
     */
    private fun detokenize(joined: String): String {
        val raw = WHITESPACE_REGEX.replace(joined.replace(METASPACE, " "), " ")
        val noSpaceBefore = setOf(',', '.', '!', '?', ';', ':', '।', ')', ']')
        val result = StringBuilder(raw.length)
        var pendingSpace = false
        var i = 0

        while (i < raw.length) {
            val c = raw[i]
            if (c.isWhitespace()) {
                if (result.isNotEmpty()) pendingSpace = true
                i++
                continue
            }

            if (c == '"' || c == '\'') {
                // A metaspace before a quote marks an opening quote; a bare quote
                // token is a closing one and belongs to the preceding word.
                if (pendingSpace || result.isEmpty()) {
                    if (result.isNotEmpty()) result.append(' ')
                    result.append(c)
                    var j = i + 1
                    while (j < raw.length && raw[j].isWhitespace()) j++
                    i = j // no space after an opening quote
                } else {
                    result.append(c)
                    i++
                }
                pendingSpace = false
                continue
            }

            if (pendingSpace && result.isNotEmpty() && c !in noSpaceBefore) result.append(' ')
            result.append(c)
            pendingSpace = false
            i++
        }
        return result.toString().trim()
    }
}
