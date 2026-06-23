package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCoverageAnalyzerTest {

    private val analyzer = DemoCoverageAnalyzer()

    @Test
    fun emptyRecordsHaveNoCoverage() {
        val coverage = analyzer.analyze(emptyList())

        assertEquals(0, coverage.coveredCount)
        assertEquals(5, coverage.totalCount)
        assertFalse(coverage.isComplete)
        assertTrue(coverage.items.all { !it.covered })
    }

    @Test
    fun completeRecordsCoverAllDemoScenarios() {
        val coverage = analyzer.analyze(
            listOf(
                record(status = RecognitionStatus.LOCAL_SUCCESS, explanation = "安全区：识别成功"),
                record(status = RecognitionStatus.LOCAL_UNKNOWN, explanation = "拒识区：距离高于阈值"),
                record(status = RecognitionStatus.LOCAL_UNKNOWN, explanation = "候选人过近：拒绝确认"),
                record(status = RecognitionStatus.LIVE_DEMO, explanation = "已完成多人视频识别演示：检测到 2 张人脸")
            )
        )

        assertEquals(5, coverage.coveredCount)
        assertTrue(coverage.isComplete)
    }

    @Test
    fun partialCoverageKeepsSuggestions() {
        val coverage = analyzer.analyze(
            listOf(record(status = RecognitionStatus.LOCAL_SUCCESS, explanation = "安全区：识别成功"))
        )

        assertEquals(1, coverage.coveredCount)
        assertTrue(coverage.items.any { !it.covered && it.suggestion.contains("未知人员") })
    }

    @Test
    fun liveDemoRecordCoversVideoScenario() {
        val coverage = analyzer.analyze(
            listOf(record(status = RecognitionStatus.LIVE_DEMO, explanation = "已完成多人视频识别演示：检测到 2 张人脸"))
        )

        assertTrue(coverage.items.first { it.label == "视频多人识别" }.covered)
    }

    @Test
    fun singleFaceLiveRecordDoesNotCoverMultiFaceScenario() {
        val coverage = analyzer.analyze(
            listOf(record(status = RecognitionStatus.LIVE_DEMO, explanation = "已完成多人视频识别演示：检测到 1 张人脸"))
        )

        assertFalse(coverage.items.first { it.label == "视频多人识别" }.covered)
    }

    @Test
    fun cloudRecordsCoverSuccessAndUnknownScenarios() {
        val coverage = analyzer.analyze(
            listOf(
                record(status = RecognitionStatus.CLOUD_SUCCESS, explanation = "云端识别成功"),
                record(status = RecognitionStatus.CLOUD_UNKNOWN, explanation = "云端判定未知人员")
            )
        )

        assertTrue(coverage.items.first { it.label == "识别成功" }.covered)
        assertTrue(coverage.items.first { it.label == "未知人员" }.covered)
    }

    private fun record(status: String, explanation: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = if (status == RecognitionStatus.LOCAL_SUCCESS) "张三" else RecognitionStatus.UNKNOWN_PERSON,
            distance = 0f,
            confidence = 0f,
            status = status,
            explanation = explanation
        )
    }
}
