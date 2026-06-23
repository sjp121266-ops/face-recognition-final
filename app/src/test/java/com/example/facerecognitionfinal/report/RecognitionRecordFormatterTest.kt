package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRecordFormatterTest {

    private val formatter = RecognitionRecordFormatter()

    @Test
    fun emptyRecordsUseEmptyText() {
        assertEquals("暂无记录", formatter.format(emptyList()))
    }

    @Test
    fun formatsRecordWithStatusConfidenceAndDistance() {
        val output = formatter.format(
            listOf(
                RecognitionRecord(
                    timestamp = 0L,
                    name = "张三",
                    distance = 1.23456f,
                    confidence = 87.65f,
                    status = RecognitionStatus.LOCAL_SUCCESS,
                    explanation = "距离低于阈值"
                )
            )
        )

        assertTrue(output.contains("识别成功"))
        assertTrue(output.contains("姓名：张三"))
        assertTrue(output.contains("相似度：87.7%"))
        assertTrue(output.contains("距离：1.235"))
    }

    @Test
    fun maxDistanceIsRenderedAsDash() {
        val output = formatter.format(
            listOf(
                RecognitionRecord(
                    timestamp = 0L,
                    name = RecognitionStatus.UNKNOWN_PERSON,
                    distance = Float.MAX_VALUE,
                    confidence = 0f,
                    status = RecognitionStatus.LOCAL_UNKNOWN,
                    explanation = "无可用特征"
                )
            )
        )

        assertTrue(output.contains("距离：--"))
    }

    @Test
    fun liveDemoRecordIsRenderedAsDemoEvidence() {
        val output = formatter.format(
            listOf(
                RecognitionRecord(
                    timestamp = 0L,
                    name = RecognitionStatus.LIVE_DEMO_PERSON,
                    distance = Float.MAX_VALUE,
                    confidence = 0f,
                    status = RecognitionStatus.LIVE_DEMO,
                    explanation = "已完成多人视频识别演示：检测到 2 张人脸"
                )
            )
        )

        assertTrue(output.contains("视频演示"))
        assertTrue(output.contains("结果："))
        assertTrue(output.contains("检测到 2 张人脸"))
        assertTrue(output.contains("相似度").not())
        assertTrue(output.contains("距离").not())
    }

    @Test
    fun limitsVisibleRecordsForScreenshotReadability() {
        val records = (1..5).map {
            RecognitionRecord(
                timestamp = it.toLong(),
                name = "人员$it",
                distance = it.toFloat(),
                confidence = 80f,
                status = RecognitionStatus.LOCAL_SUCCESS,
                explanation = "第 $it 条"
            )
        }

        val output = formatter.format(records)

        assertTrue(output.contains("人员1"))
        assertTrue(output.contains("人员3"))
        assertTrue(output.contains("人员4").not())
        assertTrue(output.contains("仅显示最近 3 条，另有 2 条"))
    }

    @Test
    fun fullFormatIncludesEveryRecordAndExplanation() {
        val records = (1..5).map {
            RecognitionRecord(
                timestamp = it.toLong(),
                name = "人员$it",
                distance = it.toFloat(),
                confidence = 80f,
                status = RecognitionStatus.LOCAL_SUCCESS,
                explanation = "第 $it 条完整说明"
            )
        }

        val output = formatter.formatFull(records)

        assertTrue(output.contains("人员1"))
        assertTrue(output.contains("人员5"))
        assertTrue(output.contains("第 5 条完整说明"))
        assertTrue(output.contains("仅显示最近").not())
    }
}
