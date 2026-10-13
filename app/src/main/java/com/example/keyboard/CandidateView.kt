package com.example.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.MainActivity

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onSettingsClickListener: (() -> Unit)? = null
    var onSymbolsClickListener: (() -> Unit)? = null
    var onEmojiClickListener: (() -> Unit)? = null
    var onPasteClickListener: (() -> Unit)? = null
    var onSearchClickListener: (() -> Unit)? = null

    private var suggestions: List<String> = emptyList()
    private val candidateRects = ArrayList<Pair<String, RectF>>(3)
    private var pressedIndex = -1

    // Ícones da barra de ferramentas (Símbolos, Configurações, Emojis, Colar, Pesquisa)
    private val toolbarIcons = listOf("?123", "⚙", "😊", "📋", "🔍")
    private val toolbarRects = ArrayList<Pair<String, RectF>>(5)
    private var pressedToolbarIndex = -1

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#263238") // Fundo elegante alinhado à referência
    }

    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#313C42")
        strokeWidth = 2f
    }

    private val pressedChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404A50")
        style = Paint.Style.FILL
    }

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF") // Branco destacado para o candidato central
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val normalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC") // Tom legível e de alto contraste contra #263238
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1") // Tom claro para ícones funcionais
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setSuggestions(list: List<String>) {
        suggestions = list.take(3)
        calculateLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (44 * density).toInt() // Altura ergonômica da barra de sugestões
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
    }

    private fun calculateLayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        candidateRects.clear()
        toolbarRects.clear()

        if (suggestions.isNotEmpty()) {
            val count = suggestions.size
            val chipWidth = w / count
            for (i in 0 until count) {
                val rect = RectF(i * chipWidth, 0f, (i + 1) * chipWidth, h)
                candidateRects.add(Pair(suggestions[i], rect))
            }
        } else {
            // Barra de ferramentas quando não está em composição
            val iconCount = toolbarIcons.size
            val itemWidth = w / iconCount
            for (i in 0 until iconCount) {
                val rect = RectF(i * itemWidth, 0f, (i + 1) * itemWidth, h)
                toolbarRects.add(Pair(toolbarIcons[i], rect))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Linha divisória inferior sutil
        canvas.drawLine(0f, height.toFloat() - 1f, width.toFloat(), height.toFloat() - 1f, dividerPaint)

        if (suggestions.isNotEmpty()) {
            // Desenhar sugestões com estilo de chips modernos
            for (i in candidateRects.indices) {
                val (text, rect) = candidateRects[i]

                if (i == pressedIndex) {
                    canvas.drawRoundRect(
                        RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f),
                        10f, 10f, pressedChipPaint
                    )
                }

                val isPrimary = if (suggestions.size == 3) i == 1 else i == 0
                val paint = if (isPrimary) primaryTextPaint else normalTextPaint

                paint.textSize = rect.height() * 0.38f
                val textY = rect.centerY() - ((paint.descent() + paint.ascent()) / 2)
                canvas.drawText(text, rect.centerX(), textY, paint)

                if (i < candidateRects.size - 1) {
                    canvas.drawLine(rect.right, rect.top + 10f, rect.right, rect.bottom - 10f, dividerPaint)
                }
            }
        } else {
            // Desenhar botões da barra de ferramentas
            for (i in toolbarRects.indices) {
                val (icon, rect) = toolbarRects[i]

                if (i == pressedToolbarIndex) {
                    canvas.drawCircle(rect.centerX(), rect.centerY(), rect.height() * 0.38f, pressedChipPaint)
                }

                iconPaint.textSize = if (icon.length > 2) rect.height() * 0.34f else rect.height() * 0.42f
                val textY = rect.centerY() - ((iconPaint.descent() + iconPaint.ascent()) / 2)
                canvas.drawText(icon, rect.centerX(), textY, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (suggestions.isNotEmpty()) {
                    for (i in candidateRects.indices) {
                        if (candidateRects[i].second.contains(x, y)) {
                            pressedIndex = i
                            invalidate()
                            return true
                        }
                    }
                } else {
                    for (i in toolbarRects.indices) {
                        if (toolbarRects[i].second.contains(x, y)) {
                            pressedToolbarIndex = i
                            invalidate()
                            return true
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (suggestions.isNotEmpty() && pressedIndex >= 0 && pressedIndex < candidateRects.size) {
                    val chosen = candidateRects[pressedIndex].first
                    onSuggestionClickListener?.invoke(chosen)
                } else if (suggestions.isEmpty() && pressedToolbarIndex >= 0 && pressedToolbarIndex < toolbarRects.size) {
                    when (pressedToolbarIndex) {
                        0 -> {
                            // Acesso imediato à seleção de símbolos
                            onSymbolsClickListener?.invoke()
                        }
                        1 -> {
                            // Configurações
                            if (onSettingsClickListener != null) {
                                onSettingsClickListener?.invoke()
                            } else {
                                try {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                        2 -> {
                            // Emojis
                            onEmojiClickListener?.invoke()
                        }
                        3 -> {
                            // Colar da área de transferência
                            onPasteClickListener?.invoke()
                        }
                        4 -> {
                            // Ação de pesquisa
                            onSearchClickListener?.invoke()
                        }
                    }
                }
                pressedIndex = -1
                pressedToolbarIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                pressedToolbarIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
