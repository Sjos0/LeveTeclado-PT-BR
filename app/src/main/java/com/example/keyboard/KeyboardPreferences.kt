package com.example.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferências persistentes do teclado.
 * Usa SharedPreferences MODE_PRIVATE (mesmo mecanismo das demais configs).
 * A chave Groq sobrevive ao fechar o app e reiniciar o aparelho;
 * não é enviada a nenhum servidor nosso e não fica no código-fonte.
 */
class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("teclado_pt_br_prefs", Context.MODE_PRIVATE)

    var vibrateOnPress: Boolean
        get() = prefs.getBoolean("vibrate_on_press", false)
        set(value) = prefs.edit().putBoolean("vibrate_on_press", value).apply()

    var autoCorrection: Boolean
        get() = prefs.getBoolean("auto_correction", true)
        set(value) = prefs.edit().putBoolean("auto_correction", value).apply()

    var autoCapitalization: Boolean
        get() = prefs.getBoolean("auto_capitalization", true)
        set(value) = prefs.edit().putBoolean("auto_capitalization", value).apply()

    /**
     * Chave da API Groq (Whisper).
     * Persistida localmente com as demais preferências do app.
     * O usuário não precisa digitar de novo ao sair e voltar.
     */
    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("groq_api_key", value.trim()).apply()
        }

    var speechToTextEnabled: Boolean
        get() = prefs.getBoolean("speech_to_text_enabled", true)
        set(value) = prefs.edit().putBoolean("speech_to_text_enabled", value).apply()
}
