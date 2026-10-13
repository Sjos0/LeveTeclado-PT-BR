package com.example

import com.example.keyboard.CompactTrieEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class KeyboardEngineTest {

    private lateinit var engine: CompactTrieEngine

    @Before
    fun setUp() {
        val binFile = File("src/main/assets/dict_pt_br.bin")
        val bytes = if (binFile.exists()) {
            binFile.readBytes()
        } else {
            File("app/src/main/assets/dict_pt_br.bin").readBytes()
        }
        engine = CompactTrieEngine(bytes)
    }

    @Test
    fun testR3_emptyPrefixSuggestionsOrderedByFrequency() {
        // R3: campo vazio deve exibir as 3 mais frequentes (ex: que, não, de), nunca alfabético ("a", "abraço")
        val suggestions = engine.getSuggestions("", "")
        assertEquals(3, suggestions.size)
        // As 3 palavras mais frequentes registradas no nosso binário são "de", "a", "que" ou similar de alta frequência
        assertFalse("Não deve vir 'abraço' ou ordem alfabética no top 3", suggestions.contains("abraço"))
        assertFalse("Não deve vir 'abril' no top 3", suggestions.contains("abril"))
        assertTrue("Deve conter palavras gramaticais de altíssima frequência", suggestions.contains("de") || suggestions.contains("que") || suggestions.contains("não"))
    }

    @Test
    fun testR2_validWordsMustNotBeAutoCorrected() {
        // R2: Palavras corretas NÃO podem ser alteradas
        val validWords = listOf(
            "esta", "festa", "algo", "calor", "ainda", "todo", "pois", "malha",
            "abraço", "abraços", "pra", "tbm", "avó", "está", "pode", "pôde",
            "vem", "vêm", "por", "pôr", "de", "que"
        )
        for (word in validWords) {
            val correction = engine.getAutoCorrection("", word)
            assertNull("A palavra '$word' é válida e JAMAIS deve ser corrigida!", correction)
        }
    }

    @Test
    fun testR2_stoplistAndSlangProtected() {
        val stoplistWords = listOf("francois", "roma", "victor", "max", "ai", "vc", "pq", "blz")
        for (word in stoplistWords) {
            val correction = engine.getAutoCorrection("", word)
            assertNull("O termo '$word' está na stoplist e JAMAIS deve sofrer correção!", correction)
        }
    }

    @Test
    fun testR2_accentRestoration() {
        // Palavras sem acento que devem receber acento com segurança
        assertEquals("não", engine.getAutoCorrection("", "nao"))
        assertEquals("você", engine.getAutoCorrection("", "voce"))
        assertEquals("mãe", engine.getAutoCorrection("", "mae"))
        assertEquals("até", engine.getAutoCorrection("", "ate"))
    }
}
