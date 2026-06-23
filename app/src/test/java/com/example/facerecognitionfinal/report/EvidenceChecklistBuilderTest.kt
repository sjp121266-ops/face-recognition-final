package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceChecklistBuilderTest {

    private val builder = EvidenceChecklistBuilder()

    @Test
    fun buildsChecklistForEmptyRecords() {
        val checklist = builder.build(emptyList())

        assertTrue(checklist.contains("真机证据链清单"))
        assertTrue(checklist.contains("自动覆盖：2/10"))
        assertTrue(checklist.contains("S7 断网识别：待真机补证据"))
        assertTrue(checklist.contains("不能仅凭预期描述写成已通过"))
    }

    @Test
    fun marksRecognitionAndLiveEvidenceFromRecords() {
        val checklist = builder.build(
            listOf(
                record("张三", RecognitionStatus.LOCAL_SUCCESS, "安全区：识别成功"),
                record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, "拒识区：距离高于阈值"),
                record(RecognitionStatus.LIVE_DEMO_PERSON, RecognitionStatus.LIVE_DEMO, "已完成多人视频识别演示：检测到 2 张人脸")
            )
        )

        assertTrue(checklist.contains("S3 已录入人员识别成功：已有记录/可整理"))
        assertTrue(checklist.contains("S4 未知人员：已有记录/可整理"))
        assertTrue(checklist.contains("S6 多人视频：已有记录/可整理"))
    }

    private fun record(name: String, status: String, explanation: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = name,
            distance = 1f,
            confidence = 80f,
            status = status,
            explanation = explanation
        )
    }
}
