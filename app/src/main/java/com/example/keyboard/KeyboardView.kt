package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        fun onReplaceKey(text: String)
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
        const val CODE_EMOJI = -6
        const val CODE_SPACE = 32

        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_LOCKED = 2

        const val MODE_TEXT = 0
        const val MODE_SYMBOLS = 1
        const val MODE_MORE_SYMBOLS = 2

        const val ACTION_TYPE_SEARCH = 1
        const val ACTION_TYPE_SEND = 2
        const val ACTION_TYPE_GO = 3
        const val ACTION_TYPE_DONE = 4
        const val ACTION_TYPE_ENTER = 5
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

    var actionType: Int = ACTION_TYPE_SEARCH
        set(value) {
            field = value
            invalidate()
        }

    var vibrateEnabled: Boolean = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Paleta exata do Gboard Dark (Teal-tinted Charcoal)
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#172227") // Fundo escuro azul-petróleo do Gboard
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A373E") // Teclas normais com tom suave
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2A30") // Teclas funcionais (?123, Shift, Backspace)
        style = Paint.Style.FILL
    }
    private val pressedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B4B53") // Feedback visual de tecla pressionada
        style = Paint.Style.FILL
    }
    private val actionKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#50B4CE") // Tom ciano/azul vibrante do botão de ação
        style = Paint.Style.FILL
    }
    private val actionPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3994AB")
        style = Paint.Style.FILL
    }
    private val emojiBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#50B4CE") // Borda de destaque azul na tecla de emoji como no print
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F7")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val specialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0EBF0")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121A1E")
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 8f, Color.parseColor("#90000000"))
    }

    data class Key(
        val code: Int,
        var label: String,
        val accents: List<String> = emptyList(),
        val widthWeight: Float = 1.0f,
        val isSpecial: Boolean = false,
        val isAction: Boolean = false,
        val isEmoji: Boolean = false,
        var rect: RectF = RectF()
    )

    private val keys = ArrayList<Key>(50)

    private data class PointerInfo(
        var key: Key,
        var startX: Float,
        var startY: Float,
        var isLongPressTriggered: Boolean = false,
        var dispatchedChar: String? = null
    )

    private val activePointers = HashMap<Int, PointerInfo>()

    // Popup de acentos e emojis
    private var popupActive = false
    private var popupKey: Key? = null
    private val popupRects = ArrayList<Pair<String, RectF>>()
    private var selectedPopupIndex = -1
    private var popupPointerId = -1

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
        // 5 linhas com proporção ergonômica idêntica ao print do Gboard
        val height = (255 * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyboardLayout()
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

        val paddingH = 4.5f
        val paddingV = 6f
        val rows = when (keyboardMode) {
            MODE_TEXT -> getTextRows()
            MODE_SYMBOLS -> getSymbolRows()
            MODE_MORE_SYMBOLS -> getMoreSymbolRows()
            else -> getTextRows()
        }

        val numRows = rows.size
        val rowHeight = (h - (paddingV * (numRows + 1))) / numRows
        var currentY = paddingV

        for (row in rows) {
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val availableWidth = w - (paddingH * (row.size + 1))
            var currentX = paddingH

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
                currentX += keyWidth + paddingH
            }
            currentY += rowHeight + paddingV
        }
    }

    private fun getTextRows(): List<List<Key>> {
        // Linha 1: Linha de Números dedicada (idêntica ao print)
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )

        // Linha 2: q w e r t y u i o p
        val row2 = listOf(
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

        // Linha 3: a s d f g h j k l ç (10 teclas perfeitamente alinhadas com as linhas 1 e 2)
        val row3 = listOf(
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

        // Linha 4: Shift, z x c v b n m, Backspace
        val row4 = listOf(
            Key(CODE_SHIFT, "⇧", widthWeight = 1.45f, isSpecial = true),
            Key('z'.code, "z"),
            Key('x'.code, "x"),
            Key('c'.code, "c", accents = listOf("ç")),
            Key('v'.code, "v"),
            Key('b'.code, "b"),
            Key('n'.code, "n", accents = listOf("ñ")),
            Key('m'.code, "m"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.45f, isSpecial = true)
        )

        // Linha 5: ?123, , , Emoji com borda azul, Espaço, ., Botão de Ação ciano (exato do print)
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "?123", widthWeight = 1.35f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", accents = listOf("😊", "😂", "❤️", "👍", "👏", "🙏", "😍", "🔥", "🎉", "😎"), widthWeight = 1.05f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 3.8f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true, accents = listOf("!", "?", "@", "-", "_", "/")),
            Key(CODE_ENTER, "action", widthWeight = 1.45f, isAction = true)
        )

        return listOf(row1, row2, row3, row4, row5)
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
            Key('*'.code, "*"), Key('"'.code, "\""), Key('\''.code, "'"), Key(':'.code, ":"),
            Key(';'.code, ";"), Key('!'.code, "!"), Key('?'.code, "?"), Key('['.code, "["),
            Key(']'.code, "]"), Key('\\'.code, "\\")
        )
        val row4 = listOf(
            Key(CODE_MORE_SYMBOLS, "=\\<", widthWeight = 1.45f, isSpecial = true),
            Key('~'.code, "~"), Key('`'.code, "`"), Key('<'.code, "<"), Key('>'.code, ">"),
            Key('='.code, "="), Key('_'.code, "_"), Key('|'.code, "|"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.45f, isSpecial = true)
        )
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.35f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", widthWeight = 1.05f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 3.8f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_ENTER, "action", widthWeight = 1.45f, isAction = true)
        )
        return listOf(row1, row2, row3, row4, row5)
    }

    private fun getMoreSymbolRows(): List<List<Key>> {
        val row1 = listOf(
            Key('1'.code, "1"), Key('2'.code, "2"), Key('3'.code, "3"), Key('4'.code, "4"),
            Key('5'.code, "5"), Key('6'.code, "6"), Key('7'.code, "7"), Key('8'.code, "8"),
            Key('9'.code, "9"), Key('0'.code, "0")
        )
        val row2 = listOf(
            Key('~'.code, "~"), Key('`'.code, "`"), Key('|'.code, "|"), Key('\\'.code, "\\"),
            Key('^'.code, "^"), Key('°'.code, "°"), Key('•'.code, "•"), Key('·'.code, "·"),
            Key('…'.code, "…"), Key('§'.code, "§")
        )
        val row3 = listOf(
            Key('¿'.code, "¿"), Key('¡'.code, "¡"), Key('¢'.code, "¢"), Key('£'.code, "£"),
            Key('€'.code, "€"), Key('¥'.code, "¥"), Key('©'.code, "©"), Key('®'.code, "®"),
            Key('™'.code, "™"), Key('±'.code, "±")
        )
        val row4 = listOf(
            Key(CODE_MORE_SYMBOLS, "?123", widthWeight = 1.45f, isSpecial = true),
            Key('<'.code, "<"), Key('>'.code, ">"), Key('{'.code, "{"), Key('}'.code, "}"),
            Key('='.code, "="), Key('_'.code, "_"), Key('‰'.code, "‰"),
            Key(CODE_BACKSPACE, "⌫", widthWeight = 1.45f, isSpecial = true)
        )
        val row5 = listOf(
            Key(CODE_MODE_CHANGE, "ABC", widthWeight = 1.35f, isSpecial = true),
            Key(','.code, ",", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_EMOJI, "😊", widthWeight = 1.05f, isSpecial = true, isEmoji = true),
            Key(CODE_SPACE, "espaço", widthWeight = 3.8f),
            Key('.'.code, ".", widthWeight = 1.0f, isSpecial = true),
            Key(CODE_ENTER, "action", widthWeight = 1.45f, isAction = true)
        )
        return listOf(row1, row2, row3, row4, row5)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val cornerRadius = 16f

        for (i in keys.indices) {
            val key = keys[i]
            val isPressed = isKeyPressed(key)

            val paint = when {
                key.isAction -> if (isPressed) actionPressedPaint else actionKeyPaint
                isPressed && !popupActive -> pressedKeyPaint
                key.isSpecial -> specialKeyPaint
                else -> keyPaint
            }

            canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paint)

            // Borda azul ao redor da tecla de emoji (exato do screenshot de referência)
            if (key.isEmoji) {
                val insetRect = RectF(key.rect.left + 3f, key.rect.top + 3f, key.rect.right - 3f, key.rect.bottom - 3f)
                canvas.drawRoundRect(insetRect, cornerRadius - 2f, cornerRadius - 2f, emojiBorderPaint)
            }

            // Renderização do conteúdo de cada tecla
            when (key.code) {
                CODE_SHIFT -> drawShiftIcon(canvas, key.rect)
                CODE_BACKSPACE -> drawBackspaceIcon(canvas, key.rect)
                CODE_ENTER -> drawActionIcon(canvas, key.rect)
                CODE_EMOJI -> drawEmojiIcon(canvas, key.rect)
                CODE_SPACE -> {
                    // Barra de espaço limpa sem texto ou com texto translúcido sutil
                }
                else -> {
                    val isSpecialText = key.isSpecial || key.label.length > 1
                    val fontPaint = if (isSpecialText) specialTextPaint else textPaint

                    fontPaint.textSize = when {
                        key.label.length > 2 -> key.rect.height() * 0.30f
                        else -> key.rect.height() * 0.44f
                    }

                    val textY = key.rect.centerY() - ((fontPaint.descent() + fontPaint.ascent()) / 2)
                    canvas.drawText(key.label, key.rect.centerX(), textY, fontPaint)
                }
            }
        }

        // Desenhar popup de acentos se ativo
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

    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.height() * 0.22f

        val color = when (shiftState) {
            SHIFT_LOCKED -> Color.parseColor("#50B4CE") // Azul-ciano quando Caps Lock está travado
            SHIFT_ON -> Color.WHITE // Branco sólido quando ativo para 1 letra
            else -> Color.parseColor("#9DB1BC") // Cinza suave quando inativo
        }

        iconPaint.color = color
        iconFillPaint.color = color

        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx - size, cy + (size * 0.2f))
            lineTo(cx - (size * 0.4f), cy + (size * 0.2f))
            lineTo(cx - (size * 0.4f), cy + size)
            lineTo(cx + (size * 0.4f), cy + size)
            lineTo(cx + (size * 0.4f), cy + (size * 0.2f))
            lineTo(cx + size, cy + (size * 0.2f))
            close()
        }

        if (shiftState != SHIFT_OFF) {
            canvas.drawPath(path, iconFillPaint)
        } else {
            canvas.drawPath(path, iconPaint)
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val w = rect.height() * 0.26f
        val h = rect.height() * 0.20f

        iconPaint.color = Color.parseColor("#F1F5F7")

        val path = Path().apply {
            moveTo(cx - (w * 1.1f), cy)
            lineTo(cx - (w * 0.4f), cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - (w * 0.4f), cy + h)
            close()
        }
        canvas.drawPath(path, iconPaint)

        // Desenhar o X dentro do ícone de backspace
        val xSize = h * 0.45f
        val xCenter = cx + (w * 0.3f)
        canvas.drawLine(xCenter - xSize, cy - xSize, xCenter + xSize, cy + xSize, iconPaint)
        canvas.drawLine(xCenter - xSize, cy + xSize, xCenter + xSize, cy - xSize, iconPaint)
    }

    private fun drawActionIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.height() * 0.18f

        iconPaint.color = Color.WHITE
        iconFillPaint.color = Color.WHITE

        when (actionType) {
            ACTION_TYPE_SEARCH -> {
                // Desenhar lupa 🔍 (idêntica ao print de referência)
                val circleCenter = cx - (r * 0.2f)
                val circleY = cy - (r * 0.2f)
                canvas.drawCircle(circleCenter, circleY, r * 0.75f, iconPaint)
                canvas.drawLine(
                    circleCenter + (r * 0.5f),
                    circleY + (r * 0.5f),
                    circleCenter + (r * 1.3f),
                    circleY + (r * 1.3f),
                    iconPaint
                )
            }
            ACTION_TYPE_SEND -> {
                // Ícone de envio
                val path = Path().apply {
                    moveTo(cx - r, cy - r)
                    lineTo(cx + r, cy)
                    lineTo(cx - r, cy + r)
                    lineTo(cx - (r * 0.4f), cy)
                    close()
                }
                canvas.drawPath(path, iconFillPaint)
            }
            ACTION_TYPE_DONE -> {
                // Checkmark ✓
                canvas.drawLine(cx - r, cy, cx - (r * 0.3f), cy + (r * 0.7f), iconPaint)
                canvas.drawLine(cx - (r * 0.3f), cy + (r * 0.7f), cx + r, cy - (r * 0.6f), iconPaint)
            }
            else -> {
                // Seta de nova linha / Enter ↵
                val path = Path().apply {
                    moveTo(cx + r, cy - (r * 0.7f))
                    lineTo(cx + r, cy + (r * 0.3f))
                    lineTo(cx - r, cy + (r * 0.3f))
                }
                canvas.drawPath(path, iconPaint)
                canvas.drawLine(cx - r, cy + (r * 0.3f), cx - (r * 0.3f), cy - (r * 0.3f), iconPaint)
                canvas.drawLine(cx - r, cy + (r * 0.3f), cx - (r * 0.3f), cy + (r * 0.9f), iconPaint)
            }
        }
    }

    private fun drawEmojiIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.height() * 0.22f

        iconPaint.color = Color.parseColor("#F1F5F7")

        // Círculo do rosto
        canvas.drawCircle(cx, cy, r, iconPaint)
        // Olhos
        val eyeRadius = r * 0.12f
        iconFillPaint.color = Color.parseColor("#F1F5F7")
        canvas.drawCircle(cx - (r * 0.35f), cy - (r * 0.2f), eyeRadius, iconFillPaint)
        canvas.drawCircle(cx + (r * 0.35f), cy - (r * 0.2f), eyeRadius, iconFillPaint)
        // Sorriso
        val smileRect = RectF(cx - (r * 0.55f), cy - (r * 0.3f), cx + (r * 0.55f), cy + (r * 0.6f))
        canvas.drawArc(smileRect, 30f, 120f, false, iconPaint)
    }

    private fun calculatePopupLayout(key: Key) {
        popupRects.clear()
        val count = key.accents.size
        val itemWidth = key.rect.width().coerceAtLeast(64f)
        val itemHeight = key.rect.height()
        val totalWidth = count * itemWidth

        var startX = key.rect.centerX() - (totalWidth / 2f)
        if (startX < 8f) startX = 8f
        if (startX + totalWidth > width - 8f) startX = width - totalWidth - 8f
        val popupY = key.rect.top - itemHeight - 12f

        for (i in 0 until count) {
            val charText = if (shiftState != SHIFT_OFF && key.accents[i].length == 1 && Character.isLetter(key.accents[i][0])) {
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
            popupRects.add(Pair(charText, rect))
        }
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupRects.isEmpty()) return
        val first = popupRects.first().second
        val last = popupRects.last().second
        val fullPopupRect = RectF(first.left - 6f, first.top - 6f, last.right + 6f, last.bottom + 6f)

        canvas.drawRoundRect(fullPopupRect, 18f, 18f, popupBgPaint)

        for (i in popupRects.indices) {
            val (symbol, rect) = popupRects[i]
            if (i == selectedPopupIndex) {
                canvas.drawRoundRect(rect, 12f, 12f, actionKeyPaint)
            }
            textPaint.color = Color.WHITE
            textPaint.textSize = rect.height() * 0.42f
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
                    val info = PointerInfo(key, x, y)
                    activePointers[pointerId] = info
                    asyncVibrate()

                    // DISPARO INSTANTÂNEO NO PRIMEIRO TOQUE (0ms de latência!)
                    when (key.code) {
                        CODE_SHIFT -> listener?.onShift()
                        CODE_BACKSPACE -> {
                            listener?.onBackspace()
                            backspaceDelay = 350L
                            handler.postDelayed(backspaceRunnable, backspaceDelay)
                        }
                        CODE_ENTER -> listener?.onActionKey()
                        CODE_MODE_CHANGE -> {
                            keyboardMode = if (keyboardMode == MODE_TEXT) MODE_SYMBOLS else MODE_TEXT
                        }
                        CODE_MORE_SYMBOLS -> {
                            keyboardMode = if (keyboardMode == MODE_SYMBOLS) MODE_MORE_SYMBOLS else MODE_SYMBOLS
                        }
                        CODE_EMOJI -> {
                            // Ao tocar em emoji rápido: insere sorriso, ao segurar: abre lista
                            listener?.onKey(key.code, "😊")
                            info.dispatchedChar = "😊"
                        }
                        CODE_SPACE -> listener?.onKey(CODE_SPACE, " ")
                        else -> {
                            listener?.onKey(key.code, key.label)
                            info.dispatchedChar = key.label
                        }
                    }

                    if (key.accents.isNotEmpty()) {
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
                        listener?.onReplaceKey(chosen)
                    }
                    popupActive = false
                    popupKey = null
                    selectedPopupIndex = -1
                    popupPointerId = -1
                    activePointers.remove(pointerId)
                    invalidate()
                    return true
                }

                activePointers.remove(pointerId)

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

    private fun findKey(x: Float, y: Float): Key? {
        for (i in keys.indices) {
            if (keys[i].rect.contains(x, y)) {
                return keys[i]
            }
        }
        return null
    }
}
