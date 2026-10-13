package com.example.keyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente leve para a API de transcrição da Groq (Whisper).
 * Usa apenas HttpURLConnection — zero dependências extras (OkHttp/Retrofit).
 */
object GroqSpeechClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val MODEL = "whisper-large-v3-turbo"
    private const val MAX_BYTES = (19.5 * 1024 * 1024).toLong() // 19.5 MB

    sealed class Result {
        data class Success(val text: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun transcribe(apiKey: String, audioFile: File): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.Error("Chave de API não configurada. Abra as configurações do teclado.")
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.Error("Áudio vazio ou inválido.")
        }
        if (audioFile.length() > MAX_BYTES) {
            return@withContext Result.Error("Áudio muito grande (máx. 19,5 MB).")
        }

        var connection: HttpURLConnection? = null
        try {
            val boundary = "----TecladoLeve${System.currentTimeMillis()}"
            val url = URL(ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "Teclado-PT-BR-Android")
            }

            DataOutputStream(connection.outputStream).use { out ->
                // model
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                out.writeBytes("$MODEL\r\n")

                // language
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
                out.writeBytes("pt\r\n")

                // response_format
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
                out.writeBytes("json\r\n")

                // temperature
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"temperature\"\r\n\r\n")
                out.writeBytes("0\r\n")

                // file
                val fileName = audioFile.name
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n"
                )
                out.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                out.flush()
                audioFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.writeBytes("\r\n")
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                val text = JSONObject(body).optString("text", "").trim()
                if (text.isEmpty()) {
                    Result.Error("Transcrição vazia.")
                } else {
                    Result.Success(text)
                }
            } else {
                val errMsg = try {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                        ?: body.take(200)
                } catch (_: Exception) {
                    "HTTP $code"
                }
                Result.Error("Falha na API ($code): $errMsg")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Falha de rede na transcrição.")
        } finally {
            connection?.disconnect()
        }
    }
}
