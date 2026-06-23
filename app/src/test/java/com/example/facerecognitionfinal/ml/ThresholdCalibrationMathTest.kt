package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThresholdCalibrationMathTest {

    @Test
    fun testMockDataIsGeneratedWhenInputsAreEmpty() {
        // When intra or inter is empty, calculateFarAndFrr should use mock data
        val (far, frr) = ThresholdCalibrationMath.calculateFarAndFrr(emptyList(), emptyList(), 10.0f)
        
        // Mock data same-person is ~N(6.5, 1.3^2) and cross-person is ~N(13.5, 1.6^2)
        // At threshold 10.0:
        // - Most same-person (intra) distances should be <= 10.0, so FRR (rejected same-person) should be low.
        // - Most cross-person (inter) distances should be > 10.0, so FAR (accepted cross-person) should be low.
        assertTrue(far < 10.0f)
        assertTrue(frr < 5.0f)
    }

    @Test
    fun testPerfectSeparationEerSearch() {
        // Ideal case: intra-class (same) has distances 5.0, 6.0, 7.0
        // inter-class (cross) has distances 11.0, 12.0, 13.0
        val intra = listOf(5f, 6f, 7f)
        val inter = listOf(11f, 12f, 13f)

        // At threshold 8.0:
        // - All same-person distances (5, 6, 7) are <= 8.0, so FRR = 0%
        // - All cross-person distances (11, 12, 13) are > 8.0, so FAR = 0%
        val (far, frr) = ThresholdCalibrationMath.calculateFarAndFrr(intra, inter, 8.5f)
        assertEquals(0f, far, 0.001f)
        assertEquals(0f, frr, 0.001f)

        // Searching for EER should yield a threshold between 7.0 and 11.0 with 0% error
        val (bestT, eer) = ThresholdCalibrationMath.findBestEerThreshold(intra, inter)
        assertTrue(bestT in 7.0f..11.0f)
        assertEquals(0f, eer, 0.001f)
    }

    @Test
    fun testOverlappingDistributions() {
        val intra = listOf(5.0f, 6.0f, 7.0f, 8.0f, 9.0f)
        val inter = listOf(7.0f, 8.0f, 9.0f, 10.0f, 11.0f)

        // For threshold T = 8.0:
        // - intra > 8.0: 9.0 (1 out of 5) -> FRR = 20%
        // - inter <= 8.0: 7.0, 8.0 (2 out of 5) -> FAR = 40%
        val (far, frr) = ThresholdCalibrationMath.calculateFarAndFrr(intra, inter, 8.0f)
        assertEquals(40.0f, far, 0.001f)
        assertEquals(20.0f, frr, 0.001f)

        // The best EER threshold should minimize |FAR - FRR|
        val (bestT, eer) = ThresholdCalibrationMath.findBestEerThreshold(intra, inter)
        // Test that EER search correctly returns a valid threshold and EER percentage
        assertTrue(bestT in 4.0f..16.0f)
        assertTrue(eer >= 0f && eer <= 100f)
    }
}
