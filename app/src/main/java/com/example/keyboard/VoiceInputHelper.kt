package com.example.keyboard

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Gravador de áudio leve com MediaRecorder (AAC em container MPEG-4 / .m4a).
 * Compatível com API 26+ e com o formato aceito pela Groq Whisper.
 */
class VoiceInputHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recording = false

    val isRecording: Boolean get() = recording

    fun start(): File {
        stopInternal(deleteFile = true)

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64_000)
            mr.setAudioSamplingRate(16_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            // Se a gravação não começou, libera o microfone para a próxima tentativa não falhar
            try {
                mr.release()
            } catch (_: Exception) {
            }
            file.delete()
            outputFile = null
            throw e
        }

        recorder = mr
        recording = true
        return file
    }

    /**
     * Amplitude aproximada 0f..1f para o anel cosmético de intensidade.
     */
    fun amplitude(): Float {
        val r = recorder ?: return 0f
        return try {
            val max = r.maxAmplitude.coerceAtLeast(1)
            (max / 32768f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    fun stop(): File? {
        return stopInternal(deleteFile = false)
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean): File? {
        val file = outputFile
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                try {
                    reset()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        recording = false
        outputFile = null
        if (deleteFile) {
            try {
                file?.delete()
            } catch (_: Exception) {
            }
            return null
        }
        return file
    }
}
