package com.example.facerecognitionfinal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.PointF
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 184, 166)
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val unknownBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(248, 113, 113)
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val scanningBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(96, 165, 250)
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 211, 238)
        style = Paint.Style.STROKE
        strokeWidth = 7.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val thinCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 125, 211, 252)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val scanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 125, 211, 252)
        style = Paint.Style.FILL
    }
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 17, 24, 39)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textSize = 24f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val livenessBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 158, 11) // Amber
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val livenessTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val livenessSubtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 24f
        typeface = android.graphics.Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }
    private val livenessBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 15, 23, 42) // Dark Slate
        style = Paint.Style.FILL
    }

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val meshNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hudTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(209, 213, 219)
        textSize = 12.5f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    @Volatile
    var livenessInstruction: String? = null
        set(value) {
            field = value
            postInvalidate()
        }

    @Volatile
    var livenessStepInfo: String? = null
        set(value) {
            field = value
            postInvalidate()
        }

    @Volatile
    var livenessTimeLeft: Int = 0
        set(value) {
            field = value
            postInvalidate()
        }

    @Volatile
    var livenessStatus: LivenessStatus = LivenessStatus.NONE
        set(value) {
            field = value
            postInvalidate()
        }

    enum class LivenessStatus {
        NONE,
        CHECKING,
        PASSED,
        FAILED
    }

    private var faces: List<FaceBox> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var mirrorHorizontally: Boolean = false
    private var liveAnimationEnabled: Boolean = false

    fun setLiveAnimationEnabled(enabled: Boolean) {
        liveAnimationEnabled = enabled
        if (enabled) {
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }

    fun setFaces(
        faces: List<FaceBox>,
        imageWidth: Int,
        imageHeight: Int,
        mirrorHorizontally: Boolean
    ) {
        this.faces = faces
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.mirrorHorizontally = mirrorHorizontally
        invalidate()
    }

    fun clear() {
        faces = emptyList()
        invalidate()
    }

    // Recognition pulse animation state
    private var pulseAlpha = 0f
    private var pulseRadius = 0f
    private var pulseActive = false
    private var pulseStartTime = 0L

    fun showRecognitionPulse() {
        pulseActive = true
        pulseStartTime = System.currentTimeMillis()
        pulseAlpha = 1f
        pulseRadius = 0f
        postInvalidate()
        // Animate pulse over 800ms
        postDelayed({
            pulseActive = false
            postInvalidate()
        }, 800)
    }

    private fun drawLivenessOverlay(canvas: Canvas) {
        if (livenessStatus == LivenessStatus.PASSED) {
            val tintPaint = Paint().apply {
                color = Color.argb(30, 34, 197, 94) // Light green tint
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
        } else if (livenessStatus == LivenessStatus.FAILED) {
            val tintPaint = Paint().apply {
                color = Color.argb(30, 239, 68, 68) // Light red tint
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
        }
    }

    private fun drawLivenessHud(canvas: Canvas) {
        val bannerWidth = width * 0.85f
        val bannerHeight = 130f
        val bannerLeft = (width - bannerWidth) / 2f
        val bannerTop = 40f
        val rect = RectF(bannerLeft, bannerTop, bannerLeft + bannerWidth, bannerTop + bannerHeight)
        
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = when (livenessStatus) {
                LivenessStatus.PASSED -> Color.rgb(34, 197, 94)
                LivenessStatus.FAILED -> Color.rgb(239, 68, 68)
                else -> Color.rgb(245, 158, 11)
            }
        }
        
        canvas.drawRoundRect(rect, 16f, 16f, livenessBannerPaint)
        canvas.drawRoundRect(rect, 16f, 16f, borderPaint)
        
        val instructionText = livenessInstruction ?: "请对准摄像头"
        val stepInfo = livenessStepInfo ?: ""
        val timeLeftText = if (livenessStatus == LivenessStatus.CHECKING && livenessTimeLeft > 0) " | 剩 ${livenessTimeLeft} 秒" else ""
        val subtext = if (livenessStatus == LivenessStatus.PASSED) "验证成功" else if (livenessStatus == LivenessStatus.FAILED) "验证失败" else "$stepInfo$timeLeftText"
        
        val textY = rect.centerY() - 6f
        canvas.drawText(instructionText, rect.centerX(), textY, livenessTextPaint)
        canvas.drawText(subtext, rect.centerX(), rect.centerY() + 30f, livenessSubtextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (livenessStatus != LivenessStatus.NONE) {
            drawLivenessOverlay(canvas)
            drawLivenessHud(canvas)
        }

        if (faces.isEmpty() || imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        val scale = maxOf(width / imageWidth.toFloat(), height / imageHeight.toFloat())
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val dx = (width - scaledWidth) / 2f
        val dy = (height - scaledHeight) / 2f

        val mappedFaces = faces.map { face ->
            MappedFace(face, mapRect(face.bounds, scale, dx, dy), statusFor(face))
        }

        drawMultiFaceAtmosphere(canvas, mappedFaces)

        val labelRects = mappedFaces.map { mappedFace ->
            labelRectFor(mappedFace.face.label, mappedFace.rect, mappedFace.status)
        }

        drawFaceCount(canvas, mappedFaces.size)

        mappedFaces.forEachIndexed { index, mappedFace ->
            drawContourMesh(canvas, mappedFace, scale, dx, dy)
            drawTechFrame(canvas, mappedFace.rect, mappedFace.status, labelRects[index])
        }
        mappedFaces.forEachIndexed { index, mappedFace ->
            drawLabel(canvas, mappedFace.face.label, mappedFace.rect, mappedFace.status, labelRects[index])
            drawTelemetryPanel(canvas, mappedFace)
        }

        // Draw recognition pulse
        if (pulseActive) {
            drawRecognitionPulse(canvas)
        }

        if (liveAnimationEnabled) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawRecognitionPulse(canvas: Canvas) {
        val elapsed = System.currentTimeMillis() - pulseStartTime
        val progress = (elapsed / 800f).coerceIn(0f, 1f)
        pulseRadius = width.coerceAtMost(height) * 0.7f * progress
        pulseAlpha = (1f - progress) * 0.5f

        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb((pulseAlpha * 255).toInt(), 63, 125, 246)
        }
        canvas.drawCircle(width / 2f, height / 2f, pulseRadius, pulsePaint)

        if (pulseActive) {
            postInvalidateDelayed(16)
        }
    }

    private fun statusFor(face: FaceBox): FaceStatus {
        return when {
            livenessStatus == LivenessStatus.CHECKING -> FaceStatus.LIVENESS
            face.label.contains("识别中") -> FaceStatus.SCANNING
            face.isKnown -> FaceStatus.KNOWN
            else -> FaceStatus.UNKNOWN
        }
    }

    private fun drawMultiFaceAtmosphere(canvas: Canvas, mappedFaces: List<MappedFace>) {
        if (mappedFaces.size < 2) return

        val alpha = (26 + min(mappedFaces.size, 6) * 6).coerceAtMost(62)
        mappedFaces.forEach { mappedFace ->
            val rect = RectF(mappedFace.rect).apply {
                inset(-18f, -18f)
            }
            fillPaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                Color.argb(alpha, Color.red(mappedFace.status.accentColor), Color.green(mappedFace.status.accentColor), Color.blue(mappedFace.status.accentColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, 24f, 24f, fillPaint)
            fillPaint.shader = null
        }
    }

    private fun drawTechFrame(canvas: Canvas, rect: RectF, status: FaceStatus, labelRect: RectF) {
        val accentColor = status.accentColor
        val subduedColor = status.subduedColor
        val basePaint = paintForStatus(status)

        haloPaint.color = Color.argb(46, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(rect, 18f, 18f, haloPaint)

        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            Color.argb(5, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, 16f, 16f, fillPaint)
        fillPaint.shader = null

        canvas.drawRoundRect(rect, 14f, 14f, basePaint)

        cornerPaint.color = accentColor
        thinCornerPaint.color = subduedColor
        val cornerLength = min(rect.width(), rect.height()) * 0.22f
        val innerInset = 8f
        drawCorners(canvas, rect, cornerLength, cornerPaint)
        drawCorners(canvas, RectF(rect).apply { inset(innerInset, innerInset) }, cornerLength * 0.58f, thinCornerPaint)

        drawStatusTicks(canvas, rect, status)
        drawScanningLine(canvas, rect, status, labelRect)
    }

    private fun drawCorners(canvas: Canvas, rect: RectF, cornerLength: Float, paint: Paint) {
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLength, rect.top, paint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLength, paint)
        canvas.drawLine(rect.right, rect.top, rect.right - cornerLength, rect.top, paint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLength, paint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLength, rect.bottom, paint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLength, paint)
        canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLength, rect.bottom, paint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLength, paint)
    }

    private fun drawStatusTicks(canvas: Canvas, rect: RectF, status: FaceStatus) {
        val tickWidth = min(40f, max(18f, rect.width() * 0.14f))
        val tickTop = rect.top + 14f
        val tickRight = rect.right - 12f
        thinCornerPaint.color = status.accentColor
        thinCornerPaint.strokeWidth = 3f
        repeat(status.tickCount) { index ->
            val y = tickTop + index * 9f
            canvas.drawLine(tickRight - tickWidth, y, tickRight, y, thinCornerPaint)
        }
        thinCornerPaint.strokeWidth = 2.5f
    }

    private fun drawScanningLine(canvas: Canvas, rect: RectF, status: FaceStatus, labelRect: RectF) {
        if (!liveAnimationEnabled) return

        val phase = (SystemClock.uptimeMillis() % SCAN_DURATION_MS) / SCAN_DURATION_MS.toFloat()
        val wave = sin(phase * Math.PI * 2).toFloat()
        var scanY = rect.top + rect.height() * phase
        val labelSafety = RectF(labelRect).apply { inset(-6f, -6f) }
        if (labelSafety.intersects(rect.left, scanY - 18f, rect.right, scanY + 18f)) {
            scanY = (labelSafety.bottom + 22f).coerceAtMost(rect.bottom - 10f)
        }

        val pulse = 0.6f + 0.4f * wave.coerceAtLeast(0f)
        val red = Color.red(status.scanColor)
        val green = Color.green(status.scanColor)
        val blue = Color.blue(status.scanColor)

        scanGlowPaint.shader = LinearGradient(
            rect.left,
            scanY - 22f,
            rect.left,
            scanY + 22f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb((42 * pulse).toInt(), red, green, blue),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect.left + 8f, scanY - 22f, rect.right - 8f, scanY + 22f, scanGlowPaint)
        scanGlowPaint.shader = null

        scanPaint.color = Color.argb((130 + 95 * pulse).toInt(), red, green, blue)
        canvas.drawLine(rect.left + 12f, scanY, rect.right - 12f, scanY, scanPaint)

        val notch = 8f
        canvas.drawLine(rect.left + 18f, scanY - notch, rect.left + 18f, scanY + notch, scanPaint)
        canvas.drawLine(rect.right - 18f, scanY - notch, rect.right - 18f, scanY + notch, scanPaint)
    }

    private fun labelRectFor(label: String, rect: RectF, status: FaceStatus): RectF {
        val horizontalPadding = 14f
        val verticalPadding = 9f
        val statusGap = 8f
        val statusWidth = statusPaint.measureText(status.text) + STATUS_HORIZONTAL_PADDING * 2
        val textWidth = labelPaint.measureText(label)
        val desiredWidth = textWidth + horizontalPadding * 2 + statusGap + statusWidth
        val labelWidth = desiredWidth.coerceAtMost(width.toFloat() - LABEL_SCREEN_PADDING * 2)
        val labelHeight = max(
            labelPaint.textSize + verticalPadding * 2,
            statusPaint.textSize + STATUS_VERTICAL_PADDING * 2 + 6f
        )
        val labelLeft = rect.left
            .coerceAtLeast(LABEL_SCREEN_PADDING)
            .coerceAtMost((width - labelWidth - LABEL_SCREEN_PADDING).coerceAtLeast(LABEL_SCREEN_PADDING))
        val aboveTop = rect.top - labelHeight - 10f
        val labelTop = if (aboveTop >= LABEL_SCREEN_PADDING) {
            aboveTop
        } else {
            (rect.top + 10f).coerceAtMost((height - labelHeight - LABEL_SCREEN_PADDING).coerceAtLeast(LABEL_SCREEN_PADDING))
        }

        return RectF(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
    }

    private fun paintForStatus(status: FaceStatus): Paint {
        return when (status) {
            FaceStatus.SCANNING -> scanningBoxPaint
            FaceStatus.KNOWN -> boxPaint
            FaceStatus.LIVENESS -> livenessBoxPaint
            else -> unknownBoxPaint
        }
    }

    private fun drawLabel(canvas: Canvas, label: String, rect: RectF, status: FaceStatus, labelRect: RectF) {
        val horizontalPadding = 14f
        val verticalPadding = 9f
        val statusText = status.text
        val statusWidth = statusPaint.measureText(statusText) + STATUS_HORIZONTAL_PADDING * 2

        labelBackgroundPaint.color = Color.argb(224, 8, 13, 23)
        canvas.drawRoundRect(labelRect, 10f, 10f, labelBackgroundPaint)

        fillPaint.shader = LinearGradient(
            labelRect.left,
            labelRect.top,
            labelRect.right,
            labelRect.bottom,
            Color.argb(96, Color.red(status.accentColor), Color.green(status.accentColor), Color.blue(status.accentColor)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(labelRect, 10f, 10f, fillPaint)
        fillPaint.shader = null

        val statusRect = RectF(
            labelRect.right - statusWidth - horizontalPadding,
            labelRect.centerY() - (statusPaint.textSize + STATUS_VERTICAL_PADDING * 2) / 2f,
            labelRect.right - horizontalPadding,
            labelRect.centerY() + (statusPaint.textSize + STATUS_VERTICAL_PADDING * 2) / 2f
        )
        labelBackgroundPaint.color = Color.argb(220, Color.red(status.accentColor), Color.green(status.accentColor), Color.blue(status.accentColor))
        canvas.drawRoundRect(statusRect, 8f, 8f, labelBackgroundPaint)

        val maxLabelRight = statusRect.left - 8f
        val baseline = labelRect.bottom - verticalPadding - 6f
        canvas.save()
        canvas.clipRect(labelRect.left + horizontalPadding, labelRect.top, maxLabelRight, labelRect.bottom)
        canvas.drawText(label, labelRect.left + horizontalPadding, baseline, labelPaint)
        canvas.restore()

        statusPaint.color = Color.WHITE
        canvas.drawText(
            statusText,
            statusRect.left + STATUS_HORIZONTAL_PADDING,
            statusRect.bottom - STATUS_VERTICAL_PADDING - 4f,
            statusPaint
        )

        labelBackgroundPaint.color = status.accentColor
        canvas.drawCircle(labelRect.left + 5f, labelRect.centerY(), 3.5f, labelBackgroundPaint)
        canvas.drawLine(rect.left + 16f, rect.top, labelRect.left + 12f, labelRect.bottom, thinCornerPaint.apply {
            color = Color.argb(150, Color.red(status.accentColor), Color.green(status.accentColor), Color.blue(status.accentColor))
            strokeWidth = 2f
        })
        thinCornerPaint.strokeWidth = 2.5f
    }

    private fun drawFaceCount(canvas: Canvas, faceCount: Int) {
        if (faceCount < 2) return

        val text = "$faceCount FACE LOCKS"
        val paddingX = 14f
        val paddingY = 8f
        val textWidth = countPaint.measureText(text)
        val rect = RectF(
            LABEL_SCREEN_PADDING,
            LABEL_SCREEN_PADDING,
            LABEL_SCREEN_PADDING + textWidth + paddingX * 2,
            LABEL_SCREEN_PADDING + countPaint.textSize + paddingY * 2
        )
        labelBackgroundPaint.color = Color.argb(150, 8, 13, 23)
        canvas.drawRoundRect(rect, 9f, 9f, labelBackgroundPaint)
        canvas.drawText(text, rect.left + paddingX, rect.bottom - paddingY - 4f, countPaint)
    }

    private fun mapRect(bounds: Rect, scale: Float, dx: Float, dy: Float): RectF {
        val left = if (mirrorHorizontally) imageWidth - bounds.right else bounds.left
        val right = if (mirrorHorizontally) imageWidth - bounds.left else bounds.right
        return RectF(
            left * scale + dx,
            bounds.top * scale + dy,
            right * scale + dx,
            bounds.bottom * scale + dy
        )
    }

    private fun mapPoint(point: PointF, scale: Float, dx: Float, dy: Float): PointF {
        val x = if (mirrorHorizontally) imageWidth - point.x else point.x
        return PointF(
            x * scale + dx,
            point.y * scale + dy
        )
    }

    private fun drawContourMesh(canvas: Canvas, mappedFace: MappedFace, scale: Float, dx: Float, dy: Float) {
        val contours = mappedFace.face.contours ?: return
        val status = mappedFace.status
        val accentColor = status.accentColor
        
        meshPaint.color = Color.argb(90, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        meshNodePaint.color = Color.argb(150, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        
        contours.forEach { points ->
            if (points.isEmpty()) return@forEach
            val path = Path()
            val firstPoint = mapPoint(points[0], scale, dx, dy)
            path.moveTo(firstPoint.x, firstPoint.y)
            canvas.drawCircle(firstPoint.x, firstPoint.y, 2f, meshNodePaint)
            
            for (i in 1 until points.size) {
                val pt = mapPoint(points[i], scale, dx, dy)
                path.lineTo(pt.x, pt.y)
                canvas.drawCircle(pt.x, pt.y, 2f, meshNodePaint)
            }
            if (points.size == 16) {
                path.close()
            }
            canvas.drawPath(path, meshPaint)
        }
    }

    private fun drawTelemetryPanel(canvas: Canvas, mappedFace: MappedFace) {
        val status = mappedFace.status
        val accentColor = status.accentColor
        val rect = mappedFace.rect
        
        val smileProb = mappedFace.face.smileProb
        val leftEyeProb = mappedFace.face.leftEyeProb
        val rightEyeProb = mappedFace.face.rightEyeProb
        val yawVal = mappedFace.face.yaw
        val pitchVal = mappedFace.face.pitch
        val rollVal = mappedFace.face.roll
        
        if (smileProb == null && leftEyeProb == null && rightEyeProb == null && yawVal == null && pitchVal == null && rollVal == null) {
            return
        }
        
        val spaceOnRight = width - rect.right
        val spaceOnLeft = rect.left
        val drawOnRight = spaceOnRight >= spaceOnLeft
        
        val panelWidth = 200f
        val panelHeight = 135f
        val panelOffset = 22f
        
        val panelLeft = if (drawOnRight) rect.right + panelOffset else rect.left - panelOffset - panelWidth
        val panelTop = rect.top
        val panelRight = panelLeft + panelWidth
        val panelBottom = panelTop + panelHeight
        val panelRect = RectF(panelLeft, panelTop, panelRight, panelBottom)
        
        val startX = if (drawOnRight) rect.right else rect.left
        val startY = rect.top + 30f
        val endX = if (drawOnRight) rect.right + panelOffset else rect.left - panelOffset
        val endY = rect.top + 30f
        
        val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        canvas.drawLine(startX, startY, endX, endY, connectorPaint)
        canvas.drawCircle(startX, startY, 4f, connectorPaint.apply { style = Paint.Style.FILL })
        
        val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 8, 13, 23)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(panelRect, 8f, 8f, hudBgPaint)
        
        val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(panelRect, 8f, 8f, hudBorderPaint)
        
        val titleBarHeight = 26f
        val hudHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.FILL
        }
        val headerRect = RectF(panelLeft, panelTop, panelRight, panelTop + titleBarHeight)
        canvas.drawRoundRect(headerRect, 8f, 8f, hudHeaderPaint)
        canvas.drawLine(panelLeft, panelTop + titleBarHeight, panelRight, panelTop + titleBarHeight, hudBorderPaint)
        
        canvas.drawText("TELEMETRY SYS", panelLeft + 10f, panelTop + 18f, hudTitlePaint)
        
        var currentY = panelTop + 44f
        val lineSpacing = 19f
        
        val yVal = yawVal ?: 0f
        val pVal = pitchVal ?: 0f
        val rVal = rollVal ?: 0f
        canvas.drawText(String.format(java.util.Locale.CHINA, "POSE: Y:%-3.0f P:%-3.0f R:%-3.0f", yVal, pVal, rVal), panelLeft + 10f, currentY, hudTextPaint)
        currentY += lineSpacing
        
        val barLeft = panelLeft + 85f
        val barRight = panelRight - 12f
        val barWidth = barRight - barLeft
        
        val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(30, 255, 255, 255)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(90, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        }
        
        val smilePercent = (smileProb ?: 0f) * 100f
        canvas.drawText(String.format(java.util.Locale.CHINA, "SMILE: %3.0f%%", smilePercent), panelLeft + 10f, currentY, hudTextPaint)
        val smileBarRight = barLeft + barWidth * (smilePercent / 100f)
        canvas.drawRect(barLeft, currentY - 9f, barRight, currentY - 2f, barBgPaint)
        canvas.drawRect(barLeft, currentY - 9f, smileBarRight, currentY - 2f, barPaint)
        currentY += lineSpacing
        
        val leftEyePercent = (leftEyeProb ?: 0f) * 100f
        canvas.drawText(String.format(java.util.Locale.CHINA, "EYE-L: %3.0f%%", leftEyePercent), panelLeft + 10f, currentY, hudTextPaint)
        val eyeLBarRight = barLeft + barWidth * (leftEyePercent / 100f)
        canvas.drawRect(barLeft, currentY - 9f, barRight, currentY - 2f, barBgPaint)
        canvas.drawRect(barLeft, currentY - 9f, eyeLBarRight, currentY - 2f, barPaint)
        currentY += lineSpacing
        
        val rightEyePercent = (rightEyeProb ?: 0f) * 100f
        canvas.drawText(String.format(java.util.Locale.CHINA, "EYE-R: %3.0f%%", rightEyePercent), panelLeft + 10f, currentY, hudTextPaint)
        val eyeRBarRight = barLeft + barWidth * (rightEyePercent / 100f)
        canvas.drawRect(barLeft, currentY - 9f, barRight, currentY - 2f, barBgPaint)
        canvas.drawRect(barLeft, currentY - 9f, eyeRBarRight, currentY - 2f, barPaint)
        currentY += lineSpacing
        
        val livenessText = if (livenessStatus == LivenessStatus.CHECKING) {
            "LIVENESS: RUNNING"
        } else if (livenessStatus == LivenessStatus.PASSED) {
            "LIVENESS: PASSED"
        } else if (livenessStatus == LivenessStatus.FAILED) {
            "LIVENESS: BLOCKED"
        } else if (status == FaceStatus.KNOWN) {
            "AUTH: VERIFIED"
        } else {
            "AUTH: SCANNING..."
        }
        
        val statusTextPaint = Paint(hudTextPaint).apply {
            color = if (livenessStatus == LivenessStatus.FAILED) {
                Color.rgb(239, 68, 68)
            } else if (livenessStatus == LivenessStatus.PASSED || status == FaceStatus.KNOWN) {
                Color.rgb(34, 197, 94)
            } else {
                accentColor
            }
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(livenessText, panelLeft + 10f, currentY, statusTextPaint)
    }

    data class FaceBox(
        val bounds: Rect,
        val label: String,
        val isKnown: Boolean,
        val contours: List<List<PointF>>? = null,
        val smileProb: Float? = null,
        val leftEyeProb: Float? = null,
        val rightEyeProb: Float? = null,
        val yaw: Float? = null,
        val pitch: Float? = null,
        val roll: Float? = null
    )

    companion object {
        private const val SCAN_DURATION_MS = 1600L
        private const val LABEL_SCREEN_PADDING = 10f
        private const val STATUS_HORIZONTAL_PADDING = 8f
        private const val STATUS_VERTICAL_PADDING = 4f
    }

    private data class MappedFace(
        val face: FaceBox,
        val rect: RectF,
        val status: FaceStatus
    )

    private enum class FaceStatus(
        val text: String,
        val accentColor: Int,
        val subduedColor: Int,
        val scanColor: Int,
        val tickCount: Int
    ) {
        KNOWN(
            "KNOWN",
            Color.rgb(20, 184, 166),
            Color.argb(160, 94, 234, 212),
            Color.rgb(94, 234, 212),
            3
        ),
        UNKNOWN(
            "UNKNOWN",
            Color.rgb(248, 113, 113),
            Color.argb(160, 252, 165, 165),
            Color.rgb(252, 165, 165),
            1
        ),
        SCANNING(
            "SCAN",
            Color.rgb(96, 165, 250),
            Color.argb(160, 147, 197, 253),
            Color.rgb(125, 211, 252),
            2
        ),
        LIVENESS(
            "LIVENESS",
            Color.rgb(245, 158, 11),
            Color.argb(160, 253, 230, 138),
            Color.rgb(253, 230, 138),
            4
        )
    }
}
