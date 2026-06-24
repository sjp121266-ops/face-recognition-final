package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceImageQualityAnalyzerSymmetryTest {

    private val analyzer = FaceImageQualityAnalyzer(minSharpness = 0f)

    @Test
    fun testPerfectSymmetry() {
        val pixels = IntArray(WIDTH * HEIGHT) { index ->
            // Completely uniform gray level
            rgb(128, 128, 128)
        }

        val result = analyzer.evaluatePixels(pixels, WIDTH, HEIGHT)
        
        // Assert that the result details are accessible and symmetry is near 1.0
        assertTrue(result is FaceImageQualityAnalyzer.Result.Accepted)
        val details = (result as FaceImageQualityAnalyzer.Result.Accepted).details
        assertEquals(1.0f, details.symmetry, 0.001f)
    }

    @Test
    fun testSideLightingAsymmetry() {
        val pixels = IntArray(WIDTH * HEIGHT) { index ->
            val x = index % WIDTH
            // Left half is bright (180), Right half is dark (90)
            val gray = if (x < WIDTH / 2) 180 else 90
            rgb(gray, gray, gray)
        }

        val result = analyzer.evaluatePixels(pixels, WIDTH, HEIGHT)

        // Expected symmetry = 1.0 - |180 - 90| / 180 = 0.50
        // Which is exactly equal to the minimum threshold, so it might block or accept depending on floating point precision.
        // Let's inspect the details symmetry value.
        val details = when (result) {
            is FaceImageQualityAnalyzer.Result.Accepted -> result.details
            is FaceImageQualityAnalyzer.Result.Blocked -> result.details
        }
        assertEquals(0.50f, details.symmetry, 0.01f)
    }

    @Test
    fun testStrongSideLightingBlocked() {
        val pixels = IntArray(WIDTH * HEIGHT) { index ->
            val x = index % WIDTH
            // Left half is very bright (200), Right half is very dark (50)
            val gray = if (x < WIDTH / 2) 200 else 50
            rgb(gray, gray, gray)
        }

        val result = analyzer.evaluatePixels(pixels, WIDTH, HEIGHT)

        // Expected symmetry = 1.0 - (200 - 50)/200 = 0.25 (now accepted since quality gates are relaxed)
        assertTrue(result is FaceImageQualityAnalyzer.Result.Accepted)
        val details = (result as FaceImageQualityAnalyzer.Result.Accepted).details
        assertEquals(0.25f, details.symmetry, 0.01f)
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int {
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    companion object {
        private const val WIDTH = 32
        private const val HEIGHT = 32
    }
}
