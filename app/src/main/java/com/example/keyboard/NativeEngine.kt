package com.example.keyboard

import android.content.Context
import android.util.Log

object NativeEngine {
    private const val TAG = "NativeEngine"
    private var isNativeLoaded = false
    private var fallbackEngine: CompactTrieEngine? = null

    init {
        try {
            System.loadLibrary("keyboard_core")
            isNativeLoaded = true
            Log.i(TAG, "libkeyboard_core.so nativa em Rust carregada com sucesso!")
        } catch (t: Throwable) {
            Log.w(TAG, "libkeyboard_core.so não disponível nesta arquitetura ou ambiente, usando fallback puro em Kotlin: ${t.message}")
            isNativeLoaded = false
        }
    }

    fun init(context: Context): Boolean {
        return try {
            val bytes = context.assets.open("dict_pt_br.bin").use { it.readBytes() }

            // O motor Kotlin é sempre carregado: ele faz a autocorreção (ver getAutoCorrection)
            fallbackEngine = CompactTrieEngine(bytes)

            if (isNativeLoaded && nativeInit(bytes)) {
                Log.i(TAG, "Dicionário binário inicializado no núcleo Rust com sucesso.")
            } else {
                Log.i(TAG, "Dicionário binário inicializado no CompactTrieEngine (Kotlin).")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar dict_pt_br.bin: ${e.message}", e)
            false
        }
    }

    fun getSuggestions(prevWord: String, prefix: String): List<String> {
        if (isNativeLoaded) {
            try {
                val result = nativeGetSuggestions(prevWord, prefix)
                if (result != null) {
                    return result.toList()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha na chamada JNI nativeGetSuggestions: ${t.message}")
            }
        }
        return fallbackEngine?.getSuggestions(prevWord, prefix) ?: emptyList()
    }

    fun getAutoCorrection(prevWord: String, typedWord: String): String? {
        // A autocorreção roda no motor Kotlin, que tem a versão atual das regras (ver suggest.rs).
        // A libkeyboard_core.so commitada não contém as strings da stoplist atual, então parece ter sido
        // compilada de uma versão anterior. Depois de recompilar a partir de rust-core/ (cargo-ndk),
        // dá para voltar a usar nativeGetAutoCorrection aqui.
        return fallbackEngine?.getAutoCorrection(prevWord, typedWord)
    }

    fun isWord(word: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeIsWord(word)
            } catch (t: Throwable) {
                Log.w(TAG, "Falha na chamada JNI nativeIsWord: ${t.message}")
            }
        }
        return fallbackEngine?.isWord(word) ?: false
    }

    // Declarações nativas em Rust (JNI)
    private external fun nativeInit(data: ByteArray): Boolean
    private external fun nativeGetSuggestions(prevWord: String, prefix: String): Array<String>?
    private external fun nativeGetAutoCorrection(prevWord: String, typedWord: String): String?
    private external fun nativeIsWord(word: String): Boolean
}
