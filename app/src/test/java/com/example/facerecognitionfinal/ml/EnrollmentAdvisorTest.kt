package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentAdvisorTest {

    private val advisor = EnrollmentAdvisor()

    @Test
    fun recommendsMoreSamplesWhenBelowTarget() {
        val advice = advisor.advice(sampleCount = 1)

        assertTrue(advice.contains("建议继续"))
        assertTrue(advice.contains("3 组"))
    }

    @Test
    fun approvesWhenRecommendedSamplesReached() {
        val advice = advisor.advice(sampleCount = 3)

        assertTrue(advice.contains("已达到推荐录入数量"))
    }
}
