package com.goldrs.vida

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Camada visual do Vida — tokens + views custom + utilitários de animação.
 * Fonte única de estilo para a UI; não depende de bibliotecas externas.
 */
object Vida {
    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    class Palette(val dark: Boolean) {
        val ink = if (dark) Color.rgb(235, 238, 246) else Color.rgb(24, 27, 40)
        val muted = if (dark) Color.rgb(150, 156, 173) else Color.rgb(104, 109, 126)
        val surface = if (dark) Color.rgb(9, 11, 18) else Color.rgb(244, 246, 252)
        val surfaceTint = if (dark) Color.rgb(18, 21, 34) else Color.rgb(233, 236, 252)
        val card = if (dark) Color.rgb(20, 24, 37) else Color.rgb(255, 255, 255)
        val cardStroke = if (dark) Color.rgb(42, 48, 68) else Color.rgb(231, 234, 243)
        val primary = Color.rgb(101, 117, 255)
        val violet = Color.rgb(168, 112, 255)
        val teal = Color.rgb(45, 191, 191)
        val green = Color.rgb(52, 199, 123)
        val red = Color.rgb(245, 96, 96)
        val amber = Color.rgb(251, 193, 86)
        val chipBg = if (dark) Color.rgb(38, 43, 64) else Color.rgb(236, 239, 255)
        val heroTop = if (dark) Color.rgb(56, 60, 150) else Color.rgb(110, 128, 255)
        val heroBottom = if (dark) Color.rgb(30, 32, 66) else Color.rgb(176, 138, 255)
    }

    /** Cache por contexto: evita reler a config de tema a cada linha. */
    private val cache = java.util.WeakHashMap<Context, Palette>()
    fun paletteOf(ctx: Context): Palette =
        cache.get(ctx) ?: Palette(isDark(ctx)).also { cache[ctx] = it }
}

/** dp -> px (inteiro). */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** dp -> px (flutuante). */
fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density

/** Palette cacheada para o contexto atual. */
fun Context.vidaPalette(): Vida.Palette = Vida.paletteOf(this)

/** Gradiente arredondado (hero, botões, ícones). */
fun gradientBackground(radiusDp: Int, c1: Int, c2: Int, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable {
    val gd = GradientDrawable(orientation)
    gd.colors = intArrayOf(c1, c2)
    gd.cornerRadius = radiusDp.toFloat()
    return gd
}

/** Fundo retangular arredondado, com borda opcional. */
fun roundedBackground(radiusDp: Int, color: Int, strokeDp: Int = 0, strokeColor: Int = 0): GradientDrawable {
    val gd = GradientDrawable()
    gd.cornerRadius = radiusDp.toFloat()
    gd.setColor(color)
    if (strokeDp > 0) gd.setStroke(strokeDp, strokeColor)
    return gd
}

/** Círculo sólido (badges, indicadores). */
fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(color)
}

/** Círculo vazio com borda. */
fun circleStroke(strokeColor: Int, strokeW: Int = 2): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(strokeW, strokeColor)
}

/** Moeda em pt-BR. */
fun moneyLabel(v: Double): String = "R$ %.2f".format(Locale("pt", "BR"), v)

/** Chip/etiqueta arredondada. */
fun chip(ctx: Context, text: String, bg: Int, fg: Int, radiusDp: Int = 10): TextView {
    val t = TextView(ctx)
    t.text = text
    t.textSize = (ctx.dpF(11))
    t.setPadding(ctx.dp(9), ctx.dp(4), ctx.dp(9), ctx.dp(4))
    t.setTextColor(fg)
    t.background = roundedBackground(radiusDp, bg)
    return t
}

/** Quantas colunas cabem na largura do dispositivo (para grid responsivo). */
fun Context.gridColumns(minDp: Int = 300): Int {
    val w = resources.displayMetrics.widthPixels
    val min = dp(minDp)
    return (w / min).coerceIn(1, 3)
}

/**
 * Distribui os itens em grade responsiva (linhas de células com peso 1f),
 * recalculando o número de colunas conforme a largura.
 */
fun responsiveGrid(parent: LinearLayout, ctx: Context, items: List<View>, minDp: Int = 300, gapDp: Int = 10) {
    if (items.isEmpty()) return
    val cols = ctx.gridColumns(minDp)
    val gap = ctx.dp(gapDp)
    var row: LinearLayout? = null
    var col = 0
    for (i in items.indices) {
        if (col == 0) {
            row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) topMargin = gap })
        }
        val cell = items[i]
        row!!.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (col < cols - 1) rightMargin = gap
        })
        col++
        if (col == cols) col = 0
    }
}

