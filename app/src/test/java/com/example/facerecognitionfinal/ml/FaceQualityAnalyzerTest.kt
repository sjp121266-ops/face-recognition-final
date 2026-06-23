package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityAnalyzerTest {

    private val analyzer = FaceQualityAnalyzer()

    @Test
    fun acceptsFaceWithGoodSizeAndAngle() {
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 5f,
            rollDegrees = 4f
        )

        assertTrue(result is FaceQualityAnalyzer.Result.Accepted)
    }

    @Test
    fun blocksFaceThatIsTooSmall() {
        val result = analyzer.evaluate(
            faceWidth = 60,
            faceHeight = 60,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 0f,
            rollDegrees = 0f
        )

        assertTrue(result is FaceQualityAnalyzer.Result.Blocked)
        result as FaceQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("过小"))
    }

    @Test
    fun blocksFaceWithLargeYaw() {
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 32f,
            rollDegrees = 0f
        )

        assertTrue(result is FaceQualityAnalyzer.Result.Blocked)
        result as FaceQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("侧转"))
    }

    @Test
    fun blocksFaceTooCloseToImageEdge() {
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 0f,
            rollDegrees = 0f,
            faceLeft = 2,
            faceTop = 120,
            faceRight = 182,
            faceBottom = 300
        )

        assertTrue(result is FaceQualityAnalyzer.Result.Blocked)
        result as FaceQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("边缘"))
    }

    @Test
    fun blocksFaceWithLargeRoll() {
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 0f,
            rollDegrees = -30f
        )

        assertTrue(result is FaceQualityAnalyzer.Result.Blocked)
        result as FaceQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("倾斜"))
    }
}
