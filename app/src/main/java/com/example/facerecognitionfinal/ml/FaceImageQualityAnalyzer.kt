package com.example.facerecognitionfinal.ml

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

class FaceImageQualityAnalyzer(
    private val minBrightness: Float = MIN_BRIGHTNESS,
    private val maxBrightness: Float = MAX_BRIGHTNESS,
    private val minSharpness: Float = MIN_SHARPNESS,
    private val minSymmetry: Float = MIN_SYMMETRY,
    private val maxYaw: Float = MAX_YAW,
    private val maxPitch: Float = MAX_PITCH,
    private val maxRoll: Float = MAX_ROLL
) {

    fun evaluate(
        bitmap: Bitmap,
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f
    ): Result {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) {
            val emptyDetails = QualityDetails(0f, 0f, 0f, yaw, pitch, roll, 0)
            return Result.Blocked("人脸图像尺寸异常，请重新拍照。", emptyDetails)
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return evaluatePixels(pixels, width, height, yaw, pitch, roll)
    }

    fun evaluatePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f
    ): Result {
        if (width <= 1 || height <= 1 || pixels.size < width * height) {
            val emptyDetails = QualityDetails(0f, 0f, 0f, yaw, pitch, roll, 0)
            return Result.Blocked("人脸图像尺寸异常，请重新拍照。", emptyDetails)
        }

        val luma = FloatArray(width * height)
        var totalLuma = 0f
        for (index in luma.indices) {
            val value = luminance(pixels[index])
            luma[index] = value
            totalLuma += value
        }
        val averageLuma = totalLuma / luma.size
        val sharpness = averageGradient(luma, width, height)
        val symmetry = computeSymmetry(pixels, width, height)

        // Calculate score
        // Base score is 100.
        var score = 100
        
        // 1. Brightness penalty (ideal luma is 120)
        val lumaDiff = abs(averageLuma - 120f)
        if (lumaDiff > 25f) {
            score -= ((lumaDiff - 25f) * 0.4f).toInt()
        }
        
        // 2. Sharpness penalty (ideal sharpness is 15+)
        if (sharpness < 15f) {
            score -= ((15f - sharpness) * 1.5f).toInt()
        }
        
        // 3. Symmetry penalty (up to 30 points)
        score -= ((1f - symmetry) * 30f).toInt()
        
        // 4. Head pose penalty
        score -= ((abs(yaw) + abs(pitch) + abs(roll)) * 0.8f).toInt()
        
        val finalScore = score.coerceIn(0, 100)
        val details = QualityDetails(averageLuma, sharpness, symmetry, yaw, pitch, roll, finalScore)

        return when {
            averageLuma < minBrightness -> Result.Blocked(
                "人脸画面偏暗，建议打开灯光或面向光源重试。", details
            )
            averageLuma > maxBrightness -> Result.Blocked(
                "人脸画面过亮，建议避开强光或降低曝光后重试。", details
            )
            sharpness < minSharpness -> Result.Blocked(
                "人脸画面模糊，建议保持手机稳定并重新拍照。", details
            )
            symmetry < minSymmetry -> Result.Blocked(
                "左右脸部光照极不均匀，请避开侧向强光源。", details
            )
            abs(yaw) > maxYaw -> Result.Blocked(
                String.format(java.util.Locale.CHINA, "请面朝正前方进行采集（左右偏角: %.1f°）", yaw), details
            )
            abs(pitch) > maxPitch -> Result.Blocked(
                String.format(java.util.Locale.CHINA, "请面朝正前方进行采集（上下偏角: %.1f°）", pitch), details
            )
            abs(roll) > maxRoll -> Result.Blocked(
                String.format(java.util.Locale.CHINA, "请保持头部端正进行采集（倾斜偏角: %.1f°）", roll), details
            )
            else -> Result.Accepted(
                summary = "人脸图像质量合规，适合录入与识别。",
                details = details
            )
        }
    }

    private fun luminance(pixel: Int): Float {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return red * 0.299f + green * 0.587f + blue * 0.114f
    }

    private fun averageGradient(luma: FloatArray, width: Int, height: Int): Float {
        var total = 0f
        var count = 0
        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                val index = y * width + x
                total += abs(luma[index] - luma[index + 1])
                total += abs(luma[index] - luma[index + width])
                count += 2
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun computeSymmetry(pixels: IntArray, width: Int, height: Int): Float {
        var leftSum = 0f
        var rightSum = 0f
        var leftCount = 0
        var rightCount = 0
        val halfWidth = width / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val yVal = 0.299f * r + 0.587f * g + 0.114f * b
                if (x < halfWidth) {
                    leftSum += yVal
                    leftCount++
                } else if (x > halfWidth) {
                    rightSum += yVal
                    rightCount++
                }
            }
        }
        val leftAvg = if (leftCount > 0) leftSum / leftCount else 0f
        val rightAvg = if (rightCount > 0) rightSum / rightCount else 0f
        val maxVal = max(leftAvg, rightAvg)
        if (maxVal == 0f) return 1f
        val diff = abs(leftAvg - rightAvg)
        return (1f - diff / maxVal).coerceIn(0f, 1f)
    }

    data class QualityDetails(
        val brightness: Float,
        val sharpness: Float,
        val symmetry: Float,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        val score: Int
    )

    sealed class Result {
        data class Accepted(
            val summary: String,
            val details: QualityDetails
        ) : Result()

        data class Blocked(
            val reason: String,
            val details: QualityDetails
        ) : Result()
    }

    companion object {
        const val MIN_BRIGHTNESS = 35f
        const val MAX_BRIGHTNESS = 235f
        const val MIN_SHARPNESS = 4.0f
        const val MIN_SYMMETRY = 0.50f
        const val MAX_YAW = 18.0f
        const val MAX_PITCH = 15.0f
        const val MAX_ROLL = 15.0f
    }
}
