package com.example.facerecognitionfinal.workflow

import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFrameCoordinatorTest {

    @Test
    fun faceGeometryBlocksSmallFaces() {
        val coordinator = LiveFrameCoordinator()

        val result = coordinator.evaluateFaceGeometry(
            face = LiveFrameCoordinator.DetectedFace(
                bounds = bounds(100, 100, 140, 140),
                yawDegrees = 0f,
                rollDegrees = 0f
            ),
            imageWidth = 480,
            imageHeight = 640
        )

        assertTrue(result is LiveFrameCoordinator.FaceGeometryResult.Blocked)
    }

    @Test
    fun nonStabilizableResultsAreNotChanged() {
        val coordinator = LiveFrameCoordinator()
        val result = LiveFrameCoordinator.LiveFaceResult(
            line = "1. 质量不足",
            bounds = bounds(),
            label = "质量不足",
            stableKey = "质量不足",
            isKnown = false,
            isConfirmed = true,
            shouldStabilize = false
        )

        val stable = coordinator.stabilize(listOf(result)).single()

        assertEquals(result, stable)
        assertNull(stable.stabilizationDebug)
    }

    @Test
    fun stabilizableKnownResultRequiresTwoFramesBeforeConfirmed() {
        val coordinator = LiveFrameCoordinator()
        val firstFrame = knownResult()

        val firstStable = coordinator.stabilize(listOf(firstFrame)).single()
        val secondStable = coordinator.stabilize(listOf(firstFrame)).single()

        assertFalse(firstStable.isConfirmed)
        assertEquals("识别中...", firstStable.label)
        assertTrue(secondStable.isConfirmed)
        assertEquals("张三 99.0%", secondStable.label)
        assertTrue(secondStable.isKnown)
        assertTrue(secondStable.stabilizationDebug != null)
        assertEquals(2, secondStable.stabilizationDebug!!.winningVoteCount)
    }

    @Test
    fun liveDemoRecordRequiresTwoConfirmedFacesAndKnownIdentity() {
        val coordinator = LiveFrameCoordinator()
        val oneKnown = knownResult(label = "张三 99.0%", isKnown = true, isConfirmed = true)
            .copy(stabilizationDebug = debug(trackId = 1), recognitionDistance = 0.1f)
        val oneUnknown = knownResult(label = "未知人员", stableKey = "未知人员", isKnown = false, isConfirmed = true)
            .copy(stabilizationDebug = debug(trackId = 2), recognitionDistance = 11f)

        assertNull(coordinator.liveDemoRecord(1, listOf(oneKnown, oneUnknown)))

        val record = coordinator.liveDemoRecord(2, listOf(oneKnown, oneUnknown))

        assertEquals(RecognitionStatus.LIVE_DEMO_PERSON, record!!.name)
        assertTrue(record.explanation.contains("检测到 2 张人脸"))
        assertTrue(record.explanation.contains("张三 99.0%"))
        assertTrue(record.explanation.contains("调试："))
        assertTrue(record.explanation.contains("T1"))
        assertTrue(record.explanation.contains("L2 0.100"))
    }

    private fun knownResult(
        label: String = "张三 99.0%",
        stableKey: String = "张三",
        isKnown: Boolean = true,
        isConfirmed: Boolean = false
    ): LiveFrameCoordinator.LiveFaceResult {
        return LiveFrameCoordinator.LiveFaceResult(
            line = "1. 张三  相似度 99.0%  距离 0.100",
            bounds = bounds(),
            label = label,
            stableKey = stableKey,
            isKnown = isKnown,
            isConfirmed = isConfirmed,
            shouldStabilize = true,
            recognitionDistance = 0.1f,
            recognitionConfidence = 99f
        )
    }

    private fun debug(trackId: Int): com.example.facerecognitionfinal.ml.LiveRecognitionStabilizer.StabilizationDebug {
        return com.example.facerecognitionfinal.ml.LiveRecognitionStabilizer.StabilizationDebug(
            trackId = trackId,
            isNewTrack = false,
            matchType = com.example.facerecognitionfinal.ml.LiveRecognitionStabilizer.MatchType.IOU,
            iou = 0.8f,
            centerDistancePx = 10,
            voteCounts = mapOf("张三" to 2),
            winningVoteCount = 2,
            requiredStableFrames = 2,
            voteWindowSize = 5,
            observedKey = "张三",
            stableLabel = "张三 99.0%"
        )
    }

    private fun bounds(
        left: Int = 20,
        top: Int = 20,
        right: Int = 220,
        bottom: Int = 220
    ): LiveFrameCoordinator.Bounds {
        return LiveFrameCoordinator.Bounds(left, top, right, bottom)
    }
}
