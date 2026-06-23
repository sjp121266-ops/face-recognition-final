package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceEmbeddingGuardTest {

    private val guard = FaceEmbeddingGuard(expectedSize = 3, outlierThreshold = 2f)

    @Test
    fun validEmbeddingRequiresExpectedSizeAndFiniteValues() {
        assertTrue(guard.isValid(floatArrayOf(1f, 2f, 3f)))
        assertFalse(guard.isValid(floatArrayOf(1f, 2f)))
        assertFalse(guard.isValid(floatArrayOf(1f, Float.NaN, 3f)))
        assertFalse(guard.isValid(floatArrayOf(1f, Float.POSITIVE_INFINITY, 3f)))
    }

    @Test
    fun distanceUsesL2Distance() {
        val distance = guard.distance(
            first = floatArrayOf(0f, 0f, 0f),
            second = floatArrayOf(1f, 2f, 2f)
        )

        assertEquals(3f, distance, 0.001f)
        assertEquals(distance, EmbeddingDistance.l2(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 2f, 2f)), 0.001f)
    }

    @Test
    fun closeEmbeddingIsNotOutlier() {
        val profile = PersonProfile("张三", mutableListOf(floatArrayOf(0f, 0f, 0f)))

        assertFalse(guard.isOutlierForProfile(floatArrayOf(1f, 0f, 0f), profile))
    }

    @Test
    fun farEmbeddingIsOutlier() {
        val profile = PersonProfile("张三", mutableListOf(floatArrayOf(0f, 0f, 0f)))

        assertTrue(guard.isOutlierForProfile(floatArrayOf(3f, 0f, 0f), profile))
    }

    @Test
    fun invalidStoredEmbeddingsAreIgnored() {
        val profile = PersonProfile(
            "张三",
            mutableListOf(
                floatArrayOf(Float.NaN, 0f, 0f),
                floatArrayOf(0f, 0f, 0f)
            )
        )

        assertFalse(guard.isOutlierForProfile(floatArrayOf(1f, 0f, 0f), profile))
    }

    @Test
    fun profileWithNoValidStoredEmbeddingsDoesNotRejectNewSample() {
        val profile = PersonProfile("张三", mutableListOf(floatArrayOf(Float.NaN, 0f, 0f)))

        assertFalse(guard.isOutlierForProfile(floatArrayOf(3f, 0f, 0f), profile))
    }
}
