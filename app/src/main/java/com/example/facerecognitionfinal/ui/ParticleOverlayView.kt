package com.example.facerecognitionfinal.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Celebration particle / confetti overlay.
 *
 * When triggered, spawns colorful confetti particles that float down
 * with gravity and fade out over ~2 seconds. Used for recognition success.
 */
class ParticleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var color: Int,
        var size: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var alpha: Int,
        var shape: Int  // 0=rect, 1=circle, 2=triangle
    )

    private val particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trianglePath = Path()

    private val colors = intArrayOf(
        Color.parseColor("#FF6B6B"), Color.parseColor("#FFD93D"),
        Color.parseColor("#6BCB77"), Color.parseColor("#4D96FF"),
        Color.parseColor("#FF8C42"), Color.parseColor("#C084FC"),
        Color.parseColor("#F472B6"), Color.parseColor("#14A69A")
    )

    fun burst() {
        val cx = width / 2f
        val cy = height * 0.3f
        val count = 60

        particles.clear()
        for (i in 0 until count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = 400f + Random.nextFloat() * 800f
            particles.add(
                Particle(
                    x = cx + Random.nextFloat() * 60f - 30f,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed * 0.5f - 400f,
                    color = colors[Random.nextInt(colors.size)],
                    size = 8f + Random.nextFloat() * 14f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                    alpha = 255,
                    shape = Random.nextInt(3)
                )
            )
        }

        animator?.cancel()
        val startTime = System.currentTimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                val dt = 0.016f  // ~60fps
                var anyAlive = false
                for (p in particles) {
                    if (p.alpha <= 0) continue
                    p.vy += 600f * dt  // gravity
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rotation += p.rotationSpeed * dt

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                    p.alpha = (255 * (1f - elapsed / 1.8f)).toInt().coerceIn(0, 255)
                    if (p.alpha > 0) anyAlive = true
                }
                if (!anyAlive) {
                    particles.clear()
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            if (p.alpha <= 0) continue
            paint.color = p.color
            paint.alpha = p.alpha

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            when (p.shape) {
                0 -> {  // Rect
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(-p.size / 2, -p.size / 4, p.size / 2, p.size / 4, paint)
                }
                1 -> {  // Circle
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(0f, 0f, p.size / 2, paint)
                }
                2 -> {  // Triangle
                    paint.style = Paint.Style.FILL
                    trianglePath.reset()
                    trianglePath.moveTo(0f, -p.size / 2)
                    trianglePath.lineTo(-p.size / 2, p.size / 2)
                    trianglePath.lineTo(p.size / 2, p.size / 2)
                    trianglePath.close()
                    canvas.drawPath(trianglePath, paint)
                }
            }
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        particles.clear()
    }
}
