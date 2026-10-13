package com.example.keyboard

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("teclado_pt_br_prefs", Context.MODE_PRIVATE)

    var vibrateOnPress: Boolean
        get() = prefs.getBoolean("vibrate_on_press", false) // Desligado por padrão para economizar bateria
        set(value) = prefs.edit().putBoolean("vibrate_on_press", value).apply()

    var autoCorrection: Boolean
        get() = prefs.getBoolean("auto_correction", true)
        set(value) = prefs.edit().putBoolean("auto_correction", value).apply()

    var autoCapitalization: Boolean
        get() = prefs.getBoolean("auto_capitalization", true)
        set(value) = prefs.edit().putBoolean("auto_capitalization", value).apply()
}
