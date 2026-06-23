package com.example.facerecognitionfinal.workflow

import com.example.facerecognitionfinal.data.RecognitionStatus
import com.example.facerecognitionfinal.ml.FaceQualityAnalyzer
import com.example.facerecognitionfinal.ml.LiveDemoRecordPolicy
import com.example.facerecognitionfinal.ml.LiveRecognitionStabilizer

class LiveFrameCoordinator(
    private val faceQualityAnalyzer: FaceQualityAnalyzer = FaceQualityAnalyzer(),
    private val stabilizer: LiveRecognitionStabilizer = LiveRecognitionStabilizer()
) {

    fun reset() {
        stabilizer.reset()
    }

    fun evaluateFaceGeometry(face: DetectedFace, imageWidth: Int, imageHeight: Int): FaceGeometryResult {
        return when (val quality = faceQualityAnalyzer.evaluate(
            faceWidth = face.bounds.width,
            faceHeight = face.bounds.height,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            yawDegrees = face.yawDegrees,
            rollDegrees = face.rollDegrees,
            faceLeft = face.bounds.left,
            faceTop = face.bounds.top,
            faceRight = face.bounds.right,
            faceBottom = face.bounds.bottom
        )) {
            is FaceQualityAnalyzer.Result.Accepted -> FaceGeometryResult.Accepted
            is FaceQualityAnalyzer.Result.Blocked -> FaceGeometryResult.Blocked(quality.reason)
        }
    }

    fun stabilize(results: List<LiveFaceResult>): List<LiveFaceResult> {
        val stabilizableResults = results.filter { it.shouldStabilize }
        val stableObservations = stabilizer.stabilize(
            stabilizableResults.map { result ->
                LiveRecognitionStabilizer.Observation(
                    bounds = result.bounds.toStableBounds(),
                    identityKey = result.stableKey,
                    displayLabel = result.label,
                    isKnown = result.isKnown
                )
            }
        )
        val stableIterator = stableObservations.iterator()
        return results.mapIndexed { index, result ->
            if (!result.shouldStabilize) {
                return@mapIndexed result
            }
            val stable = stableIterator.next()
            val line = if (!stable.isConfirmed) {
                "${index + 1}. 识别中：正在等待跨帧投票确认，避免单帧误判。"
            } else if (stable.displayLabel == result.label) {
                result.line
            } else {
                "${index + 1}. ${stable.displayLabel}：跨帧投票保持当前稳定结果，继续观察下一帧。"
            }
            result.copy(
                line = line,
                label = stable.displayLabel,
                isKnown = stable.isKnown,
                isConfirmed = stable.isConfirmed,
                stabilizationDebug = stable.debug,
                contours = result.contours,
                smileProb = result.smileProb,
                leftEyeOpenProb = result.leftEyeOpenProb,
                rightEyeOpenProb = result.rightEyeOpenProb,
                yaw = result.yaw,
                pitch = result.pitch,
                roll = result.roll
            )
        }
    }

    fun liveDemoRecord(faceCount: Int, stableResults: List<LiveFaceResult>): LiveDemoRecord? {
        val observations = stableResults.map {
            LiveDemoRecordPolicy.Observation(
                label = it.label,
                isKnown = it.isKnown,
                isConfirmed = it.isConfirmed,
                identityKey = it.stableKey
            )
        }
        if (!LiveDemoRecordPolicy.shouldRecord(faceCount, observations)) return null
        val confirmedLabels = LiveDemoRecordPolicy.confirmedLabels(observations)
        return LiveDemoRecord(
            name = RecognitionStatus.LIVE_DEMO_PERSON,
            explanation = "已完成多人视频识别演示：检测到 ${faceCount} 张人脸，确认标签：${confirmedLabels.joinToString("、")}。结果经过跨帧稳定后写入记录，用于答辩验收覆盖。${debugSummary(stableResults)}"
        )
    }

    private fun debugSummary(stableResults: List<LiveFaceResult>): String {
        val debugLines = stableResults
            .mapNotNull { result ->
                result.stabilizationDebug?.let { debug ->
                    val distance = result.recognitionDistance?.let { " L2 ${formatDistance(it)}" }.orEmpty()
                    "${result.label}=${debug.formatCompact()}$distance"
                }
            }
        if (debugLines.isEmpty()) return ""
        return " 调试：${debugLines.joinToString("；")}。"
    }

    data class DetectedFace(
        val bounds: Bounds,
        val yawDegrees: Float,
        val rollDegrees: Float
    )

    sealed class FaceGeometryResult {
        object Accepted : FaceGeometryResult()
        data class Blocked(val reason: String) : FaceGeometryResult()
    }

    data class LiveFaceResult(
        val line: String,
        val bounds: Bounds,
        val label: String,
        val stableKey: String,
        val isKnown: Boolean,
        val isConfirmed: Boolean,
        val shouldStabilize: Boolean,
        val recognitionDistance: Float? = null,
        val recognitionConfidence: Float? = null,
        val stabilizationDebug: LiveRecognitionStabilizer.StabilizationDebug? = null,
        val contours: List<List<android.graphics.PointF>>? = null,
        val smileProb: Float? = null,
        val leftEyeOpenProb: Float? = null,
        val rightEyeOpenProb: Float? = null,
        val yaw: Float? = null,
        val pitch: Float? = null,
        val roll: Float? = null
    )

    data class LiveDemoRecord(
        val name: String,
        val explanation: String
    )

    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int = (right - left).coerceAtLeast(0)
        val height: Int = (bottom - top).coerceAtLeast(0)
    }

    private fun Bounds.toStableBounds(): LiveRecognitionStabilizer.Bounds {
        return LiveRecognitionStabilizer.Bounds(left, top, right, bottom)
    }

    private fun formatDistance(value: Float): String {
        return String.format(java.util.Locale.CHINA, "%.3f", value)
    }
}
