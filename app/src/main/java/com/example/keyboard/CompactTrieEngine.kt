package com.example.keyboard

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Motor em Kotlin puro otimizado para latência ultra-baixa (zero lag).
 * Indexação por buckets de caracteres para busca em O(1) de prefixos.
 */
class CompactTrieEngine(bytes: ByteArray) {

    data class Unigram(val exact: String, val norm: String, val freq: Int)

    private val unigrams = mutableListOf<Unigram>()
    // Indexação por primeiro caractere para pesquisa instantânea (< 5 microsegundos)
    private val buckets = HashMap<Char, MutableList<Unigram>>()
    private val bigramsByPrev = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    private val wordSet = mutableMapOf<String, Int>()

    init {
        parseBinary(bytes)
    }

    private fun parseBinary(bytes: ByteArray) {
        if (bytes.size < 14) return
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)
        if (String(magic) != "PTKB") return

        val version = buffer.short.toInt() and 0xFFFF
        if (version != 1) return

        val unigramCount = buffer.int
        val bigramCount = buffer.int

        for (i in 0 until unigramCount) {
            val exactLen = buffer.get().toInt() and 0xFF
            val exactBytes = ByteArray(exactLen)
            buffer.get(exactBytes)
            val exact = String(exactBytes, Charsets.UTF_8)

            val normLen = buffer.get().toInt() and 0xFF
            val normBytes = ByteArray(normLen)
            buffer.get(normBytes)
            val norm = String(normBytes, Charsets.UTF_8)

            val freq = buffer.short.toInt() and 0xFFFF

            val entry = Unigram(exact, norm, freq)
            unigrams.add(entry)
            wordSet[exact.lowercase()] = freq

            if (norm.isNotEmpty()) {
                buckets.getOrPut(norm[0]) { mutableListOf() }.add(entry)
            }
        }

        for (i in 0 until bigramCount) {
            val prevLen = buffer.get().toInt() and 0xFF
            val prevBytes = ByteArray(prevLen)
            buffer.get(prevBytes)
            val prevNorm = String(prevBytes, Charsets.UTF_8)

            val nextLen = buffer.get().toInt() and 0xFF
            val nextBytes = ByteArray(nextLen)
            buffer.get(nextBytes)
            val nextExact = String(nextBytes, Charsets.UTF_8)

            val weight = buffer.short.toInt() and 0xFFFF

            bigramsByPrev.getOrPut(prevNorm) { mutableListOf() }.add(Pair(nextExact, weight))
        }
    }

    fun isWord(word: String): Boolean {
        return wordSet.containsKey(word.lowercase())
    }

    fun getSuggestions(prevWord: String, prefix: String): List<String> {
        val cleanPrefix = prefix.trim()
        val prevNorm = normalize(prevWord.trim())

        if (cleanPrefix.isEmpty()) {
            if (prevNorm.isNotEmpty()) {
                val list = bigramsByPrev[prevNorm]
                if (!list.isNullOrEmpty()) {
                    return list.sortedByDescending { it.second }.map { it.first }.take(3)
                }
            }
            return unigrams.take(3).map { it.exact }
        }

        val prefixNorm = normalize(cleanPrefix)
        val prefixLower = cleanPrefix.lowercase()
        val firstChar = prefixNorm[0]

        val candidateList = buckets[firstChar] ?: return emptyList()

        val bigramBonuses = if (prevNorm.isNotEmpty()) {
            bigramsByPrev[prevNorm]?.associate { it.first.lowercase() to (it.second * 3) } ?: emptyMap()
        } else {
            emptyMap()
        }

        val scored = ArrayList<Pair<String, Int>>(16)

        for (u in candidateList) {
            if (u.norm.startsWith(prefixNorm)) {
                var score = u.freq
                val bScore = bigramBonuses[u.exact.lowercase()]
                if (bScore != null) {
                    score += bScore
                }
                if (u.norm == prefixNorm) {
                    score += 50000
                }
                if (u.exact.lowercase() == prefixLower) {
                    score += 80000
                }
                scored.add(Pair(u.exact, score))
            }
        }

        scored.sortByDescending { it.second }

        val result = ArrayList<String>(3)
        for (item in scored) {
            if (!result.contains(item.first)) {
                result.add(item.first)
                if (result.size == 3) break
            }
        }
        return result
    }

    fun getAutoCorrection(prevWord: String, typedWord: String): String? {
        val clean = typedWord.trim()
        if (clean.length < 3) return null

        val typedLower = clean.lowercase()
        if (isWord(typedLower)) return null

        val typedNorm = normalize(clean)
        val firstChar = typedNorm[0]
        val candidateList = buckets[firstChar] ?: return null

        // 1. Correção rápida de acento no bucket
        for (u in candidateList) {
            if (u.norm == typedNorm) {
                return u.exact
            }
        }

        // 2. Distância de edição conservadora (distância 1 e alta frequência)
        val prevNorm = normalize(prevWord.trim())
        var bestCandidate: Pair<String, Int>? = null

        for (u in candidateList) {
            if (kotlin.math.abs(typedNorm.length - u.norm.length) > 1) continue

            val dist = levenshtein(typedNorm, u.norm)
            if (dist == 1 && u.freq >= 15000) {
                var score = u.freq
                if (prevNorm.isNotEmpty()) {
                    val list = bigramsByPrev[prevNorm]
                    if (list != null) {
                        for ((nextW, wWeight) in list) {
                            if (nextW.equals(u.exact, ignoreCase = true)) {
                                score += wWeight * 2
                            }
                        }
                    }
                }

                if (bestCandidate == null || score > bestCandidate.second) {
                    bestCandidate = Pair(u.exact, score)
                }
            }
        }

        return bestCandidate?.first
    }

    private fun normalize(s: String): String {
        val sb = java.lang.StringBuilder(s.length)
        for (c in s.lowercase()) {
            when (c) {
                'á', 'à', 'â', 'ã', 'ä' -> sb.append('a')
                'é', 'è', 'ê', 'ë' -> sb.append('e')
                'í', 'ì', 'î', 'ï' -> sb.append('i')
                'ó', 'ò', 'ô', 'õ', 'ö' -> sb.append('o')
                'ú', 'ù', 'û', 'ü' -> sb.append('u')
                'ç' -> sb.append('c')
                'ñ' -> sb.append('n')
                else -> if (c.isLetter()) sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }
}
