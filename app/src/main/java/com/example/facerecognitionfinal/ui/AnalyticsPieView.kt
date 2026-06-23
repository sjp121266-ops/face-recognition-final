package com.example.facerecognitionfinal.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * Simple animated pie/donut chart for recognition statistics.
 */
class AnalyticsPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val value: Float, val color: Int)

    private val slices = mutableListOf<Slice>()
    private var animProgress = 0f
    private var totalValue = 0f
    private var centerText = ""
    private var centerSubText = ""

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor("#E6EDF3")
        textSize = 32f; isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor("#8B949E"); textSize = 22f
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f }
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    fun setData(slices: List<Slice>, centerText: String, centerSubText: String = "") {
        this.slices.clear()
        this.slices.addAll(slices)
        this.totalValue = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
        this.centerText = centerText
        this.centerSubText = centerSubText

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600; interpolator = DecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val padding = 40f
        rect.set(padding, padding, size - padding, size - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty() || totalValue <= 0f) return

        val cx = rect.centerX()
        val cy = rect.centerY()
        val sweepScale = animProgress
        var startAngle = -90f

        for (slice in slices) {
            val sweep = (slice.value / totalValue) * 360f * sweepScale
            arcPaint.color = slice.color
            canvas.drawArc(rect, startAngle, sweep, true, arcPaint)
            startAngle += sweep
        }

        // Center hole (donut)
        arcPaint.color = Color.parseColor("#161B22")
        val holeRadius = rect.width() * 0.28f
        canvas.drawCircle(cx, cy, holeRadius, arcPaint)

        // Center text
        val fm = centerPaint.fontMetrics
        canvas.drawText(centerText, cx, cy - (fm.ascent + fm.descent) / 2, centerPaint)
        if (centerSubText.isNotEmpty()) {
            canvas.drawText(centerSubText, cx, cy + 36f, subPaint)
        }

        // Legend below
        var legendY = rect.bottom + 36f
        val legendX = 40f
        slices.forEach { slice ->
            legendPaint.color = slice.color
            canvas.drawRect(legendX, legendY - 12f, legendX + 20f, legendY + 4f, legendPaint)
            legendPaint.color = Color.parseColor("#E6EDF3")
            val pct = "%.0f%%".format(slice.value / totalValue * 100f)
            canvas.drawText("${slice.label}  $pct", legendX + 28f, legendY, legendPaint)
            legendY += 28f
        }
    }
}
