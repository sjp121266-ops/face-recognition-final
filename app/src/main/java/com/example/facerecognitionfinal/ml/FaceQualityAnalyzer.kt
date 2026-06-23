package com.example.facerecognitionfinal.ml

import kotlin.math.abs
import kotlin.math.min

class FaceQualityAnalyzer(
    private val minFaceRatio: Float = MIN_FACE_RATIO,
    private val maxYawDegrees: Float = MAX_YAW_DEGREES,
    private val maxRollDegrees: Float = MAX_ROLL_DEGREES,
    private val minEdgePaddingRatio: Float = MIN_EDGE_PADDING_RATIO
) {

    fun evaluate(
        faceWidth: Int,
        faceHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        yawDegrees: Float,
        rollDegrees: Float,
        faceLeft: Int? = null,
        faceTop: Int? = null,
        faceRight: Int? = null,
        faceBottom: Int? = null
    ): Result {
        val shorterImageSide = min(imageWidth, imageHeight).coerceAtLeast(1)
        val shorterFaceSide = min(faceWidth, faceHeight)
        val faceRatio = shorterFaceSide.toFloat() / shorterImageSide.toFloat()
        val minPadding = shorterImageSide * minEdgePaddingRatio

        return when {
            faceRatio < minFaceRatio -> Result.Blocked(
                reason = "人脸占画面比例过小，建议靠近摄像头后重试。"
            )
            isTooCloseToEdge(faceLeft, faceTop, faceRight, faceBottom, imageWidth, imageHeight, minPadding) -> Result.Blocked(
                reason = "人脸太靠近画面边缘，建议把脸放到画面中间后重试。"
            )
            abs(yawDegrees) > maxYawDegrees -> Result.Blocked(
                reason = "人脸侧转角度过大，建议正对摄像头后重试。"
            )
            abs(rollDegrees) > maxRollDegrees -> Result.Blocked(
                reason = "头部倾斜角度过大，建议摆正后重试。"
            )
            else -> Result.Accepted(
                summary = "人脸质量通过：大小和角度适合录入/识别。"
            )
        }
    }

    private fun isTooCloseToEdge(
        faceLeft: Int?,
        faceTop: Int?,
        faceRight: Int?,
        faceBottom: Int?,
        imageWidth: Int,
        imageHeight: Int,
        minPadding: Float
    ): Boolean {
        if (faceLeft == null || faceTop == null || faceRight == null || faceBottom == null) {
            return false
        }
        return faceLeft < minPadding ||
            faceTop < minPadding ||
            imageWidth - faceRight < minPadding ||
            imageHeight - faceBottom < minPadding
    }

    sealed class Result {
        data class Accepted(val summary: String) : Result()
        data class Blocked(val reason: String) : Result()
    }

    companion object {
        const val MIN_FACE_RATIO = 0.20f
        const val MAX_YAW_DEGREES = 24f
        const val MAX_ROLL_DEGREES = 24f
        const val MIN_EDGE_PADDING_RATIO = 0.03f
    }
}
