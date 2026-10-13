package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
            updateLabelsForShift()
            invalidate()
        }

    var keyboardMode: Int = MODE_TEXT
        set(value) {
            field = value
            buildKeyboardLayout()
            invalidate()
        }

    var actionLabel: String = "↵"
        set(value) {
            field = value
            findKeyByCode(CODE_ENTER)?.label = value
            invalidate()
        }

    var vibrateEnabled: Boolean = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Paints pré-alocadas
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#27272A") // Zinc-800
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18181B") // Zinc-900
        style = Paint.Style.FILL
    }
    private val symbolBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F3F46") // Zinc-700 destacado para ?123
        style = Paint.Style.FILL
    }
    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#52525B") // Zinc-600
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4F4F5")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val symbolTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Azul claro destacado para ?123
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#71717A") // Zinc-500
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }
    private val actionKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Azul vibrante
        style = Paint.Style.FILL
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#09090B")
        style = Paint.Style.FILL
        setShadowLayer(14f, 0f, 6f, Color.parseColor("#80000000"))
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#121212") // Fundo escuro puro como no Gboard
    }

    data class Key(
        val code: Int,
        var label: String,
        val subLabel: String? = null,
        val accents: List<String> = emptyList(),
        val widthWeight: Float = 1.0f,
        val isSpecial: Boolean = false,
        val isAction: Boolean = false,
        val isSymbolToggle: Boolean = false,
        var rect: RectF = RectF()
    )

    private val keys = ArrayList<Key>(40)

    // Rastreamento Multi-Touch para digitação rápida sem atrasos
    private data class PointerInfo(
        var key: Key,
        var startX: Float,
        var startY: Float,
        var isLongPressTriggered: Boolean = false
    )

    private val activePointers = HashMap<Int, PointerInfo>()

    // Popup de acentos e símbolos
    private var popupActive = false
    private var popupKey: Key? = null
    private val popupRects = ArrayList<Pair<String, RectF>>()
    private var selectedPopupIndex = -1
    private var popupPointerId = -1

    // Handler para aceleração do Backspace e Long Press
    private val handler = Handler(Looper.getMainLooper())
    private var backspaceDelay = 350L
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            asyncVibrate()
            backspaceDelay = (backspaceDelay * 0.82).toLong().coerceAtLeast(25L)
            handler.postDelayed(this, backspaceDelay)
        }
    }

    private var pendingLongPressPointerId: Int = -1
    private val longPressRunnable = Runnable {
        val info = activePointers[pendingLongPressPointerId]
        if (info != null && info.key.accents.isNotEmpty()) {
            info.isLongPressTriggered = true
            popupActive = true
            popupKey = info.key
            popupPointerId = pendingLongPressPointerId
            selectedPopupIndex = -1
            calculatePopupLayout(info.key)
            asyncVibrate()
            invalidate()
        }
    }

    private fun asyncVibrate() {
        if (!vibrateEnabled || vibrator == null) return
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(12)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (230 * density).toInt() // Altura ergonômica
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyboardLayout()
    }

    private fun findKeyByCode(code: Int): Key? {
        for (i in keys.indices) {
            if (keys[i].code == code) return keys[i]
        }
        return null
    }

    private fun updateLabelsForShift() {
        for (k in keys) {
            if (k.code > 0 && Character.isLetter(k.code)) {
                k.label = if (shiftState != SHIFT_OFF) {
                    k.label.uppercase()
                } else {
                    k.label.lowercase()
                }
            }
        }
    }

    private fun buildKeyboardLayout() {
        keys.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 5f
        val rows = when (keyboardMode) {
            MODE_TEXT -> getTextRows()
            MODE_SYMBOLS -> getSymbolRows()
            MODE_MORE_SYMBOLS -> getMoreSymbolRows()
            else -> getTextRows()
        }

        val numRows = rows.size
        val rowHeight = (h - (padding * (numRows + 1))) / numRows
        var currentY = padding

        for (row in rows) {
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val availableWidth = w - (padding * (row.size + 1))
            var currentX = padding

            for (keyDef in row) {
                val keyWidth = (keyDef.widthWeight / totalWeight) * availableWidth
                keyDef.rect.set(currentX, currentY, currentX + keyWidth, currentY + rowHeight)

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

    private fun getTextRows(): List<List<Key>> {
        // Linha 1: q w e r t y u i o p com números superiores disponíveis
        val row1 = listOf(
            Key('q'.code, "q", subLabel = "1", accents = listOf("1")),
            Key('w'.code, "w", subLabel = "2", accents = listOf("2")),
            Key('e'.code, "e", subLabel = "3", accents = listOf("é", "ê", "è", "3")),
            Key('r'.code, "r", subLabel = "4", accents = listOf("4")),
            Key('t'.code, "t", subLabel = "5", accents = listOf("5")),
            Key('y'.code, "y", subLabel = "6", accents = listOf("6")),
            Key('u'.code, "u", subLabel = "7", accents = listOf("ú", "ü", "ù", "7")),
            Key('i'.code, "i", subLabel = "8", accents = listOf("í", "ì", "î", "8")),
            Key('o'.code, "o", subLabel = "9", accents = listOf("ó", "ô", "õ", "ò", "9")),
            Key('p'.code, "p", subLabel = "0", accents = listOf("0"))
        )

        // Linha 2: a s d f g h j k l ç (ç sempre visível!)
        val row2 = listOf(
            Key('a'.code, "a", accents = listOf("á", "à", "â", "ã", "ä")),
            Key('s'.code, "s", accents = listOf("ß")),
            Key('d'.code, "d"),
            Key('f'.code, "f"),
            Key('g'.code, "g"),
            Key('h'.code, "h"),
            Key('j'.code, "j"),
            Key('k'.code, "k"),
            Key('l'.code, "l"),
            Key('ç'.code, "ç", accents = listOf("c"))
        )

        // Linha 3: Shift, z x c v b n m, Backspace
        val row3 = listOf(
            Key(CODE_SHIFT, "⇧", widthWeight = 1.35f, isSpecial = true),
            Key('z'.code, "z"),
            Key('x'.code, "x"),
            Key('c'.code, "c", accents = listOf("ç")),
            Key('v'.code, "v"),
            Key('b'.code, "b"),
            Key('n'.code, "n", accents = listOf("ñ")),
            Key('m'.code, "m"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.35f, isSpecial = true)
        )

        // Linha 4: Botão de Símbolos ?123 destacado, vírgula com símbolos, espaço, ponto com pontuação, Enter
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "?123", widthWeight = 1.4f, isSpecial = true, isSymbolToggle = true),
            Key(','.code, ",", subLabel = "<", accents = listOf(";", ":", "(", ")"), widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.2f),
            Key('.'.code, ".", subLabel = ">", accents = listOf("!", "?", "@", "-", "_", "/"), widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.4f, isSpecial = true, isAction = true)
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
            Key('@'.code, "@"), Key('#'.code, "#"), Key('$'.code, "$", accents = listOf("R$", "€", "£")),
            Key('%'.code, "%"), Key('&'.code, "&"), Key('-'.code, "-"), Key('+'.code, "+"),
            Key('('.code, "("), Key(')'.code, ")"), Key('/'.code, "/")
        )
        val row3 = listOf(
            Key(CODE_MORE_SYMBOLS, "=\\<", widthWeight = 1.35f, isSpecial = true, isSymbolToggle = true),
            Key('*'.code, "*"), Key('"'.code, "\""), Key('\''.code, "'"), Key(':'.code, ":"),
            Key(';'.code, ";"), Key('!'.code, "!"), Key('?'.code, "?"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.35f, isSpecial = true)
        )
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.4f, isSpecial = true, isSymbolToggle = true),
            Key(','.code, ",", widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.2f),
            Key('.'.code, ".", widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.4f, isSpecial = true, isAction = true)
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
            Key(CODE_MORE_SYMBOLS, "?123", widthWeight = 1.35f, isSpecial = true, isSymbolToggle = true),
            Key('¢'.code, "¢"), Key('£'.code, "£"), Key('€'.code, "€"), Key('¥'.code, "¥"),
            Key('©'.code, "©"), Key('®'.code, "®"), Key('™'.code, "™"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.35f, isSpecial = true)
        )
        val row4 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.4f, isSpecial = true, isSymbolToggle = true),
            Key(','.code, ",", widthWeight = 1.0f),
            Key(CODE_SPACE, "espaço", widthWeight = 4.2f),
            Key('.'.code, ".", widthWeight = 1.0f),
            Key(CODE_ENTER, actionLabel, widthWeight = 1.4f, isSpecial = true, isAction = true)
        )
        return listOf(row1, row2, row3, row4)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val cornerRadius = 12f

        for (i in keys.indices) {
            val key = keys[i]
            val isPressed = isKeyPressed(key)

            val paint = when {
                isPressed && !popupActive -> pressedKeyPaint
                key.isAction -> actionKeyPaint
                key.isSymbolToggle -> symbolBtnPaint
                key.isSpecial -> specialKeyPaint
                else -> keyPaint
            }

            canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paint)

            // Destaque do Shift
            if (key.code == CODE_SHIFT) {
                textPaint.color = when (shiftState) {
                    SHIFT_LOCKED -> Color.parseColor("#38BDF8") // Azul brilhante
                    SHIFT_ON -> Color.WHITE
                    else -> Color.parseColor("#71717A")
                }
            } else if (key.isSymbolToggle) {
                textPaint.color = Color.parseColor("#38BDF8") // ?123 em destaque
            } else {
                textPaint.color = Color.parseColor("#F4F4F5")
            }

            // Tamanho da fonte por tecla
            textPaint.textSize = when {
                key.code == CODE_SPACE -> key.rect.height() * 0.28f
                key.isSymbolToggle -> key.rect.height() * 0.32f
                key.label.length > 2 -> key.rect.height() * 0.30f
                else -> key.rect.height() * 0.42f
            }

            val textY = key.rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(key.label, key.rect.centerX(), textY, textPaint)

            // Dica de número ou símbolo no canto superior
            if (key.subLabel != null) {
                subTextPaint.textSize = key.rect.height() * 0.22f
                canvas.drawText(key.subLabel, key.rect.right - 14f, key.rect.top + 18f, subTextPaint)
            } else if (key.accents.isNotEmpty()) {
                subTextPaint.textSize = key.rect.height() * 0.18f
                canvas.drawText("·", key.rect.right - 10f, key.rect.top + 14f, subTextPaint)
            }
        }

        // Desenhar popup de acentos/símbolos se ativo
        if (popupActive && popupKey != null && popupRects.isNotEmpty()) {
            drawPopup(canvas)
        }
    }

    private fun isKeyPressed(key: Key): Boolean {
        for (info in activePointers.values) {
            if (info.key == key) return true
        }
        return false
    }

    private fun calculatePopupLayout(key: Key) {
        popupRects.clear()
        val count = key.accents.size
        val itemWidth = key.rect.width().coerceAtLeast(62f)
        val itemHeight = key.rect.height()
        val totalWidth = count * itemWidth

        var startX = key.rect.centerX() - (totalWidth / 2f)
        if (startX < 8f) startX = 8f
        if (startX + totalWidth > width - 8f) startX = width - totalWidth - 8f
        val popupY = key.rect.top - itemHeight - 10f

        for (i in 0 until count) {
            val symbolChar = if (shiftState != SHIFT_OFF && key.accents[i].length == 1 && Character.isLetter(key.accents[i][0])) {
                key.accents[i].uppercase()
            } else {
                key.accents[i]
            }
            val rect = RectF(
                startX + (i * itemWidth),
                popupY,
                startX + ((i + 1) * itemWidth),
                popupY + itemHeight
            )
            popupRects.add(Pair(symbolChar, rect))
        }
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupRects.isEmpty()) return
        val first = popupRects.first().second
        val last = popupRects.last().second
        val fullPopupRect = RectF(first.left - 4f, first.top - 4f, last.right + 4f, last.bottom + 4f)

        canvas.drawRoundRect(fullPopupRect, 14f, 14f, popupBgPaint)

        for (i in popupRects.indices) {
            val (symbol, rect) = popupRects[i]
            if (i == selectedPopupIndex) {
                canvas.drawRoundRect(rect, 10f, 10f, actionKeyPaint)
            }
            textPaint.color = Color.WHITE
            textPaint.textSize = rect.height() * 0.40f
            val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(symbol, rect.centerX(), textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val key = findKey(x, y)

                if (key != null) {
                    // OTIMIZAÇÃO: Chording / Roll-over imediato!
                    // Se já havia outra tecla pressionada por outro dedo, dispara ela imediatamente para evitar perda de digitação rápida
                    if (activePointers.isNotEmpty()) {
                        for ((_, otherInfo) in activePointers) {
                            if (!otherInfo.isLongPressTriggered && otherInfo.key != key) {
                                dispatchKey(otherInfo.key)
                                otherInfo.isLongPressTriggered = true // já consumida
                            }
                        }
                    }

                    activePointers[pointerId] = PointerInfo(key, x, y)
                    asyncVibrate()

                    if (key.code == CODE_BACKSPACE) {
                        listener?.onBackspace()
                        backspaceDelay = 350L
                        handler.postDelayed(backspaceRunnable, backspaceDelay)
                    } else if (key.accents.isNotEmpty()) {
                        pendingLongPressPointerId = pointerId
                        handler.postDelayed(longPressRunnable, 300L)
                    }

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (popupActive && pointerId == popupPointerId) {
                    val x = event.getX(actionIndex)
                    val y = event.getY(actionIndex)
                    var newIndex = -1
                    for (i in popupRects.indices) {
                        if (popupRects[i].second.contains(x, y)) {
                            newIndex = i
                            break
                        }
                    }
                    if (newIndex != selectedPopupIndex) {
                        selectedPopupIndex = newIndex
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)

                if (popupActive && pointerId == popupPointerId) {
                    if (selectedPopupIndex >= 0 && selectedPopupIndex < popupRects.size) {
                        val chosen = popupRects[selectedPopupIndex].first
                        listener?.onKey(chosen[0].code, chosen)
                    }
                    popupActive = false
                    popupKey = null
                    selectedPopupIndex = -1
                    popupPointerId = -1
                    activePointers.remove(pointerId)
                    invalidate()
                    return true
                }

                val info = activePointers.remove(pointerId)
                if (info != null && !info.isLongPressTriggered) {
                    dispatchKey(info.key)
                }

                if (actionMasked == MotionEvent.ACTION_UP) {
                    activePointers.clear()
                    popupActive = false
                    popupKey = null
                    selectedPopupIndex = -1
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRunnable)
                activePointers.clear()
                popupActive = false
                popupKey = null
                selectedPopupIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchKey(key: Key) {
        when (key.code) {
            CODE_SHIFT -> listener?.onShift()
            CODE_BACKSPACE -> {
                // já dispara no down e repeat
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

    private fun findKey(x: Float, y: Float): Key? {
        for (i in keys.indices) {
            if (keys[i].rect.contains(x, y)) {
                return keys[i]
            }
        }
        return null
    }
}
