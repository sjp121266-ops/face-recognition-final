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
 * Simple animated horizontal bar chart.
 */
class AnalyticsBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val value: Float, val color: Int)

    private val bars = mutableListOf<Bar>()
    private var animProgress = 0f
    private var maxValue = 1f
    private var title = ""

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#21262D"); style = Paint.Style.FILL
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6EDF3"); textSize = 22f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E"); textSize = 20f; textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E"); textSize = 24f; isFakeBoldText = true
    }
    private var animator: ValueAnimator? = null

    fun setData(title: String, bars: List<Bar>) {
        this.title = title
        this.bars.clear()
        this.bars.addAll(bars)
        this.maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500; interpolator = DecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val padding = 40f
        var y = padding + 30f
        val barHeight = 32f
        val barSpacing = 18f
        val labelWidth = 140f
        val availableWidth = width - padding * 2 - labelWidth - 50f
        val barRightX = padding + labelWidth + availableWidth

        // Title
        canvas.drawText(title, padding, y, titlePaint)
        y += 40f

        bars.forEach { bar ->
            // Label
            canvas.drawText(bar.label, padding, y + barHeight * 0.6f, labelPaint)

            // Bar background
            canvas.drawRoundRect(
                RectF(padding + labelWidth, y, barRightX, y + barHeight),
                6f, 6f, barBgPaint
            )

            // Bar fill (animated)
            val barWidth = (bar.value / maxValue) * availableWidth * animProgress
            barPaint.color = bar.color
            if (barWidth > 8f) {
                canvas.drawRoundRect(
                    RectF(padding + labelWidth, y, padding + labelWidth + barWidth, y + barHeight),
                    6f, 6f, barPaint
                )
            }

            // Value text
            val valueText = bar.value.toInt().toString()
            canvas.drawText(valueText, barRightX + 40f, y + barHeight * 0.65f, valuePaint)

            y += barHeight + barSpacing
        }
    }
}
