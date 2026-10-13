package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
// import androidx.core.content.ContextCompat
import android.widget.Toast
import android.view.Gravity
import android.graphics.Color
import android.widget.TextView
import android.widget.FrameLayout
import android.os.Looper
import android.os.Handler
import android.content.pm.PackageManager
import android.Manifest

class LatinIMEKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private data class AutoCorrectionRecord(
        val original: String,
        val corrected: String
    )

    private lateinit var prefs: KeyboardPreferences
    private var voiceHelper: VoiceInputHelper? = null
    private var recordingOverlay: FrameLayout? = null
    private var amplitudeView: TextView? = null
    private var statusLabel: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            val amp = voiceHelper?.amplitude() ?: 0f
            amplitudeView?.let { v ->
                val scale = 1f + amp * 0.55f
                v.scaleX = scale
                v.scaleY = scale
                val alpha = (0.45f + amp * 0.55f).coerceIn(0.4f, 1f)
                v.alpha = alpha
            }
            if (voiceHelper?.isRecording == true) {
                mainHandler.postDelayed(this, 80L)
            }
        }
    }
    private lateinit var keyboardView: KeyboardView
    private lateinit var candidateView: CandidateView

    private val composingWord = StringBuilder()
    private var previousWord: String = ""
    private var lastAutoCorrection: AutoCorrectionRecord? = null

    private var lastShiftPressTime = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        NativeEngine.init(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#263238"))
        }

        candidateView = CandidateView(this).apply {
            onSuggestionClickListener = { chosen ->
                commitSuggestion(chosen)
            }
            onToolbarActionClickListener = { action ->
                when (action) {
                    CandidateView.ToolbarAction.MENU -> {
                        // Alterna para símbolos/menu
                        keyboardView.keyboardMode = if (keyboardView.keyboardMode == KeyboardView.MODE_TEXT) {
                            KeyboardView.MODE_SYMBOLS
                        } else {
                            KeyboardView.MODE_TEXT
                        }
                    }
                    CandidateView.ToolbarAction.CURSOR -> {
                        // Navegação de cursor / seleção rápida
                        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                        currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                    }
                    CandidateView.ToolbarAction.CLIPBOARD -> {
                        // Colar da área de transferência
                        try {
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = clipboard?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasteText = clip.getItemAt(0).text?.toString()
                                if (!pasteText.isNullOrEmpty()) {
                                    commitComposingText()
                                    currentInputConnection?.commitText(pasteText, 1)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    CandidateView.ToolbarAction.TRANSLATE -> {
                        // Ação de tradução: comuta layout ou abre configurações se necessário
                        try {
                            val intent = android.content.Intent(this@LatinIMEKeyboardService, com.example.MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    CandidateView.ToolbarAction.SETTINGS -> {
                        // Abre configurações do app
                        try {
                            val intent = android.content.Intent(this@LatinIMEKeyboardService, com.example.MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    CandidateView.ToolbarAction.MIC -> {
                        onMicTapped()
                    }
                }
            }
        }

        keyboardView = KeyboardView(this).apply {
            listener = this@LatinIMEKeyboardService
            vibrateEnabled = prefs.vibrateOnPress
        }

        root.addView(candidateView)
        root.addView(keyboardView)

        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingWord.setLength(0)
        previousWord = ""
        lastAutoCorrection = null
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.vibrateEnabled = prefs.vibrateOnPress

        if (info != null) {
            updateEnterActionLabel(info)
            checkAutoCapitalization(info)
        }
        updateCandidates()
    }

    private fun updateEnterActionLabel(info: EditorInfo) {
        val action = info.imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv())

        keyboardView.actionType = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> KeyboardView.ACTION_TYPE_SEARCH
            EditorInfo.IME_ACTION_SEND -> KeyboardView.ACTION_TYPE_SEND
            EditorInfo.IME_ACTION_GO -> KeyboardView.ACTION_TYPE_GO
            EditorInfo.IME_ACTION_DONE -> KeyboardView.ACTION_TYPE_DONE
            else -> KeyboardView.ACTION_TYPE_ENTER
        }
    }

    private fun checkAutoCapitalization(info: EditorInfo? = currentInputEditorInfo) {
        if (!prefs.autoCapitalization || keyboardView.shiftState == KeyboardView.SHIFT_LOCKED) {
            return
        }

        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""

        // Autocapitalização no início do texto ou após '.', '?', '!' seguido de espaço ou quebra de linha
        val shouldCapitalize = when {
            textBefore.isEmpty() -> true
            textBefore.endsWith("\n") -> true
            textBefore.length >= 2 && (textBefore[0] == '.' || textBefore[0] == '?' || textBefore[0] == '!') && textBefore[1] == ' ' -> true
            else -> false
        }

        keyboardView.shiftState = if (shouldCapitalize) KeyboardView.SHIFT_ON else KeyboardView.SHIFT_OFF
    }

    override fun onKey(code: Int, text: String?) {
        val ic = currentInputConnection ?: return
        lastAutoCorrection = null // Qualquer digitação descarta a janela de desfazer autocorreção

        if (code == KeyboardView.CODE_SPACE) {
            handleSpace()
            return
        }

        if (text != null) {
            val charToInsert = if (keyboardView.shiftState != KeyboardView.SHIFT_OFF) {
                text.uppercase()
            } else {
                text.lowercase()
            }

            if (Character.isLetter(code)) {
                composingWord.append(charToInsert)
                ic.setComposingText(composingWord.toString(), 1)
                updateCandidates()
            } else {
                commitComposingText()
                ic.commitText(charToInsert, 1)
                updateCandidates()
            }

            // Se o shift estiver apenas temporariamente ativo, desativa após digitar a letra
            if (keyboardView.shiftState == KeyboardView.SHIFT_ON) {
                keyboardView.shiftState = KeyboardView.SHIFT_OFF
            }
        }
    }

    override fun onReplaceKey(text: String) {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
            composingWord.append(text)
            ic.setComposingText(composingWord.toString(), 1)
            updateCandidates()
        } else {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(text, 1)
        }
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val typed = composingWord.toString()

        if (typed.isNotEmpty()) {
            var wordToCommit = typed

            // Correção automática conservadora
            if (prefs.autoCorrection) {
                val correction = NativeEngine.getAutoCorrection(previousWord, typed)
                if (correction != null && !correction.equals(typed, ignoreCase = true)) {
                    wordToCommit = correction
                    // Registra para permitir desfazer com Backspace imediato
                    lastAutoCorrection = AutoCorrectionRecord(original = typed, corrected = correction)
                }
            }

            ic.commitText("$wordToCommit ", 1)
            previousWord = wordToCommit
            composingWord.setLength(0)
        } else {
            ic.commitText(" ", 1)
        }

        checkAutoCapitalization()
        updateCandidates()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return

        // REQUISITO: "Backspace logo após uma correção desfaz essa correção."
        if (lastAutoCorrection != null) {
            val record = lastAutoCorrection!!
            lastAutoCorrection = null

            // Apagar a palavra corrigida e o espaço inserido
            val deleteCount = record.corrected.length + 1
            ic.deleteSurroundingText(deleteCount, 0)

            // Restaurar exatamente o que o usuário havia digitado originalmente
            composingWord.setLength(0)
            composingWord.append(record.original)
            ic.setComposingText(composingWord.toString(), 1)
            updateCandidates()
            return
        }

        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
            if (composingWord.isNotEmpty()) {
                ic.setComposingText(composingWord.toString(), 1)
            } else {
                ic.commitText("", 1)
            }
            updateCandidates()
        } else {
            // Nenhum texto em composição: envia evento padrão de backspace
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        checkAutoCapitalization()
    }

    override fun onShift() {
        val now = System.currentTimeMillis()
        keyboardView.shiftState = when (keyboardView.shiftState) {
            KeyboardView.SHIFT_OFF -> KeyboardView.SHIFT_ON
            KeyboardView.SHIFT_ON -> {
                if (now - lastShiftPressTime < 350) {
                    KeyboardView.SHIFT_LOCKED // Duplo clique ativa Caps Lock
                } else {
                    KeyboardView.SHIFT_OFF
                }
            }
            KeyboardView.SHIFT_LOCKED -> KeyboardView.SHIFT_OFF
            else -> KeyboardView.SHIFT_OFF
        }
        lastShiftPressTime = now
    }

    override fun onActionKey() {
        val ic = currentInputConnection ?: return
        commitComposingText()

        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }

        checkAutoCapitalization()
    }

    private fun commitSuggestion(chosen: String) {
        val ic = currentInputConnection ?: return
        lastAutoCorrection = null
        ic.commitText("$chosen ", 1)
        previousWord = chosen
        composingWord.setLength(0)

        checkAutoCapitalization()
        updateCandidates()
    }

    private fun commitComposingText() {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            val typed = composingWord.toString()
            ic.commitText(typed, 1)
            previousWord = typed
            composingWord.setLength(0)
        }
    }

    private fun updateCandidates() {
        if (!::candidateView.isInitialized) return
        val prefix = composingWord.toString()
        if (prefix.isNotEmpty()) {
            val suggestions = NativeEngine.getSuggestions(previousWord, prefix)
            candidateView.setSuggestions(suggestions)
        } else {
            // Quando não estiver escrevendo nada ou terminou a palavra, esvazia as sugestões
            // para que a barra volte imediatamente a exibir a toolbar com os ícones da referência
            candidateView.setSuggestions(emptyList())
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        commitComposingText()
        lastAutoCorrection = null
    }


    private fun onMicTapped() {
        if (voiceHelper?.isRecording == true) {
            finishRecordingAndTranscribe()
            return
        }
        val key = prefs.groqApiKey
        if (key.isBlank()) {
            Toast.makeText(this, "Configure a chave da API Groq nas configurações do teclado.", Toast.LENGTH_LONG).show()
            try {
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Permissão de microfone necessária. Abra as configurações do app e conceda o acesso.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("request_mic_permission", true)
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        startVoiceRecording()
    }

    private fun startVoiceRecording() {
        try {
            if (voiceHelper == null) voiceHelper = VoiceInputHelper(this)
            voiceHelper!!.start()
            showRecordingOverlay()
            mainHandler.post(amplitudeRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível iniciar o microfone: ${e.message}", Toast.LENGTH_LONG).show()
            hideRecordingOverlay()
        }
    }

    private fun finishRecordingAndTranscribe() {
        mainHandler.removeCallbacks(amplitudeRunnable)
        val file = voiceHelper?.stop()
        statusLabel?.text = "Transcrevendo…"
        amplitudeView?.text = "…"
        if (file == null) {
            hideRecordingOverlay()
            Toast.makeText(this, "Gravação inválida.", Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = prefs.groqApiKey
        CoroutineScope(Dispatchers.Main).launch {
            val result = GroqSpeechClient.transcribe(apiKey, file)
            try {
                file.delete()
            } catch (_: Exception) {
            }
            hideRecordingOverlay()
            when (result) {
                is GroqSpeechClient.Result.Success -> {
                    commitComposingText()
                    currentInputConnection?.commitText(result.text, 1)
                    Toast.makeText(this@LatinIMEKeyboardService, "Texto inserido", Toast.LENGTH_SHORT).show()
                }
                is GroqSpeechClient.Result.Error -> {
                    Toast.makeText(this@LatinIMEKeyboardService, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRecordingOverlay() {
        hideRecordingOverlay()
        val density = resources.displayMetrics.density
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E01B2327"))
            isClickable = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        val title = TextView(this).apply {
            text = "Ouvindo… toque no quadrado para enviar"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        statusLabel = title
        val ring = TextView(this).apply {
            text = "🎤"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            setTextColor(Color.parseColor("#50B4CE"))
        }
        amplitudeView = ring
        val stopBtn = TextView(this).apply {
            text = "■"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setPadding((28 * density).toInt(), (10 * density).toInt(), (28 * density).toInt(), (10 * density).toInt())
            setOnClickListener { finishRecordingAndTranscribe() }
        }
        val cancel = TextView(this).apply {
            text = "Cancelar"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, (8 * density).toInt(), 0, 0)
            setOnClickListener {
                mainHandler.removeCallbacks(amplitudeRunnable)
                voiceHelper?.cancel()
                hideRecordingOverlay()
            }
        }
        column.addView(title)
        column.addView(ring)
        column.addView(stopBtn)
        column.addView(cancel)
        overlay.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        // Inserir overlay acima do teclado no root
        val root = keyboardView.parent as? LinearLayout
        if (root != null) {
            root.addView(overlay, 0, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            recordingOverlay = overlay
        }
    }

    private fun hideRecordingOverlay() {
        val overlay = recordingOverlay ?: return
        try {
            (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
        } catch (_: Exception) {
        }
        recordingOverlay = null
        amplitudeView = null
        statusLabel = null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(amplitudeRunnable)
        voiceHelper?.cancel()
        hideRecordingOverlay()
        super.onDestroy()
    }

}
