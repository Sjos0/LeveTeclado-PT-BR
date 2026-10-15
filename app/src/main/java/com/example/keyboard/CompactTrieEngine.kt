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
    private val topFrequencyUnigrams = mutableListOf<String>()
    // Indexação por primeiro caractere para pesquisa instantânea (< 5 microsegundos)
    private val buckets = HashMap<Char, MutableList<Unigram>>()
    private val bigramsByPrev = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    private val wordSet = mutableMapOf<String, Int>()

    init {
        parseBinary(bytes)
        topFrequencyUnigrams.addAll(
            unigrams.sortedByDescending { it.freq }.take(3).map { it.exact }
        )
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
            return topFrequencyUnigrams
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

    private val stoplist = setOf(
        "francois", "françois", "roma", "victor", "max",
        "ai", "esta", "festa", "algo", "calor", "ainda", "todo", "toda", "todos", "todas",
        "pois", "malha", "abraço", "abraços", "pra", "tbm", "vc", "pq", "blz", "bjs", "kkk", "kkkk",
        "avó", "avô", "está", "pode", "pôde", "vem", "vêm", "por", "pôr", "de", "que", "não",
        "bebe", "bebê", "pais", "país", "nós", "robôs", "vocês", "pés"
    )

    /** Nomes próprios e estrangeirismos que nunca podem ser o ALVO de uma autocorreção (ver stoplist_names.txt). */
    private val names = setOf("francois", "françois", "roma", "victor", "max")

    fun getAutoCorrection(prevWord: String, typedWord: String): String? {
        val clean = typedWord.trim()
        if (clean.length < 3) return null

        val typedLower = clean.lowercase()

        // 0. Proteção: stoplist e palavras que NUNCA devem ser alteradas
        if (stoplist.contains(typedLower)) return null

        // Se já for uma palavra válida exatamente no dicionário, NÃO corrige!
        if (isWord(typedLower)) return null

        val isShortWord = clean.length <= 4
        val typedNorm = normalize(clean)
        // Palavra já digitada com acento (ex.: "dá", "às") não sofre correção de acento
        val typedHasAccent = typedLower != typedNorm

        // 1. Correção de acento: 'nao' -> 'não', 'voce' -> 'você', 'mae' -> 'mãe'
        if (!typedHasAccent) {
            val accentCandidates = unigrams.filter { it.norm == typedNorm }
            if (accentCandidates.size == 1) {
                val candidate = accentCandidates[0]
                if (candidate.freq >= 12000) return candidate.exact
            } else if (accentCandidates.size > 1) {
                // Ambiguidade (ex.: 'po' -> pó/pô): NUNCA substitui automaticamente no espaço
                return null
            }
        }

        // Palavras com até 4 letras nunca sofrem correção por distância de edição
        if (isShortWord) return null

        // 2. Erro de digitação plausível (ver isPlausibleTypo), só para palavras frequentes
        val prevNorm = normalize(prevWord.trim())
        var bestCandidate: Pair<String, Int>? = null

        for (u in unigrams) {
            if (names.contains(u.exact.lowercase())) continue
            if (u.freq < 20000 || !isPlausibleTypo(typedNorm, u.norm)) continue

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

    /** Posição aproximada de uma letra num teclado QWERTY: (x em quartos de tecla, linha). */
    private fun keyPosition(c: Char): Pair<Int, Int>? {
        val rows = arrayOf(
            Triple("qwertyuiop", 0, 0),
            Triple("asdfghjkl", 1, 1),
            Triple("zxcvbnm", 3, 2)
        )
        for ((letters, offset, line) in rows) {
            val col = letters.indexOf(c)
            if (col >= 0) return Pair(col * 4 + offset, line)
        }
        return null
    }

    /** Duas letras são vizinhas se estão na mesma tecla ou em teclas encostadas. */
    private fun areNeighborKeys(a: Char, b: Char): Boolean {
        if (a == b) return false
        val pa = keyPosition(a) ?: return false
        val pb = keyPosition(b) ?: return false
        return kotlin.math.abs(pa.second - pb.second) <= 1 && kotlin.math.abs(pa.first - pb.first) <= 4
    }

    /**
     * Verdadeiro só para erros de digitação plausíveis de [candidate]:
     * tecla vizinha errada ("abracp" -> "abraco"), duas letras trocadas de lugar ("ontme" -> "ontem")
     * ou uma letra dobrada a mais/menos ("muuito" -> "muito").
     * Trocar uma letra por outra não vizinha, ou acrescentar/remover letra não dobrada, NÃO é erro
     * típico de digitação ("parar" -> "para", "disso" -> "isso", "muita" -> "muito") e fica de fora.
     */
    private fun isPlausibleTypo(typed: String, candidate: String): Boolean {
        if (typed.length == candidate.length) {
            val diffs = typed.indices.filter { typed[it] != candidate[it] }
            return when (diffs.size) {
                1 -> areNeighborKeys(typed[diffs[0]], candidate[diffs[0]])
                2 -> {
                    val i = diffs[0]
                    val j = diffs[1]
                    j == i + 1 && typed[i] == candidate[j] && typed[j] == candidate[i]
                }
                else -> false
            }
        }

        val longer: String
        val shorter: String
        when {
            typed.length == candidate.length + 1 -> { longer = typed; shorter = candidate }
            candidate.length == typed.length + 1 -> { longer = candidate; shorter = typed }
            else -> return false
        }

        return longer.indices.any { j ->
            val doubled = (j > 0 && longer[j] == longer[j - 1]) ||
                (j + 1 < longer.length && longer[j] == longer[j + 1])
            doubled && longer.removeRange(j, j + 1) == shorter
        }
    }
}
