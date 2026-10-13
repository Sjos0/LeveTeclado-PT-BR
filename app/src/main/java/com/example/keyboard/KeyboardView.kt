package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(code: Int, text: String?)
        fun onActionKey()
        fun onBackspace()
        fun onShift()
    }

    companion object {
        const val CODE_SHIFT = -1
        const val CODE_BACKSPACE = -2
        const val CODE_ENTER = -3
        const val CODE_MODE_CHANGE = -4
        const val CODE_MORE_SYMBOLS = -5
        const val CODE_SPACE = 32

        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_LOCKED = 2

        const val MODE_TEXT = 0
        const val MODE_SYMBOLS = 1
        const val MODE_MORE_SYMBOLS = 2
    }

    var listener: OnKeyboardActionListener? = null
    var shiftState: Int = SHIFT_OFF
        set(value) {
            field = value
            invalidate()
        }

    var keyboardMode: Int = MODE_TEXT
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var actionLabel: String = "↵"
        set(value) {
            field = value
            invalidate()
        }

    var vibrateEnabled: Boolean = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Configurações de desenho
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155") // Slate-700
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B") // Slate-800
        style = Paint.Style.FILL
    }
    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569") // Slate-600
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC") // Slate-50
        textAlign = Paint.Align.CENTER
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8") // Slate-400
        textAlign = Paint.Align.CENTER
    }
    private val accentKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0EA5E9") // Sky-500
        style = Paint.Style.FILL
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Slate-900
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 4f, Color.parseColor("#80000000"))
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#0F172A") // Slate-900
    }

    data class Key(
        val code: Int,
        var label: String,
        val subLabel: String? = null,
        val accents: List<String> = emptyList(),
        val widthWeight: Float = 1.0f,
        val isSpecial: Boolean = false,
        val isAction: Boolean = false,
        var rect: RectF = RectF()
    )

    private val keys = mutableListOf<Key>()
    private var pressedKey: Key? = null

    // Popup de acentos
    private var popupActive = false
    private var popupKey: Key? = null
    private val popupRects = mutableListOf<Pair<String, RectF>>()
    private var selectedAccentIndex = -1

    // Handler para aceleração do Backspace e Long Press
    private val handler = Handler(Looper.getMainLooper())
    private var backspaceDelay = 400L
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (pressedKey?.code == CODE_BACKSPACE) {
                listener?.onBackspace()
                vibrate()
                // Acelera a repetição até atingir 30ms
                backspaceDelay = (backspaceDelay * 0.85).toLong().coerceAtLeast(30L)
                handler.postDelayed(this, backspaceDelay)
            }
        }
    }

    private val longPressRunnable = Runnable {
        pressedKey?.let { k ->
            if (k.accents.isNotEmpty()) {
                popupActive = true
                popupKey = k
                selectedAccentIndex = -1
                calculatePopupLayout(k)
                vibrate()
                invalidate()
            }
        }
    }

    private fun vibrate() {
        if (!vibrateEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(15)
            }
        } catch (_: Exception) {}
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Altura padrão do teclado adaptada (cerca de 220dp convertidos em pixels)
        val density = resources.displayMetrics.density
        val height = (220 * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyboardLayout()
    }

    private fun buildKeyboardLayout() {
        keys.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 4f
        val rows = getRowsForCurrentMode()
        val numRows = rows.size
        val rowHeight = (h - (padding * (numRows + 1))) / numRows

        var currentY = padding

        for (row in rows) {
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val availableWidth = w - (padding * (row.size + 1))
            var currentX = padding

            for (keyDef in row) {
                val keyWidth = (keyDef.widthWeight / totalWeight) * availableWidth
                val keyRect = RectF(currentX, currentY, currentX + keyWidth, currentY + rowHeight)
                keyDef.rect = keyRect

                // Ajustar maiúsculas/minúsculas de acordo com o Shift
                if (keyDef.code > 0 && Character.isLetter(keyDef.code)) {
                    keyDef.label = if (shiftState != SHIFT_OFF) {
                        keyDef.label.uppercase()
                    } else {
                        keyDef.label.lowercase()
                    }
                }

                keys.add(keyDef)
                currentX += keyWidth + padding
            }
            currentY += rowHeight + padding
        }
    }

    private fun getRowsForCurrentMode(): List<List<Key>> {
        return when (keyboardMode) {
            MODE_TEXT -> getTextRows()
            MODE_SYMBOLS -> getSymbolRows()
            MODE_MORE_SYMBOLS -> getMoreSymbolRows()
            else -> getTextRows()
        }
    }

    private fun getTextRows(): List<List<Key>> {
        // Linha 1: q w e r t y u i o p
        val row1 = listOf(
            Key('q'.code, "q"),
            Key('w'.code, "w"),
            Key('e'.code, "e", accents = listOf("é", "ê", "è", "ë")),
            Key('r'.code, "r"),
            Key('t'.code, "t"),
            Key('y'.code, "y"),
            Key('u'.code, "u", accents = listOf("ú", "ü", "ù", "û")),
            Key('i'.code, "i", accents = listOf("í", "ì", "î", "ï")),
            Key('o'.code, "o", accents = listOf("ó", "ô", "õ", "ò", "ö")),
            Key('p'.code, "p")
        )

        // Linha 2: a s d f g h j k l ç (ç sempre visível!)
        val row2 = listOf(
            Key('a'.code, "a", accents = listOf("á", "à", "â", "ã", "ä")),
            Key('s'.code, "s"),
            Key('d'.code, "d"),
            Key('f'.code, "f"),
            Key('g'.code, "g"),
            Key('h'.code, "h"),
            Key('j'.code, "j"),
            Key('k'.code, "k"),
            Key('l'.code, "l"),
            Key('ç'.code, "ç")
        )

        // Linha 3: Shift, z x c v b n m, Backspace
        val row3 = listOf(
            Key(CODE_SHIFT, "⇧", widthWeight = 1.3f, isSpecial = true),
            Key('z'.code, "z"),
            Key('x'.code, "x"),
            Key('c'.code, "c", accents = listOf("ç")),
            Key('v'.code, "v"),
            Key('b'.code, "b"),
            Key('n'.code, "n", accents = listOf("ñ")),
            Key('m'.code, "m"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.3f, isSpecial = true)
        )

        // Linha 4: ?123, , , Espaço, ., Enter
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "?123", widthWeight = 1.3f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.4f),
            Key('.'.code, ".", widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.5f, isSpecial = true, isAction = true)
        )

        return listOf(row1, row2, row3, row4)
    }

    private fun getSymbolRows(): List<List<Key>> {
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )
        val row2 = listOf(
            Key('@'.code, "@"), Key('#'.code, "#"), Key('$'.code, "$"), Key('%'.code, "%"),
            Key('&'.code, "&"), Key('-'.code, "-"), Key('+'.code, "+"), Key('('.code, "("),
            Key(')'.code, ")"), Key('/'.code, "/")
        )
        val row3 = listOf(
            Key(CODE_MORE_SYMBOLS, "=\\<", widthWeight = 1.3f, isSpecial = true),
            Key('*'.code, "*"), Key('"'.code, "\""), Key('\''.code, "'"), Key(':'.code, ":"),
            Key(';'.code, ";"), Key('!'.code, "!"), Key('?'.code, "?"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.3f, isSpecial = true)
        )
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.3f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.4f),
            Key('.'.code, ".", widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.5f, isSpecial = true, isAction = true)
        )
        return listOf(row1, row2, row3, row4)
    }

    private fun getMoreSymbolRows(): List<List<Key>> {
        val row1 = listOf(
            Key('~'.code, "~"), Key('`'.code, "`"), Key('|'.code, "|"), Key('\\'.code, "\\"),
            Key('^'.code, "^"), Key('_'.code, "_"), Key('='.code, "="), Key('<'.code, "<"),
            Key('>'.code, ">"), Key('['.code, "[")
        )
        val row2 = listOf(
            Key(']'.code, "]"), Key('{'.code, "{"), Key('}'.code, "}"), Key('§'.code, "§"),
            Key('°'.code, "°"), Key('·'.code, "·"), Key('•'.code, "•"), Key('…'.code, "…"),
            Key('¿'.code, "¿"), Key('¡'.code, "¡")
        )
        val row3 = listOf(
            Key(CODE_MODE_CHANGE, "?123", widthWeight = 1.3f, isSpecial = true),
            Key('¢'.code, "¢"), Key('£'.code, "£"), Key('€'.code, "€"), Key('¥'.code, "¥"),
            Key('©'.code, "©"), Key('®'.code, "®"), Key('™'.code, "™"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.3f, isSpecial = true)
        )
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.3f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.4f),
            Key('.'.code, ".", widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.5f, isSpecial = true, isAction = true)
        )
        return listOf(row1, row2, row3, row4)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val cornerRadius = 10f

        for (key in keys) {
            val paint = when {
                key == pressedKey && !popupActive -> pressedKeyPaint
                key.isAction -> accentKeyPaint
                key.isSpecial -> specialKeyPaint
                else -> keyPaint
            }

            canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paint)

            // Destaque visual do Shift ativo ou travado (Caps Lock)
            if (key.code == CODE_SHIFT) {
                if (shiftState == SHIFT_LOCKED) {
                    textPaint.color = Color.parseColor("#38BDF8") // Destaque azul
                } else if (shiftState == SHIFT_ON) {
                    textPaint.color = Color.parseColor("#F8FAFC")
                } else {
                    textPaint.color = Color.parseColor("#94A3B8")
                }
            } else {
                textPaint.color = Color.parseColor("#F8FAFC")
            }

            // Texto da tecla
            textPaint.textSize = when {
                key.code == CODE_SPACE -> key.rect.height() * 0.28f
                key.label.length > 2 -> key.rect.height() * 0.30f
                else -> key.rect.height() * 0.40f
            }

            val textY = key.rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(key.label, key.rect.centerX(), textY, textPaint)

            // Indicador sutil de acentos no canto superior direito
            if (key.accents.isNotEmpty()) {
                subTextPaint.textSize = key.rect.height() * 0.22f
                canvas.drawText("·", key.rect.right - 10f, key.rect.top + 16f, subTextPaint)
            }
        }

        // Desenhar popup de acentos se ativo
        if (popupActive && popupKey != null && popupRects.isNotEmpty()) {
            drawAccentPopup(canvas)
        }
    }

    private fun calculatePopupLayout(key: Key) {
        popupRects.clear()
        val count = key.accents.size
        val itemWidth = key.rect.width().coerceAtLeast(60f)
        val itemHeight = key.rect.height()
        val totalWidth = count * itemWidth

        var startX = key.rect.centerX() - (totalWidth / 2f)
        if (startX < 8f) startX = 8f
        if (startX + totalWidth > width - 8f) startX = width - totalWidth - 8f
        val popupY = key.rect.top - itemHeight - 8f

        for (i in 0 until count) {
            val accentChar = if (shiftState != SHIFT_OFF) {
                key.accents[i].uppercase()
            } else {
                key.accents[i].lowercase()
            }
            val rect = RectF(
                startX + (i * itemWidth),
                popupY,
                startX + ((i + 1) * itemWidth),
                popupY + itemHeight
            )
            popupRects.add(Pair(accentChar, rect))
        }
    }

    private fun drawAccentPopup(canvas: Canvas) {
        if (popupRects.isEmpty()) return
        val first = popupRects.first().second
        val last = popupRects.last().second
        val fullPopupRect = RectF(first.left - 4f, first.top - 4f, last.right + 4f, last.bottom + 4f)

        canvas.drawRoundRect(fullPopupRect, 14f, 14f, popupBgPaint)

        textPaint.color = Color.WHITE

        for (i in popupRects.indices) {
            val (accentChar, rect) = popupRects[i]
            if (i == selectedAccentIndex) {
                canvas.drawRoundRect(rect, 10f, 10f, accentKeyPaint)
            }
            textPaint.textSize = rect.height() * 0.42f
            val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(accentChar, rect.centerX(), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(x, y)
                pressedKey = key
                popupActive = false
                popupKey = null
                selectedAccentIndex = -1

                if (key != null) {
                    vibrate()
                    if (key.code == CODE_BACKSPACE) {
                        listener?.onBackspace()
                        backspaceDelay = 400L
                        handler.postDelayed(backspaceRunnable, backspaceDelay)
                    } else if (key.accents.isNotEmpty()) {
                        handler.postDelayed(longPressRunnable, 350L)
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (popupActive) {
                    var newIndex = -1
                    for (i in popupRects.indices) {
                        if (popupRects[i].second.contains(x, y)) {
                            newIndex = i
                            break
                        }
                    }
                    if (newIndex != selectedAccentIndex) {
                        selectedAccentIndex = newIndex
                        invalidate()
                    }
                } else if (pressedKey != null) {
                    val key = findKey(x, y)
                    if (key != pressedKey) {
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(backspaceRunnable)
                        pressedKey = key
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)

                if (popupActive && selectedAccentIndex >= 0 && selectedAccentIndex < popupRects.size) {
                    val chosen = popupRects[selectedAccentIndex].first
                    listener?.onKey(chosen[0].code, chosen)
                } else if (pressedKey != null) {
                    val key = pressedKey!!
                    when (key.code) {
                        CODE_SHIFT -> listener?.onShift()
                        CODE_BACKSPACE -> {
                            // Backspace simples caso o runnable ainda não tenha disparado
                            if (backspaceDelay == 400L) {
                                // já disparou no down
                            }
                        }
                        CODE_ENTER -> listener?.onActionKey()
                        CODE_MODE_CHANGE -> {
                            keyboardMode = if (keyboardMode == MODE_TEXT) MODE_SYMBOLS else MODE_TEXT
                        }
                        CODE_MORE_SYMBOLS -> {
                            keyboardMode = if (keyboardMode == MODE_SYMBOLS) MODE_MORE_SYMBOLS else MODE_SYMBOLS
                        }
                        CODE_SPACE -> listener?.onKey(CODE_SPACE, " ")
                        else -> {
                            listener?.onKey(key.code, key.label)
                        }
                    }
                }

                pressedKey = null
                popupActive = false
                popupKey = null
                selectedAccentIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)
                pressedKey = null
                popupActive = false
                popupKey = null
                selectedAccentIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Key? {
        for (key in keys) {
            if (key.rect.contains(x, y)) {
                return key
            }
        }
        return null
    }
}
