package com.example.facerecognitionfinal.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class ThresholdCalibrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 96, 165, 250) // 淡淡的科幻蓝网格
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 140, 160, 180)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 24f
    }

    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val thresholdLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 167, 38) // 亮橙色
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val thresholdTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 167, 38)
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val intraLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 166, 154) // 绿
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val interLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(228, 87, 107) // 红
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val modeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        textSize = 20f
        textAlign = Paint.Align.RIGHT
    }

    // Mock datasets generated with fixed seed
    private val mockIntra = mutableListOf<Float>()
    private val mockInter = mutableListOf<Float>()

    // Real datasets
    private var realIntra: List<Float> = emptyList()
    private var realInter: List<Float> = emptyList()
    private var currentThreshold: Float = 10.0f
    private var isMock: Boolean = true

    init {
        val random = java.util.Random(42)
        // Same-person (intra-class): mean = 6.5, std = 1.3
        repeat(200) {
            val valIntra = (random.nextGaussian() * 1.3 + 6.5).toFloat().coerceIn(1f, 19f)
            mockIntra.add(valIntra)
        }
        // Cross-person (inter-class): mean = 13.5, std = 1.6
        repeat(350) {
            val valInter = (random.nextGaussian() * 1.6 + 13.5).toFloat().coerceIn(1f, 19f)
            mockInter.add(valInter)
        }
    }

    fun setData(intra: List<Float>, inter: List<Float>, threshold: Float) {
        realIntra = intra
        realInter = inter
        currentThreshold = threshold
        isMock = realIntra.isEmpty() || realInter.isEmpty()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 16, 27)) // 深色科技背景

        val width = width.toFloat()
        val height = height.toFloat()

        val paddingLeft = 60f
        val paddingRight = 40f
        val paddingTop = 60f
        val paddingBottom = 50f

        val drawWidth = width - paddingLeft - paddingRight
        val drawHeight = height - paddingTop - paddingBottom

        // Draw axes
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, axisPaint) // X Axis
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint) // Y Axis

        // Draw grids & X axis labels
        val gridValues = floatArrayOf(0f, 5f, 10f, 15f, 20f)
        for (gv in gridValues) {
            val gx = paddingLeft + (gv / 20f) * drawWidth
            // vertical grid line
            canvas.drawLine(gx, paddingTop, gx, height - paddingBottom, gridPaint)
            // label
            val label = String.format(java.util.Locale.CHINA, "%.1f", gv)
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, gx - textWidth / 2f, height - paddingBottom + 32f, textPaint)
        }

        // Draw Y axis label
        canvas.save()
        canvas.rotate(-90f, paddingLeft / 2f, height / 2f)
        val yAxisLabel = "相对频数密度"
        val yLabelWidth = textPaint.measureText(yAxisLabel)
        canvas.drawText(yAxisLabel, (paddingLeft / 2f) - yLabelWidth / 2f, (height / 2f) + 10f, textPaint)
        canvas.restore()

        val intra = if (isMock) mockIntra else realIntra
        val inter = if (isMock) mockInter else realInter

        // Calculate histograms
        val smoothedIntra = computeSmoothedHistogram(intra)
        val smoothedInter = computeSmoothedHistogram(inter)

        val maxSmoothed = max(smoothedIntra.maxOrNull() ?: 1f, smoothedInter.maxOrNull() ?: 1f)

        // Draw Intra-class distribution
        drawDistribution(canvas, smoothedIntra, maxSmoothed, paddingLeft, paddingTop, drawWidth, drawHeight, height - paddingBottom, Color.rgb(20, 166, 154), intraLinePaint)

        // Draw Inter-class distribution
        drawDistribution(canvas, smoothedInter, maxSmoothed, paddingLeft, paddingTop, drawWidth, drawHeight, height - paddingBottom, Color.rgb(228, 87, 107), interLinePaint)

        // Draw threshold line
        val tx = paddingLeft + (currentThreshold / 20f) * drawWidth
        if (tx in paddingLeft..(width - paddingRight)) {
            canvas.drawLine(tx, paddingTop, tx, height - paddingBottom, thresholdLinePaint)
            val thText = String.format(java.util.Locale.CHINA, "阈值: %.1f", currentThreshold)
            canvas.drawText(thText, tx, paddingTop - 12f, thresholdTextPaint)
        }

        // Draw Legend
        drawLegend(canvas, paddingLeft, paddingTop)

        // Draw Data mode indicator
        val modeText = if (isMock) "演示模拟曲线（录入多人激活真实分析）" else "真实库内样本诊断（样本对: 同人${intra.size}/跨人${inter.size}）"
        canvas.drawText(modeText, width - paddingRight, paddingTop - 12f, modeTextPaint)
    }

    private fun computeSmoothedHistogram(distances: List<Float>): FloatArray {
        val binCounts = FloatArray(40)
        for (d in distances) {
            val binIndex = ((d / 20f) * 40).toInt().coerceIn(0, 39)
            binCounts[binIndex]++
        }
        val smoothed = FloatArray(40)
        for (i in 0 until 40) {
            var sum = 0f
            var weightSum = 0f
            for (offset in -2..2) {
                val idx = i + offset
                if (idx in 0..39) {
                    val w = when (abs(offset)) {
                        0 -> 6f
                        1 -> 3f
                        else -> 1f
                    }
                    sum += binCounts[idx] * w
                    weightSum += w
                }
            }
            smoothed[i] = if (weightSum > 0f) sum / weightSum else 0f
        }
        return smoothed
    }

    private fun drawDistribution(
        canvas: Canvas,
        smoothed: FloatArray,
        maxVal: Float,
        paddingLeft: Float,
        paddingTop: Float,
        drawWidth: Float,
        drawHeight: Float,
        baselineY: Float,
        color: Int,
        linePaint: Paint
    ) {
        val path = Path()
        path.moveTo(paddingLeft, baselineY)

        for (i in 0 until 40) {
            val x = paddingLeft + (i / 39f) * drawWidth
            val h = (smoothed[i] / maxVal) * drawHeight * 0.78f
            val y = baselineY - h
            path.lineTo(x, y)
        }
        path.lineTo(paddingLeft + drawWidth, baselineY)
        path.close()

        // Draw fill with semi-transparent gradient
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                paddingLeft, paddingTop, paddingLeft, baselineY,
                Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(5, Color.red(color), Color.green(color), Color.blue(color)),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, fillPaint)

        // Draw stroke line on top
        val strokePath = Path()
        val firstH = (smoothed[0] / maxVal) * drawHeight * 0.78f
        strokePath.moveTo(paddingLeft, baselineY - firstH)
        for (i in 1 until 40) {
            val x = paddingLeft + (i / 39f) * drawWidth
            val h = (smoothed[i] / maxVal) * drawHeight * 0.78f
            strokePath.lineTo(x, baselineY - h)
        }
        canvas.drawPath(strokePath, linePaint)
    }

    private fun drawLegend(canvas: Canvas, x: Float, y: Float) {
        val squareSize = 14f
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Intra legend
        legendPaint.color = Color.rgb(20, 166, 154)
        val intraRect = RectF(x + 20f, y - 44f, x + 20f + squareSize, y - 44f + squareSize)
        canvas.drawRect(intraRect, legendPaint)
        canvas.drawText("同人样本距离", x + 44f, y - 31f, legendTextPaint)

        // Inter legend
        legendPaint.color = Color.rgb(228, 87, 107)
        val interRect = RectF(x + 220f, y - 44f, x + 220f + squareSize, y - 44f + squareSize)
        canvas.drawRect(interRect, legendPaint)
        canvas.drawText("跨人样本距离", x + 244f, y - 31f, legendTextPaint)
    }
}
