package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.R

/**
 * Barra superior do teclado:
 * - Com composing: sugestões de palavras.
 * - Sem composing: toolbar com ícones Material.
 * - menuOpen: ícone esquerdo vira X e fecha o painel.
 * - Long-press + arraste: reordena ícones (exceto quando menuOpen).
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolbarAction {
        MENU, CURSOR, CLIPBOARD, TRANSLATE, SETTINGS, MIC
    }

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onToolbarActionClickListener: ((ToolbarAction) -> Unit)? = null
    var onToolbarOrderChanged: ((List<ToolbarAction>) -> Unit)? = null

    private var suggestions: List<String> = emptyList()
    private val candidateRects = ArrayList<Pair<String, RectF>>(3)
    private var pressedIndex = -1

    private var toolbarActions: MutableList<ToolbarAction> = mutableListOf(
        ToolbarAction.MENU,
        ToolbarAction.CURSOR,
        ToolbarAction.CLIPBOARD,
        ToolbarAction.TRANSLATE,
        ToolbarAction.SETTINGS,
        ToolbarAction.MIC
    )
    private val toolbarRects = ArrayList<Pair<ToolbarAction, RectF>>(6)
    private var pressedToolbarIndex = -1

    /** Quando true, o slot MENU desenha X e o toque fecha o painel. */
    var menuOpen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var customizeMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var dragFromIndex = -1
    private var dragCurrentIndex = -1
    private var downTime = 0L
    private var longPressTriggered = false

    private val bgPaint = Paint().apply { color = Color.parseColor("#263238") }
    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#313C42")
        strokeWidth = 2f
    }
    private val pressedChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404A50")
        style = Paint.Style.FILL
    }
    private val customizeHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3350B4CE")
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

    private var iconTint = Color.parseColor("#B0BEC5")

    private val iconMap: MutableMap<ToolbarAction, Drawable?> = mutableMapOf()
    private var closeIcon: Drawable? = null

    init {
        reloadIcons()
    }

    private fun reloadIcons() {
        iconMap[ToolbarAction.MENU] = loadTinted(R.drawable.view_cozy_24)
        iconMap[ToolbarAction.CURSOR] = loadTinted(R.drawable.ic_toolbar_cursor)
        iconMap[ToolbarAction.CLIPBOARD] = loadTinted(R.drawable.ic_toolbar_clipboard)
        iconMap[ToolbarAction.TRANSLATE] = loadTinted(R.drawable.ic_toolbar_translate)
        iconMap[ToolbarAction.SETTINGS] = loadTinted(R.drawable.ic_toolbar_settings)
        iconMap[ToolbarAction.MIC] = loadTinted(R.drawable.ic_toolbar_mic)
        closeIcon = loadTinted(R.drawable.ic_close_24)
    }

    private fun loadTinted(resId: Int): Drawable? {
        val raw = ContextCompat.getDrawable(context, resId) ?: return null
        val d = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(d, iconTint)
        return d
    }

    fun setToolbarOrder(order: List<ToolbarAction>) {
        if (order.isEmpty()) return
        toolbarActions = order.toMutableList()
        // Garante que MENU exista
        if (ToolbarAction.MENU !in toolbarActions) {
            toolbarActions.add(0, ToolbarAction.MENU)
        }
        calculateLayout()
        invalidate()
    }

    fun getToolbarOrder(): List<ToolbarAction> = toolbarActions.toList()

    fun applyPalette(p: KeyboardTheme.Palette) {
        bgPaint.color = p.candidateBg
        iconTint = p.candidateIcon
        reloadIcons()
        invalidate()
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
                candidateRects.add(Pair(suggestions[i], RectF(i * chipWidth, 0f, (i + 1) * chipWidth, h)))
            }
        } else {
            val count = toolbarActions.size
            val itemWidth = w / count
            for (i in 0 until count) {
                toolbarRects.add(Pair(toolbarActions[i], RectF(i * itemWidth, 0f, (i + 1) * itemWidth, h)))
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
            if (customizeMode) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), customizeHighlight)
            }
            val iconPx = (22f * density).toInt()
            for (i in toolbarRects.indices) {
                val (action, rect) = toolbarRects[i]
                if (i == pressedToolbarIndex || i == dragCurrentIndex) {
                    val radius = (rect.height() * 0.40f).coerceAtMost(rect.width() * 0.40f)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, pressedChipPaint)
                }
                val d = if (action == ToolbarAction.MENU && menuOpen) {
                    closeIcon
                } else {
                    iconMap[action]
                } ?: continue
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
                downTime = SystemClock.uptimeMillis()
                longPressTriggered = false
                dragFromIndex = -1
                dragCurrentIndex = -1
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
                            dragFromIndex = i
                            invalidate()
                            return true
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (suggestions.isEmpty() && dragFromIndex >= 0 && !menuOpen) {
                    val elapsed = SystemClock.uptimeMillis() - downTime
                    if (!longPressTriggered && elapsed >= 450L) {
                        longPressTriggered = true
                        customizeMode = true
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                    if (customizeMode) {
                        var over = -1
                        for (i in toolbarRects.indices) {
                            if (toolbarRects[i].second.contains(x, y)) {
                                over = i
                                break
                            }
                        }
                        if (over >= 0 && over != dragCurrentIndex) {
                            dragCurrentIndex = over
                            if (over != dragFromIndex) {
                                val item = toolbarActions.removeAt(dragFromIndex)
                                toolbarActions.add(over, item)
                                dragFromIndex = over
                                calculateLayout()
                                onToolbarOrderChanged?.invoke(toolbarActions.toList())
                            }
                            invalidate()
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (suggestions.isNotEmpty() && pressedIndex >= 0 && pressedIndex < candidateRects.size) {
                    onSuggestionClickListener?.invoke(candidateRects[pressedIndex].first)
                } else if (suggestions.isEmpty() && pressedToolbarIndex >= 0 && pressedToolbarIndex < toolbarRects.size) {
                    if (!longPressTriggered && !customizeMode) {
                        val action = toolbarRects[pressedToolbarIndex].first
                        onToolbarActionClickListener?.invoke(action)
                    }
                }
                if (customizeMode && !longPressTriggered) {
                    // toque curto sai do modo personalizar
                    customizeMode = false
                }
                // se soltar após long-press, mantém customize até toque curto fora
                if (longPressTriggered) {
                    // permanece em customizeMode até próximo toque curto
                }
                pressedIndex = -1
                pressedToolbarIndex = -1
                dragFromIndex = -1
                dragCurrentIndex = -1
                longPressTriggered = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                pressedToolbarIndex = -1
                dragFromIndex = -1
                dragCurrentIndex = -1
                longPressTriggered = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        fun parseOrder(raw: String): List<ToolbarAction> {
            val all = ToolbarAction.entries.toSet()
            val parsed = raw.split(',')
                .mapNotNull { name ->
                    try {
                        ToolbarAction.valueOf(name.trim())
                    } catch (_: Exception) {
                        null
                    }
                }
                .filter { it in all }
                .distinct()
            val missing = ToolbarAction.entries.filter { it !in parsed }
            return parsed + missing
        }

        fun serializeOrder(order: List<ToolbarAction>): String =
            order.joinToString(",") { it.name }
    }
}
