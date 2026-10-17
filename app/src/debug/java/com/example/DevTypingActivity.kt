package com.example

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText

/**
 * Tela de teste que existe SOMENTE no build de depuração (src/debug).
 * Mostra um campo de texto com o teclado sempre visível, para os testes automatizados no emulador.
 */
class DevTypingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val edit = EditText(this).apply {
            hint = "campo de teste"
            setPadding(40, 160, 40, 40)
        }
        setContentView(edit)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        edit.post { edit.requestFocus() }
    }
}
