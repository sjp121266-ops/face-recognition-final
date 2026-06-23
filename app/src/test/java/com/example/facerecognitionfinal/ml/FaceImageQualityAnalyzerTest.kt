package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertTrue
import org.junit.Test

class FaceImageQualityAnalyzerTest {

    private val analyzer = FaceImageQualityAnalyzer()

    @Test
    fun blocksDarkFaceImage() {
        val result = analyzer.evaluatePixels(
            pixels = solidPixels(gray = 20),
            width = WIDTH,
            height = HEIGHT
        )

        assertTrue(result is FaceImageQualityAnalyzer.Result.Blocked)
        result as FaceImageQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("偏暗"))
    }

    @Test
    fun blocksOverExposedFaceImage() {
        val result = analyzer.evaluatePixels(
            pixels = solidPixels(gray = 245),
            width = WIDTH,
            height = HEIGHT
        )

        assertTrue(result is FaceImageQualityAnalyzer.Result.Blocked)
        result as FaceImageQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("过亮"))
    }

    @Test
    fun blocksLowSharpnessFaceImage() {
        val result = analyzer.evaluatePixels(
            pixels = solidPixels(gray = 128),
            width = WIDTH,
            height = HEIGHT
        )

        assertTrue(result is FaceImageQualityAnalyzer.Result.Blocked)
        result as FaceImageQualityAnalyzer.Result.Blocked
        assertTrue(result.reason.contains("模糊"))
    }

    @Test
    fun acceptsBalancedSharpFaceImage() {
        val result = analyzer.evaluatePixels(
            pixels = checkerPixels(),
            width = WIDTH,
            height = HEIGHT
        )

        assertTrue(result is FaceImageQualityAnalyzer.Result.Accepted)
    }

    private fun solidPixels(gray: Int): IntArray {
        val pixel = rgb(gray, gray, gray)
        return IntArray(WIDTH * HEIGHT) { pixel }
    }

    private fun checkerPixels(): IntArray {
        return IntArray(WIDTH * HEIGHT) { index ->
            val x = index % WIDTH
            val y = index / WIDTH
            val gray = if ((x / 4 + y / 4) % 2 == 0) 80 else 180
            rgb(gray, gray, gray)
        }
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int {
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    companion object {
        private const val WIDTH = 32
        private const val HEIGHT = 32
    }
}