/** Fade + slide escalonado dos filhos de um container (efeito cascade). */
fun staggerIn(container: ViewGroup, itemDelayMs: Int = 32) {
    val n = container.childCount
    for (i in 0 until n) {
        val v = container.getChildAt(i)
        v.alpha = 0f
        v.translationY = v.resources.displayMetrics.density * 11
        v.animate()
            .alpha(1f).translationY(0f)
            .setDuration(280)
            .setStartDelay((i * itemDelayMs).toLong())
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }
}

/** Escala sutil ao pressionar (sensação de material real). Não rouba o clique. */
fun View.pressScale(factor: Float = 0.965f) {
    setOnTouchListener { v, ev ->
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                v.scaleX = factor; v.scaleY = factor
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
        false
    }
}

/** Feedback tátil leve (respeita a configuração do sistema). */
fun View.haptic() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * Anel de progresso com shader Sweep (índigo→violeta), pintura em Canvas +
 * animação desacelerada. Usado para metas, hábitos e orçamento.
 */
class RingView(context: Context, private val pal: Vida.Palette) : View(context) {
    private val strokeW = context.dpF(6f)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.cardStroke; style = Paint.Style.STROKE; strokeWidth = strokeW; strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = strokeW; strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.ink; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        textSize = context.dpF(16f)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.muted; textAlign = Paint.Align.CENTER; textSize = context.dpF(9.5f)
    }
    var progress: Float = 0f
        private set
    private var valueLabel = ""
    private var subLabel = ""
    private var animator: ValueAnimator? = null
    private val padV = context.dp(4)
    private val padS = context.dp(18)

    /** Define o alvo (0..1) e os rótulos, com animação de crescimento. */
    fun set(target: Float, value: String, sub: String) {
        animator?.cancel()
        val from = progress
        valueLabel = value
        subLabel = sub
        val to = target.coerceIn(0f, 1f)
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 780
            interpolator = DecelerateInterpolator(1.35f)
            addUpdateListener { a ->
                progress = from + (to - from) * a.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        val s = if (w == 0 || h == 0) 0 else minOf(w, h)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val c = minOf(width, height).toFloat() / 2f
        val half = strokeW / 2f
        val rect = RectF(half, half, width - half, height - half)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (progress > 0.004f) {
            arc.shader = SweepGradient(c, c, intArrayOf(pal.primary, pal.violet, pal.primary))
            canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        }
        canvas.drawText(valueLabel, c, c + padV + valuePaint.textSize * 0.35f, valuePaint)
        canvas.drawText(subLabel, c, c + padS, subPaint)
    }
}

/**
 * Gráfico de barras em Canvas (últimos N dias de gasto) — sem libs externas,
 * leve para celulares de entrada. Barras arredondadas crescem animadas.
 */
class BarChartView(context: Context, private val pal: Vida.Palette) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var data: List<Double> = emptyList()
    private var growth = 0f
    private var anim: ValueAnimator? = null

    /** Valores do dia mais antigo para o mais recente. */
    fun setData(values: List<Double>) {
        data = values
        anim?.cancel()
        growth = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 640
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { growth = it.animatedFraction; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty() || width <= 0 || height <= 0) return
        val h = height.toFloat()
        val w = width.toFloat()
        val slot = w / data.size
        val barW = (slot * 0.44f).coerceAtLeast(context.dpF(4f))
        val baseY = h - context.dpF(16f)
        val maxV = data.maxOrNull() ?: 0.0
        val scale = if (maxV <= 0) 1.0 else maxV
        data.forEachIndexed { i, v ->
            val fullH = (baseY * (v / scale)).coerceAtLeast(if (v > 0) context.dpF(4f) else 0f)
            val bh = fullH * growth
            val x = i * slot + (slot - barW) / 2f
            val isLast = i == data.lastIndex
            barPaint.color = if (isLast) pal.primary else if (v > 0) pal.amber else pal.cardStroke
            barPaint.shader = null
            canvas.drawRoundRect(x, baseY - bh, x + barW, baseY, barW / 2f, barW / 2f, barPaint)
            if (isLast || i % 2 == 0) {
                labelPaint.color = if (isLast) pal.primary else pal.muted
                labelPaint.textSize = context.dpF(if (isLast) 9.5f else 8.5f)
                val txt = if (v > 0) moneyLabel(v).replace("R$ ", "") else "0"
                canvas.drawText(txt, x + barW / 2f, h - context.dpF(2f), labelPaint)
            }
        }
        labelPaint.color = pal.primary
        labelPaint.textSize = context.dpF(9.5f)
        canvas.drawText("hoje", w - slot / 2f, baseY - context.dpF(24f), labelPaint)
    }
}
