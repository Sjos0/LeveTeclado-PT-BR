package com.example.keyboard

import android.content.Context
import android.util.Log
import com.example.BuildConfig

object NativeEngine {
    private const val TAG = "NativeEngine"
    private var isNativeLoaded = false
    private var fallbackEngine: CompactTrieEngine? = null

    /** kotlin: só Kotlin. rust: Rust para sugestões e autocorreção (sem motor Kotlin). both: comportamento padrão. */
    private val engineMode: String = BuildConfig.ENGINE_MODE

    init {
        if (engineMode == "kotlin") {
            isNativeLoaded = false
            Log.i(TAG, "Modo kotlin: biblioteca nativa não carregada de propósito")
        } else try {
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

            val nativeOk = isNativeLoaded && nativeInit(bytes)
            // No modo rust o motor Kotlin nem é criado (economiza a RAM do dicionário duplicado).
            // Se o Rust não estiver disponível, o Kotlin entra como reserva.
            fallbackEngine = if (engineMode == "rust" && nativeOk) null else CompactTrieEngine(bytes)

            if (nativeOk) {
                Log.i(TAG, "Dicionário binário inicializado no núcleo Rust (modo $engineMode).")
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
        if (engineMode == "rust" && isNativeLoaded) {
            return try {
                nativeGetAutoCorrection(prevWord, typedWord)
            } catch (t: Throwable) {
                Log.w(TAG, "Falha na chamada JNI nativeGetAutoCorrection: ${t.message}")
                null
            }
        }
        // Nos modos both e kotlin a autocorreção é feita no motor Kotlin.
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
