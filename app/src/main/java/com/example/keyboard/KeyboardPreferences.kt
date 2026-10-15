package com.example.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferências persistentes do teclado.
 * SharedPreferences MODE_PRIVATE — sobrevive a reinícios; sem segredos no código.
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

    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("groq_api_key", value.trim()).apply()
        }

    var speechToTextEnabled: Boolean
        get() = prefs.getBoolean("speech_to_text_enabled", true)
        set(value) = prefs.edit().putBoolean("speech_to_text_enabled", value).apply()

    /** Índice em KeyboardTheme.palettes (0..n-1). */
    var themeId: Int
        get() = prefs.getInt("theme_id", 0).coerceIn(0, KeyboardTheme.palettes.lastIndex)
        set(value) = prefs.edit().putInt("theme_id", value.coerceIn(0, KeyboardTheme.palettes.lastIndex)).apply()

    /**
     * Ordem da toolbar como nomes de CandidateView.ToolbarAction separados por vírgula.
     * Ex.: "MENU,CURSOR,CLIPBOARD,TRANSLATE,SETTINGS,MIC"
     */
    var toolbarOrder: String
        get() = prefs.getString("toolbar_order", DEFAULT_TOOLBAR_ORDER) ?: DEFAULT_TOOLBAR_ORDER
        set(value) = prefs.edit().putString("toolbar_order", value).apply()

    companion object {
        const val DEFAULT_TOOLBAR_ORDER = "MENU,CURSOR,CLIPBOARD,TRANSLATE,SETTINGS,MIC"
    }
}
