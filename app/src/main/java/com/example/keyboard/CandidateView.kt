package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.R

/**
 * Barra superior do teclado:
 * - Com composing: sugestões de palavras.
 * - Sem composing: toolbar com ícones Material (Google) via vector drawables.
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolbarAction {
        MENU,
        CURSOR,
        CLIPBOARD,
        TRANSLATE,
        SETTINGS,
        MIC
    }

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onToolbarActionClickListener: ((ToolbarAction) -> Unit)? = null

    private var suggestions: List<String> = emptyList()
    private val candidateRects = ArrayList<Pair<String, RectF>>(3)
    private var pressedIndex = -1

    private val toolbarActions = listOf(
        ToolbarAction.MENU,
        ToolbarAction.CURSOR,
        ToolbarAction.CLIPBOARD,
        ToolbarAction.TRANSLATE,
        ToolbarAction.SETTINGS,
        ToolbarAction.MIC
    )
    private val toolbarRects = ArrayList<Pair<ToolbarAction, RectF>>(6)
    private var pressedToolbarIndex = -1

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#263238")
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
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val normalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val iconTint = Color.parseColor("#B0BEC5")

    private val toolbarIcons: Map<ToolbarAction, Drawable?> = mapOf(
        ToolbarAction.MENU to loadTinted(R.drawable.view_cozy_24),
        ToolbarAction.CURSOR to loadTinted(R.drawable.ic_toolbar_cursor),
        ToolbarAction.CLIPBOARD to loadTinted(R.drawable.ic_toolbar_clipboard),
        ToolbarAction.TRANSLATE to loadTinted(R.drawable.ic_toolbar_translate),
        ToolbarAction.SETTINGS to loadTinted(R.drawable.ic_toolbar_settings),
        ToolbarAction.MIC to loadTinted(R.drawable.ic_toolbar_mic)
    )

    private fun loadTinted(resId: Int): Drawable? {
        val raw = ContextCompat.getDrawable(context, resId) ?: return null
        val d = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(d, iconTint)
        return d
    }

    fun setSuggestions(list: List<String>) {
        suggestions = list.take(3)
        calculateLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (44 * density).toInt()
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
            val count = toolbarActions.size
            val itemWidth = w / count
            for (i in 0 until count) {
                val rect = RectF(i * itemWidth, 0f, (i + 1) * itemWidth, h)
                toolbarRects.add(Pair(toolbarActions[i], rect))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawLine(0f, height.toFloat() - 1f, width.toFloat(), height.toFloat() - 1f, dividerPaint)

        val density = resources.displayMetrics.density

        if (suggestions.isNotEmpty()) {
            for (i in candidateRects.indices) {
                val (text, rect) = candidateRects[i]

                if (i == pressedIndex) {
                    canvas.drawRoundRect(
                        RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f),
                        10f * density, 10f * density, pressedChipPaint
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
            val iconPx = (22f * density).toInt()
            for (i in toolbarRects.indices) {
                val (action, rect) = toolbarRects[i]

                if (i == pressedToolbarIndex) {
                    val radius = (rect.height() * 0.40f).coerceAtMost(rect.width() * 0.40f)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, pressedChipPaint)
                }

                val d = toolbarIcons[action] ?: continue
                val left = (rect.centerX() - iconPx / 2f).toInt()
                val top = (rect.centerY() - iconPx / 2f).toInt()
                d.setBounds(left, top, left + iconPx, top + iconPx)
                d.draw(canvas)
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
                    val action = toolbarRects[pressedToolbarIndex].first
                    onToolbarActionClickListener?.invoke(action)
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
