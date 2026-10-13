package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.keyboard.NativeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Teclado PT-BR", appName)
    }

    @Test
    fun testKeyboardEngineSuggestionsAndCorrection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialized = NativeEngine.init(context)
        assertTrue("Dicionário deve ser inicializado com sucesso", initialized)

        // Teste de sugestão de prefixo
        val suggestions = NativeEngine.getSuggestions("", "obr")
        assertTrue("Deve conter sugestões para 'obr'", suggestions.isNotEmpty())
        assertEquals("obrigado", suggestions[0])

        // Teste de autocorreção conservadora de acento
        val correctionNao = NativeEngine.getAutoCorrection("", "nao")
        assertEquals("não", correctionNao)

        val correctionVoce = NativeEngine.getAutoCorrection("", "voce")
        assertEquals("você", correctionVoce)

        // Palavra correta não deve sofrer autocorreção
        val correctionCasa = NativeEngine.getAutoCorrection("", "casa")
        assertEquals(null, correctionCasa)
    }
}
