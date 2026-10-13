package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Barra superior do teclado:
 * - Quando o usuário estiver digitando (composing): exibe sugestões de palavras com chip central destacado.
 * - Quando o usuário NÃO estiver digitando ou finalizar uma palavra: exibe a barra de ferramentas
 *   com ícones vetoriais fiéis à referência:
 *   [Menu 4-quadrados, Cursor/Seleção, Área de transferência/Colar, Tradutor, Configurações, Microfone]
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
        color = Color.parseColor("#263238") // Fundo alinhado à referência
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
        color = Color.parseColor("#CFD8DC") // Tom legível e de alto contraste
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    // Tintura dos ícones da toolbar (cinza-claro #90A4AE / #CFD8DC como no teclado de referência)
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        style = Paint.Style.FILL
    }

    // Reutilização de objetos gráficos para zero alocação de memória no onDraw
    private val tempRect = RectF()
    private val tempPath = Path()

    fun setSuggestions(list: List<String>) {
        suggestions = list.take(3)
        calculateLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        // 44 dp ergonômico idêntico à imagem de referência
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
            // 6 botões de ferramentas distribuídos na largura exatamente como na imagem
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

        // Linha divisória inferior sutil
        canvas.drawLine(0f, height.toFloat() - 1f, width.toFloat(), height.toFloat() - 1f, dividerPaint)

        val density = resources.displayMetrics.density

        if (suggestions.isNotEmpty()) {
            // Desenhar sugestões com estilo de chips
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
            // Desenhar ícones da barra de ferramentas (100% vetoriais, zero emoji)
            iconStrokePaint.strokeWidth = 2f * density

            for (i in toolbarRects.indices) {
                val (action, rect) = toolbarRects[i]

                if (i == pressedToolbarIndex) {
                    val radius = (rect.height() * 0.40f).coerceAtMost(rect.width() * 0.40f)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, pressedChipPaint)
                }

                drawToolbarIcon(canvas, action, rect, density)
            }
        }
    }

    private fun drawToolbarIcon(canvas: Canvas, action: ToolbarAction, rect: RectF, density: Float) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val iconSize = 9.5f * density

        when (action) {
            ToolbarAction.MENU -> {
                // 4 pequenos quadrados arredondados (ícone de menu/widgets da referência)
                val blockSize = iconSize * 0.42f
                val gap = iconSize * 0.20f
                val r = 1.5f * density

                // Superior esquerdo
                tempRect.set(cx - gap - blockSize, cy - gap - blockSize, cx - gap, cy - gap)
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)
                // Superior direito
                tempRect.set(cx + gap, cy - gap - blockSize, cx + gap + blockSize, cy - gap)
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)
                // Inferior esquerdo
                tempRect.set(cx - gap - blockSize, cy + gap, cx - gap, cy + gap + blockSize)
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)
                // Inferior direito
                tempRect.set(cx + gap, cy + gap, cx + gap + blockSize, cy + gap + blockSize)
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)
            }

            ToolbarAction.CURSOR -> {
                // Barra vertical com setas horizontais (edição de texto / cursor da referência)
                val barHalfH = iconSize * 0.70f
                canvas.drawLine(cx, cy - barHalfH, cx, cy + barHalfH, iconStrokePaint)

                // Pequenas travas nas pontas
                val capW = iconSize * 0.25f
                canvas.drawLine(cx - capW, cy - barHalfH, cx + capW, cy - barHalfH, iconStrokePaint)
                canvas.drawLine(cx - capW, cy + barHalfH, cx + capW, cy + barHalfH, iconStrokePaint)

                // Seta esquerda
                tempPath.reset()
                tempPath.moveTo(cx - (iconSize * 0.35f), cy - (iconSize * 0.30f))
                tempPath.lineTo(cx - (iconSize * 0.75f), cy)
                tempPath.lineTo(cx - (iconSize * 0.35f), cy + (iconSize * 0.30f))
                canvas.drawPath(tempPath, iconStrokePaint)

                // Seta direita
                tempPath.reset()
                tempPath.moveTo(cx + (iconSize * 0.35f), cy - (iconSize * 0.30f))
                tempPath.lineTo(cx + (iconSize * 0.75f), cy)
                tempPath.lineTo(cx + (iconSize * 0.35f), cy + (iconSize * 0.30f))
                canvas.drawPath(tempPath, iconStrokePaint)
            }

            ToolbarAction.CLIPBOARD -> {
                // Prancheta / Área de transferência (ícone clipboard da referência)
                val boardW = iconSize * 0.65f
                val boardH = iconSize * 0.85f
                val corner = 2f * density

                tempRect.set(cx - boardW, cy - (boardH * 0.7f), cx + boardW, cy + (boardH * 0.9f))
                canvas.drawRoundRect(tempRect, corner, corner, iconStrokePaint)

                // Clipe superior da prancheta
                val clipW = iconSize * 0.35f
                val clipH = iconSize * 0.25f
                tempRect.set(cx - clipW, cy - (boardH * 0.9f), cx + clipW, cy - (boardH * 0.5f))
                canvas.drawRoundRect(tempRect, 1.5f * density, 1.5f * density, iconFillPaint)

                // Linhas de texto na prancheta
                val lineHalfW = iconSize * 0.38f
                canvas.drawLine(cx - lineHalfW, cy - (iconSize * 0.15f), cx + lineHalfW, cy - (iconSize * 0.15f), iconStrokePaint)
                canvas.drawLine(cx - lineHalfW, cy + (iconSize * 0.20f), cx + lineHalfW, cy + (iconSize * 0.20f), iconStrokePaint)
                canvas.drawLine(cx - lineHalfW, cy + (iconSize * 0.55f), cx + (lineHalfW * 0.3f), cy + (iconSize * 0.55f), iconStrokePaint)
            }

            ToolbarAction.TRANSLATE -> {
                // Ícone do Google Tradutor (dois balões / 'G' e caractere asiático da referência)
                val bubbleW = iconSize * 0.65f
                val bubbleH = iconSize * 0.55f
                val r = 2.5f * density

                // Balão traseiro (esquerda)
                tempRect.set(cx - (bubbleW * 0.95f), cy - (bubbleH * 0.95f), cx + (bubbleW * 0.20f), cy + (bubbleH * 0.35f))
                canvas.drawRoundRect(tempRect, r, r, iconStrokePaint)

                // Balão frontal (direita)
                tempRect.set(cx - (bubbleW * 0.25f), cy - (bubbleH * 0.30f), cx + (bubbleW * 0.90f), cy + (bubbleH * 0.90f))
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)

                // Letra estilizada no balão frontal
                val origColor = iconStrokePaint.color
                iconStrokePaint.color = Color.parseColor("#263238")
                val txtCx = tempRect.centerX()
                val txtCy = tempRect.centerY()
                canvas.drawLine(txtCx - (2f * density), txtCy - (2f * density), txtCx + (2f * density), txtCy - (2f * density), iconStrokePaint)
                canvas.drawLine(txtCx, txtCy - (2f * density), txtCx, txtCy + (2f * density), iconStrokePaint)
                iconStrokePaint.color = origColor
            }

            ToolbarAction.SETTINGS -> {
                // Ícone de engrenagem / Configurações da referência
                val rOuter = iconSize * 0.65f
                val rInner = iconSize * 0.30f

                canvas.drawCircle(cx, cy, rOuter, iconStrokePaint)
                canvas.drawCircle(cx, cy, rInner, iconFillPaint)

                // Dentes da engrenagem
                val teethCount = 6
                val toothLen = iconSize * 0.25f
                for (t in 0 until teethCount) {
                    val angle = Math.toRadians((t * (360.0 / teethCount)))
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    canvas.drawLine(
                        cx + (cos * (rOuter - (1f * density))),
                        cy + (sin * (rOuter - (1f * density))),
                        cx + (cos * (rOuter + toothLen)),
                        cy + (sin * (rOuter + toothLen)),
                        iconStrokePaint
                    )
                }
            }

            ToolbarAction.MIC -> {
                // Microfone / Digitação por voz da referência
                val micW = iconSize * 0.30f
                val micH = iconSize * 0.65f
                val r = micW

                // Corpo do microfone
                tempRect.set(cx - micW, cy - micH, cx + micW, cy + (micH * 0.25f))
                canvas.drawRoundRect(tempRect, r, r, iconFillPaint)

                // Arco do suporte do microfone
                tempRect.set(cx - (micW * 1.7f), cy - (micH * 0.40f), cx + (micW * 1.7f), cy + (micH * 0.55f))
                canvas.drawArc(tempRect, 0f, 180f, false, iconStrokePaint)

                // Haste inferior
                canvas.drawLine(cx, cy + (micH * 0.55f), cx, cy + (micH * 1.05f), iconStrokePaint)
                canvas.drawLine(cx - (iconSize * 0.30f), cy + (micH * 1.05f), cx + (iconSize * 0.30f), cy + (micH * 1.05f), iconStrokePaint)
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
