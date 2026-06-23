package com.example.facerecognitionfinal.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * Animated circular confidence gauge ring.
 *
 * Draws a sweeping arc from 0..360° with a color gradient
 * (red → yellow → green) that fills up based on confidence level.
 * Shows the percentage and label in the center.
 */
class ConfidenceRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var confidence = 0f       // 0..100
    private var displayConfidence = 0f
    private var label = ""
    private var subLabel = ""
    private var ringColor = 0
    private var isActive = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.parseColor("#30363D")
        strokeCap = Paint.Cap.ROUND
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
        alpha = 40
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#E6EDF3")
        textSize = 48f
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#8B949E")
        textSize = 28f
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#58677A")
        textSize = 22f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private val ringRect = RectF()

    private val gradientColors = intArrayOf(
        Color.parseColor("#E4576B"),  // red
        Color.parseColor("#FF8C42"),  // orange
        Color.parseColor("#FFD23F"),  // yellow
        Color.parseColor("#7ED957"),  // light green
        Color.parseColor("#14A69A")   // teal green
    )
    private val gradientPositions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    fun setConfidence(value: Float, name: String, detail: String = "") {
        confidence = value.coerceIn(0f, 100f)
        label = name
        subLabel = detail
        isActive = true
        ringColor = confidenceToColor(confidence)
        dotPaint.color = ringColor

        animator?.cancel()
        animator = ValueAnimator.ofFloat(displayConfidence, confidence).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayConfidence = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        isActive = false
        animator?.cancel()
        displayConfidence = 0f
        confidence = 0f
        label = ""
        subLabel = ""
        ringColor = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val stroke = ringPaint.strokeWidth
        val padding = stroke + 16f
        ringRect.set(padding, padding, size - padding, size - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // Background ring
        canvas.drawArc(ringRect, 135f, 270f, false, bgPaint)

        if (!isActive && displayConfidence <= 0f) {
            // Idle state
            centerTextPaint.textSize = 32f
            canvas.drawText("等待识别", cx, cy + 10f, centerTextPaint)
            centerTextPaint.textSize = 48f
            return
        }

        // Update gradient shader for the ring
        val gradient = SweepGradient(cx, cy, gradientColors, gradientPositions)
        ringPaint.shader = gradient
        glowPaint.shader = gradient

        // Sweep angle: 270° total range, confidence maps 0..100 → 0..270
        val sweepAngle = (displayConfidence / 100f) * 270f

        // Glow effect
        if (sweepAngle > 0f) {
            canvas.drawArc(ringRect, 135f, sweepAngle, false, glowPaint)
        }
        // Main ring
        canvas.drawArc(ringRect, 135f, sweepAngle, false, ringPaint)

        // Leading dot
        if (sweepAngle > 0f) {
            val dotAngle = Math.toRadians((135f + sweepAngle).toDouble())
            val dotRadius = ringRect.width() / 2f
            val dotX = cx + dotRadius * Math.cos(dotAngle).toFloat()
            val dotY = cy + dotRadius * Math.sin(dotAngle).toFloat()
            dotPaint.alpha = 255
            canvas.drawCircle(dotX, dotY, 10f, dotPaint)
        }

        // Center text: confidence percentage
        val percentText = "${displayConfidence.toInt()}%"
        canvas.drawText(percentText, cx, cy - 4f, centerTextPaint)

        // Label
        if (label.isNotEmpty()) {
            canvas.drawText(label, cx, cy + 38f, labelPaint)
        }
        // Sub-label
        if (subLabel.isNotEmpty()) {
            canvas.drawText(subLabel, cx, cy + 68f, subLabelPaint)
        }
    }

    private fun confidenceToColor(value: Float): Int {
        return when {
            value >= 80f -> Color.parseColor("#14A69A")
            value >= 60f -> Color.parseColor("#7ED957")
            value >= 40f -> Color.parseColor("#FFD23F")
            value >= 20f -> Color.parseColor("#FF8C42")
            else -> Color.parseColor("#E4576B")
        }
    }
}
