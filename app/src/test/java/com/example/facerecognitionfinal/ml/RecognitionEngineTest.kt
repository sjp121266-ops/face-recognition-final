package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEngineTest {

    private val engine = RecognitionEngine()

    @Test
    fun emptyProfileListReturnsUnknown() {
        val result = engine.recognize(vector(0f), emptyList())

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(null, result.nearestName)
        assertTrue(result.explanation.contains("本地人脸库为空"))
    }

    @Test
    fun exactEmbeddingMatchReturnsPersonName() {
        val result = engine.recognize(
            embedding = vector(0.25f),
            profiles = listOf(profile("张三", vector(0.25f)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("张三", result.name)
        assertEquals(0f, result.distance, 0.001f)
        assertEquals(100f, result.confidence, 0.001f)
    }

    @Test
    fun closestAverageDistanceWins() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(
                profile("较远人员", vector(0.7f), vector(0.8f)),
                profile("较近人员", vector(0.05f), vector(0.1f))
            )
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("较近人员", result.name)
        assertTrue(result.distance < RecognitionEngine.DEFAULT_L2_THRESHOLD)
    }

    @Test
    fun distanceAboveThresholdReturnsUnknown() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(profile("李四", vector(1f)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertTrue(result.distance > RecognitionEngine.DEFAULT_L2_THRESHOLD)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals("李四", result.nearestName)
        assertTrue(result.explanation.contains("最接近 李四"))
        assertTrue(result.explanation.contains("判定为未知人员"))
    }

    @Test
    fun distanceExactlyAtThresholdStillMatchesWhenClearWinner() {
        val thresholdEngine = RecognitionEngine(threshold = 3f)
        val result = thresholdEngine.recognize(
            embedding = FloatArray(128) { 0f },
            profiles = listOf(profile("边界人员", floatArrayOf(3f) + FloatArray(127)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("边界人员", result.name)
        assertEquals(3f, result.distance, 0.001f)
    }

    @Test
    fun distanceSlightlyAboveThresholdReturnsUnknown() {
        val thresholdEngine = RecognitionEngine(threshold = 3f)
        val result = thresholdEngine.recognize(
            embedding = FloatArray(128) { 0f },
            profiles = listOf(profile("边界人员", floatArrayOf(3.01f) + FloatArray(127)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
    }

    @Test
    fun invalidThresholdFallsBackToDefaultThreshold() {
        val thresholdEngine = RecognitionEngine(threshold = 0f)
        val result = thresholdEngine.recognize(
            embedding = FloatArray(128) { 0f },
            profiles = listOf(profile("默认阈值人员", FloatArray(128) { 0.1f }))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("默认阈值人员", result.name)
        assertTrue(result.explanation.contains("阈值 10.000"))
    }

    @Test
    fun closeTopTwoCandidatesReturnUnknownToAvoidMisrecognition() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(
                profile("候选A", vector(0.20f)),
                profile("候选B", vector(0.25f))
            )
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertEquals("候选A", result.nearestName)
        assertTrue(result.explanation.contains("候选人过近"))
        assertTrue(result.explanation.contains("拒绝确认"))
    }

    @Test
    fun clearWinnerWithRunnerUpStillReturnsMatch() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(
                profile("明显匹配", vector(0.05f)),
                profile("第二候选", vector(0.9f))
            )
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("明显匹配", result.name)
        assertTrue(result.explanation.contains("第二候选 第二候选"))
    }

    @Test
    fun matchExplanationContainsRiskLevelAndSampleDetails() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(
                profile("张三", vector(0.1f), vector(0.2f)),
                profile("李四", vector(0.8f))
            )
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertTrue(result.explanation.contains("安全区"))
        assertTrue(result.explanation.contains("最近样本距离"))
        assertTrue(result.explanation.contains("样本数 2"))
        assertTrue(result.explanation.contains("第二候选 李四"))
    }

    @Test
    fun profilesWithoutEmbeddingsReturnUnknown() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(PersonProfile("空数据"))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertEquals(null, result.nearestName)
        assertTrue(result.explanation.contains("没有可用的人脸特征"))
    }

    @Test
    fun invalidQueryEmbeddingReturnsUnknown() {
        val result = engine.recognize(
            embedding = floatArrayOf(Float.NaN, 1f),
            profiles = listOf(profile("张三", vector(0f)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertEquals(null, result.nearestName)
        assertTrue(result.explanation.contains("当前人脸特征向量异常"))
    }

    @Test
    fun wrongSizeQueryEmbeddingReturnsUnknown() {
        val result = engine.recognize(
            embedding = FloatArray(64),
            profiles = listOf(profile("张三", vector(0f)))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertTrue(result.explanation.contains("当前人脸特征向量异常"))
    }

    @Test
    fun invalidStoredEmbeddingsAreSkippedWithoutCrash() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(
                profile("坏数据", FloatArray(64), FloatArray(128) { Float.POSITIVE_INFINITY }),
                profile("有效人员", vector(0.1f))
            )
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Match)
        result as RecognitionEngine.RecognitionResult.Match
        assertEquals("有效人员", result.name)
        assertTrue(result.explanation.contains("已跳过 2 组异常特征"))
    }

    @Test
    fun onlyInvalidStoredEmbeddingsReturnUnknown() {
        val result = engine.recognize(
            embedding = vector(0f),
            profiles = listOf(profile("坏数据", FloatArray(64), FloatArray(128) { Float.NaN }))
        )

        assertTrue(result is RecognitionEngine.RecognitionResult.Unknown)
        result as RecognitionEngine.RecognitionResult.Unknown
        assertTrue(result.explanation.contains("没有可用的人脸特征"))
        assertTrue(result.explanation.contains("已跳过 2 组异常特征"))
    }

    private fun profile(name: String, vararg embeddings: FloatArray): PersonProfile {
        return PersonProfile(name, embeddings.toMutableList())
    }

    private fun vector(value: Float): FloatArray {
        return FloatArray(128) { value }
    }
}
