package pt.isep.agriot

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Gráfico de linhas simples (sem bibliotecas externas). */
class LineChartView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    class Serie(
        val values: List<Double>,
        val color: Int,
        val fill: Boolean = false,
        val dashed: Boolean = false,
        val width: Float = 2.5f
    )

    private var series: List<Serie> = emptyList()

    /** Se > 0, o eixo X tem este número fixo de pontos (o gráfico "cresce" da esquerda para a direita). */
    var maxPoints = 0
    var yMin: Double? = null
    var yMax: Double? = null

    private val density = resources.displayMetrics.density
    private val gridPaint = Paint().apply {
        color = 0xFFE6ECE6.toInt()
        strokeWidth = 1.5f * density
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setData(s: List<Serie>) {
        series = s
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4f * density
        for (i in 0..3) {
            val y = (h * i / 3f).coerceIn(1f, h - 1f)
            c.drawLine(0f, y, w, y, gridPaint)
        }
        val all = series.flatMap { it.values }
        if (all.isEmpty()) return
        val lo = yMin ?: (all.min() - 0.5)
        var hi = yMax ?: (all.max() + 0.5)
        if (hi - lo < 1e-6) hi = lo + 1.0

        for (s in series) {
            if (s.values.size < 2) continue
            val n = if (maxPoints > 1) maxPoints else s.values.size
            val path = Path()
            var lastX = 0f
            s.values.forEachIndexed { i, v ->
                val x = w * i / (n - 1).toFloat()
                val y = h - pad - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * (h - 2 * pad)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                lastX = x
            }
            if (s.fill) {
                val area = Path(path)
                area.lineTo(lastX, h)
                area.lineTo(0f, h)
                area.close()
                fillPaint.color = (s.color and 0x00FFFFFF) or 0x33000000
                c.drawPath(area, fillPaint)
            }
            linePaint.color = s.color
            linePaint.strokeWidth = s.width * density
            linePaint.pathEffect = if (s.dashed) DashPathEffect(floatArrayOf(14f, 10f), 0f) else null
            c.drawPath(path, linePaint)
        }
    }
}
