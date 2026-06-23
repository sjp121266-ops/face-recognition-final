package com.example.facerecognitionfinal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.facerecognitionfinal.ml.FeatureDimReducer
import kotlin.math.abs

class FeatureNebulaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(24, 96, 165, 250) // 淡淡的科幻蓝网格
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 211, 238) // 荧光青色
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val crosshairDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 211, 238)
        style = Paint.Style.FILL
    }
    private val scanRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 211, 238)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val scanTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 211, 238)
        textSize = 22f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        textSize = 28f
        typeface = android.graphics.Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    private var points: List<FeatureDimReducer.Point2D> = emptyList()
    private var scanPoint: FeatureDimReducer.Point2D? = null

    @Synchronized
    fun setPoints(libraryPoints: List<FeatureDimReducer.Point2D>, scan: FeatureDimReducer.Point2D?) {
        this.points = libraryPoints
        this.scanPoint = scan
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 16, 27)) // 深色夜空背景

        val allPoints = points + listOfNotNull(scanPoint)
        if (allPoints.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        // 1. 寻找特征坐标的边界包围盒，用于动态缩放
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (pt in allPoints) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }

        val widthRange = (maxX - minX).coerceAtLeast(10f)
        val heightRange = (maxY - minY).coerceAtLeast(10f)

        // 缩放比例，留出 35% 边界安全区
        val marginFactor = 0.65f
        val scaleX = (width * marginFactor) / widthRange
        val scaleY = (height * marginFactor) / heightRange
        val scale = minOf(scaleX, scaleY).coerceAtMost(3.0f).coerceAtLeast(0.4f)

        val viewCenterX = width / 2f
        val viewCenterY = height / 2f

        // 2. 绘制背景网格和圆形轨道
        drawCyberGrid(canvas, viewCenterX, viewCenterY)

        // 3. 按姓名分组，绘制同一人的散点簇、质心以及阈值边界圆
        val grouped = points.groupBy { it.name }
        for ((name, groupList) in grouped) {
            val clusterColor = getColorForName(name)

            // 计算该组的几何质心
            var sumX = 0f
            var sumY = 0f
            for (p in groupList) {
                sumX += p.x
                sumY += p.y
            }
            val cx = sumX / groupList.size
            val cy = sumY / groupList.size

            val screenCx = viewCenterX + cx * scale
            val screenCy = viewCenterY + cy * scale

            // 绘制特征边界环，半径对应本地 10.0 的决策边界
            // 在 reducer 中已把 1 维 L2 乘以 L2_SCALE(60) 像素化，这里做对应比例尺变换
            val borderRadius = 10f * FeatureDimReducer.L2_SCALE * scale

            // 填充半透明决策区
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(12, Color.red(clusterColor), Color.green(clusterColor), Color.blue(clusterColor))
            }
            canvas.drawCircle(screenCx, screenCy, borderRadius, fillPaint)

            // 绘制虚线决策圈
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                color = Color.argb(70, Color.red(clusterColor), Color.green(clusterColor), Color.blue(clusterColor))
            }
            canvas.drawCircle(screenCx, screenCy, borderRadius, borderPaint)

            // 绘制质心姓名标签
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, Color.red(clusterColor), Color.green(clusterColor), Color.blue(clusterColor))
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(name, screenCx, screenCy - borderRadius - 10f, labelPaint)

            // 绘制所有样本点
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = clusterColor
            }
            for (p in groupList) {
                val sx = viewCenterX + p.x * scale
                val sy = viewCenterY + p.y * scale
                canvas.drawCircle(sx, sy, 10f, dotPaint)
            }
        }

        // 4. 绘制当前扫描点（带十字准星与呼吸波纹）
        val scan = scanPoint
        if (scan != null) {
            val sx = viewCenterX + scan.x * scale
            val sy = viewCenterY + scan.y * scale
            drawScannerCrosshair(canvas, sx, sy)
            postInvalidateOnAnimation() // 动画更新
        }
    }

    private fun drawCyberGrid(canvas: Canvas, cx: Float, cy: Float) {
        // 画十字轴线
        canvas.drawLine(0f, cy, width.toFloat(), cy, gridPaint)
        canvas.drawLine(cx, 0f, cx, height.toFloat(), gridPaint)

        // 画同心圆弧轨道
        val maxRadius = minOf(cx, cy)
        canvas.drawCircle(cx, cy, maxRadius * 0.4f, gridPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.8f, gridPaint)
    }

    private fun drawScannerCrosshair(canvas: Canvas, x: Float, y: Float) {
        // 1. 绘制准星十字
        val len = 25f
        canvas.drawLine(x - len, y, x + len, y, crosshairPaint)
        canvas.drawLine(x, y - len, x, y + len, crosshairPaint)
        canvas.drawCircle(x, y, 6f, crosshairDotPaint)

        // 2. 绘制呼吸扩散环
        val pulse = (System.currentTimeMillis() % 1000L) / 1000L.toFloat()
        val ringRadius = 15f + 35f * pulse
        scanRingPaint.color = Color.argb((180 * (1f - pulse)).toInt(), 34, 211, 238)
        canvas.drawCircle(x, y, ringRadius, scanRingPaint)

        // 3. 绘制文字标签
        canvas.drawText("【实时落点】", x + 30f, y + 8f, scanTextPaint)
    }

    private fun drawEmptyState(canvas: Canvas) {
        canvas.drawText("暂无特征样本点，请先在上方录入人员", width / 2f, height / 2f + 10f, emptyTextPaint)
    }

    private fun getColorForName(name: String): Int {
        val hash = name.hashCode()
        // 基于 HSL/HSV 空间分配亮丽色彩：Hue 动态计算，饱和度 0.82，明度 0.90
        val hue = (abs(hash) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.82f, 0.90f))
    }
}
