package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

class LatinIMEKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private data class AutoCorrectionRecord(
        val original: String,
        val corrected: String
    )

    private lateinit var prefs: KeyboardPreferences
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
            onSymbolsClickListener = {
                keyboardView.keyboardMode = if (keyboardView.keyboardMode == KeyboardView.MODE_TEXT) {
                    KeyboardView.MODE_SYMBOLS
                } else {
                    KeyboardView.MODE_TEXT
                }
            }
            onSettingsClickListener = {
                try {
                    val intent = android.content.Intent(this@LatinIMEKeyboardService, com.example.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            onEmojiClickListener = {
                commitComposingText()
                currentInputConnection?.commitText("😊", 1)
            }
            onPasteClickListener = {
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
            onSearchClickListener = {
                onActionKey()
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
        val suggestions = NativeEngine.getSuggestions(previousWord, prefix)
        candidateView.setSuggestions(suggestions)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        commitComposingText()
        lastAutoCorrection = null
    }
}
