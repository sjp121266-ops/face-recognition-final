package com.example.facerecognitionfinal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistogramEqualizerTest {

    @Test
    fun testLowContrastExpansion() {
        // Create a low-contrast array where all elements are in a very narrow range [10, 12]
        val luma = intArrayOf(10, 10, 11, 11, 12, 12)
        val equalized = HistogramEqualizer.equalizeLuma(luma)

        // The values should be stretched across the 0-255 spectrum.
        // Minimum value (10) should map to 0.
        // Maximum value (12) should map to 255.
        assertEquals(0, equalized[0]) // 10 -> 0
        assertEquals(255, equalized[4]) // 12 -> 255
        // Middle value (11) should map to somewhere in the middle
        assertTrue(equalized[2] in 1..254)
    }

    @Test
    fun testPerfectUniformDistribution() {
        // Already perfectly distributed 0..255
        val luma = IntArray(256) { it }
        val equalized = HistogramEqualizer.equalizeLuma(luma)

        // It should remain unchanged (identity mapping)
        for (i in 0..255) {
            assertEquals(i, equalized[i])
        }
    }
}
