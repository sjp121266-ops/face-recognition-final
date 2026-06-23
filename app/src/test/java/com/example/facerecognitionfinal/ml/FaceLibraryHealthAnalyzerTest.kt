package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceLibraryHealthAnalyzerTest {

    private val analyzer = FaceLibraryHealthAnalyzer()

    @Test
    fun emptyLibraryIsBlocked() {
        val health = analyzer.analyze(emptyList())

        assertEquals(FaceLibraryHealthAnalyzer.Level.BLOCKED, health.level)
        assertTrue(health.message.contains("还没有录入人员"))
    }

    @Test
    fun onePersonWithFewSamplesWarns() {
        val health = analyzer.analyze(
            listOf(PersonProfile("张三", mutableListOf(FloatArray(128))))
        )

        assertEquals(FaceLibraryHealthAnalyzer.Level.WARNING, health.level)
        assertTrue(health.message.contains("至少录入 2 位"))
        assertTrue(health.message.contains("张三 样本偏少"))
    }

    @Test
    fun twoPeopleWithEnoughSamplesAreReady() {
        val health = analyzer.analyze(
            listOf(
                PersonProfile("张三", mutableListOf(vector(0f), vector(0.05f), vector(0.1f))),
                PersonProfile("李四", mutableListOf(vector(1f), vector(1.05f), vector(1.1f)))
            )
        )

        assertEquals(FaceLibraryHealthAnalyzer.Level.READY, health.level)
        assertTrue(health.message.contains("可以演示"))
        assertTrue(health.thresholdAdvice!!.message.contains("建议阈值"))
    }

    @Test
    fun twoSamplesStillWarnsBecauseThreeAreRecommended() {
        val health = analyzer.analyze(
            listOf(
                PersonProfile("张三", mutableListOf(vector(0f), vector(0.1f))),
                PersonProfile("李四", mutableListOf(vector(1f), vector(1.1f)))
            )
        )

        assertEquals(FaceLibraryHealthAnalyzer.Level.WARNING, health.level)
        assertTrue(health.message.contains("每人至少 3 组"))
    }

    @Test
    fun invalidEmbeddingBlocksAndCanBeSanitized() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(FloatArray(128), FloatArray(64), nonFiniteVector())),
            PersonProfile("坏数据", mutableListOf(FloatArray(32)))
        )

        val health = analyzer.analyze(profiles)
        val sanitized = analyzer.sanitize(profiles)

        assertEquals(FaceLibraryHealthAnalyzer.Level.BLOCKED, health.level)
        assertTrue(health.message.contains("异常特征向量"))
        assertEquals(1, sanitized.profiles.size)
        assertEquals("张三", sanitized.profiles.single().name)
        assertEquals(3, sanitized.removedEmbeddings)
        assertEquals(1, sanitized.removedPeople)
    }

    @Test
    fun unstableSamplesWarnWithMaxPairDistance() {
        val health = analyzer.analyze(
            listOf(
                PersonProfile("张三", mutableListOf(vector(0f), vector(3f))),
                PersonProfile("李四", mutableListOf(vector(20f), vector(20.1f)))
            )
        )

        assertEquals(FaceLibraryHealthAnalyzer.Level.WARNING, health.level)
        assertEquals("张三", health.unstablePeople.single().name)
        assertTrue(health.message.contains("样本差异偏大"))
        assertTrue(health.message.contains("最大 L2"))
    }

    @Test
    fun closeDifferentPeopleWarnWithNearestSeparation() {
        val health = analyzer.analyze(
            listOf(
                PersonProfile("张三", mutableListOf(vector(0f), vector(0.1f))),
                PersonProfile("李四", mutableListOf(vector(0.2f), vector(0.3f)))
            )
        )

        assertEquals(FaceLibraryHealthAnalyzer.Level.WARNING, health.level)
        val separation = health.nearestSeparation!!
        assertEquals("张三", separation.firstName)
        assertEquals("李四", separation.secondName)
        assertTrue(health.message.contains("区分度偏低"))
        assertTrue(health.message.contains("最近跨人 L2"))
    }

    @Test
    fun thresholdAdviceRejectsWhenIntraDistanceOverlapsCrossPersonDistance() {
        val health = analyzer.analyze(
            listOf(
                PersonProfile("张三", mutableListOf(vector(0f), vector(3f))),
                PersonProfile("李四", mutableListOf(vector(1.5f), vector(1.6f)))
            )
        )

        val advice = health.thresholdAdvice!!
        assertEquals(null, advice.suggestedThreshold)
        assertTrue(advice.message.contains("不建议调高阈值"))
        assertTrue(advice.message.contains("重新补录"))
    }

    private fun vector(value: Float): FloatArray {
        return FloatArray(128) { value }
    }

    private fun nonFiniteVector(): FloatArray {
        return FloatArray(128) { index -> if (index == 0) Float.NaN else 0f }
    }
}
