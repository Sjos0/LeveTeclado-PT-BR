package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSuggestionClickListener: ((String) -> Unit)? = null

    private var suggestions: List<String> = emptyList()
    private val candidateRects = mutableListOf<Pair<String, RectF>>()
    private var pressedIndex = -1

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0F172A") // Slate-900
    }

    private val separatorPaint = Paint().apply {
        color = Color.parseColor("#334155")
        strokeWidth = 2f
    }

    private val pressedChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        style = Paint.Style.FILL
    }

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Sky-400 (destaque para a sugestão principal central)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val normalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0") // Slate-200
        textAlign = Paint.Align.CENTER
    }

    fun setSuggestions(list: List<String>) {
        suggestions = list.take(3)
        calculateCandidateLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (42 * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateCandidateLayout()
    }

    private fun calculateCandidateLayout() {
        candidateRects.clear()
        val count = suggestions.size
        if (count == 0 || width <= 0 || height <= 0) return

        val chipWidth = width.toFloat() / count
        val h = height.toFloat()

        for (i in 0 until count) {
            val rect = RectF(i * chipWidth, 0f, (i + 1) * chipWidth, h)
            candidateRects.add(Pair(suggestions[i], rect))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (suggestions.isEmpty()) return

        for (i in candidateRects.indices) {
            val (text, rect) = candidateRects[i]

            if (i == pressedIndex) {
                canvas.drawRoundRect(
                    RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f),
                    8f, 8f, pressedChipPaint
                )
            }

            // O candidato central (índice 0 ou 1 se 3 itens) ganha destaque de sugestão principal
            val isPrimary = if (suggestions.size == 3) i == 1 else i == 0
            val paint = if (isPrimary) primaryTextPaint else normalTextPaint

            paint.textSize = rect.height() * 0.40f
            val textY = rect.centerY() - ((paint.descent() + paint.ascent()) / 2)
            canvas.drawText(text, rect.centerX(), textY, paint)

            // Separador vertical fino entre as opções
            if (i < candidateRects.size - 1) {
                canvas.drawLine(rect.right, rect.top + 8f, rect.right, rect.bottom - 8f, separatorPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in candidateRects.indices) {
                    if (candidateRects[i].second.contains(x, y)) {
                        pressedIndex = i
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedIndex >= 0 && pressedIndex < candidateRects.size) {
                    if (!candidateRects[pressedIndex].second.contains(x, y)) {
                        pressedIndex = -1
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (pressedIndex >= 0 && pressedIndex < candidateRects.size) {
                    val chosen = candidateRects[pressedIndex].first
                    onSuggestionClickListener?.invoke(chosen)
                }
                pressedIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
