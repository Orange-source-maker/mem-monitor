package com.memmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.memmonitor.core.MemorySampler

/**
 * 实时内存占用曲线（自绘，无第三方图表依赖）。
 * X 轴为采样点序列，Y 轴为占用百分比 0~100。
 */
class MemoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var samples: List<MemorySampler.Sample> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#3D8BFD")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#2A3040")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        color = Color.parseColor("#8A93A6")
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#66E05555")
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun setData(data: List<MemorySampler.Sample>) {
        samples = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padL = 70f
        val padR = 16f
        val padT = 16f
        val padB = 34f
        val w = width - padL - padR
        val h = height - padT - padB
        if (w <= 0 || h <= 0) return

        // 网格与刻度：0/25/50/75/100
        for (p in intArrayOf(0, 25, 50, 75, 100)) {
            val y = padT + h * (1 - p / 100f)
            canvas.drawLine(padL, y, padL + w, y, gridPaint)
            canvas.drawText("$p%", 8f, y + 9f, textPaint)
        }
        // 90% 警戒线
        val warnY = padT + h * 0.10f
        canvas.drawLine(padL, warnY, padL + w, warnY, warnPaint)

        if (samples.size < 2) {
            canvas.drawText("等待采样数据…", padL + 12f, padT + h / 2, textPaint)
            return
        }

        linePath.reset()
        fillPath.reset()
        val n = samples.size
        val step = w / (n - 1).coerceAtLeast(1)
        for (i in samples.indices) {
            val x = padL + i * step
            val pct = samples[i].usedPercent.coerceIn(0f, 100f)
            val y = padT + h * (1 - pct / 100f)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, padT + h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(padL + (n - 1) * step, padT + h)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + h,
            Color.parseColor("#553D8BFD"), Color.parseColor("#083D8BFD"),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        // 最新值端点
        val last = samples.last()
        val lx = padL + (n - 1) * step
        val ly = padT + h * (1 - last.usedPercent.coerceIn(0f, 100f) / 100f)
        canvas.drawCircle(lx, ly, 7f, linePaint.apply { style = Paint.Style.FILL })
        linePaint.style = Paint.Style.STROKE
    }
}
