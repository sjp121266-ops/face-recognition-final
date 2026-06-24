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
    fun smallFaceNowAccepted() {
        // With relaxed quality gates (minFaceRatio=0), small faces are accepted
        val result = analyzer.evaluate(
            faceWidth = 60,
            faceHeight = 60,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 0f,
            rollDegrees = 0f
        )
        assertTrue(result is FaceQualityAnalyzer.Result.Accepted)
    }

    @Test
    fun largeYawNowAccepted() {
        // With relaxed quality gates (maxYaw=360), any yaw is accepted
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 32f,
            rollDegrees = 0f
        )
        assertTrue(result is FaceQualityAnalyzer.Result.Accepted)
    }

    @Test
    fun faceAtEdgeNowAccepted() {
        // With relaxed quality gates (minEdgePadding=0), edge faces are accepted
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
        assertTrue(result is FaceQualityAnalyzer.Result.Accepted)
    }

    @Test
    fun largeRollNowAccepted() {
        // With relaxed quality gates (maxRoll=360), any roll is accepted
        val result = analyzer.evaluate(
            faceWidth = 180,
            faceHeight = 180,
            imageWidth = 640,
            imageHeight = 480,
            yawDegrees = 0f,
            rollDegrees = -30f
        )
        assertTrue(result is FaceQualityAnalyzer.Result.Accepted)
    }
}
