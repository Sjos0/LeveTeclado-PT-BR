package com.example.keyboard

/** Regras puras da palavra em composição, testáveis sem Android. */
object ComposingRules {
    /**
     * True se a palavra em composição ainda termina exatamente no cursor.
     * Se o usuário moveu o cursor para outro ponto, o texto antes do cursor deixa de
     * terminar com a palavra e a composição precisa ser encerrada.
     */
    fun stillAtCursor(textBeforeCursor: String, composing: String): Boolean =
        composing.isNotEmpty() && textBeforeCursor == composing
}
