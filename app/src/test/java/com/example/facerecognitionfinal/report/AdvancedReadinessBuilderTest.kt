package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedReadinessBuilderTest {

    private val builder = AdvancedReadinessBuilder()

    @Test
    fun warnsWhenCoreRecognitionEvidenceIsMissing() {
        val report = builder.build(
            profiles = listOf(profile("张三", 0f, 0.1f)),
            records = emptyList()
        )

        assertTrue(report.contains("长期算法与安全增强边界"))
        assertTrue(report.contains("暂不建议接入"))
        assertTrue(report.contains("成功识别和未知人员证据"))
        assertTrue(report.contains("暂不建议启用新 delegate"))
    }

    @Test
    fun allowsModelUpgradePreparationWhenSamplesHaveClearSeparation() {
        val report = builder.build(
            profiles = listOf(
                profile("张三", 0f, 0.1f, 0.2f),
                profile("李四", 20f, 20.1f, 20.2f)
            ),
            records = listOf(
                record("张三", RecognitionStatus.LOCAL_SUCCESS, "安全区：识别成功"),
                record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, "拒识区：距离高于阈值")
            )
        )

        assertTrue(report.contains("可作为后续加分模块调研"))
        assertTrue(report.contains("可进入 A/B 评测准备"))
        assertTrue(report.contains("重新统计同人最大 L2"))
    }

    @Test
    fun recommendsDelegateEvaluationOnlyAfterLiveDemoEvidence() {
        val report = builder.build(
            profiles = listOf(
                profile("张三", 0f, 0.1f, 0.2f),
                profile("李四", 20f, 20.1f, 20.2f)
            ),
            records = listOf(
                record("张三", RecognitionStatus.LOCAL_SUCCESS, "安全区：识别成功"),
                record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, "拒识区：距离高于阈值"),
                record("视频演示", RecognitionStatus.LIVE_DEMO, "已完成多人视频识别演示：检测到 2 张人脸")
            )
        )

        assertTrue(report.contains("可在真机上评估 NNAPI/GPU/XNNPACK"))
        assertTrue(report.contains("Release 包已关闭全局明文 HTTP"))
    }

    private fun profile(name: String, vararg values: Float): PersonProfile {
        return PersonProfile(
            name = name,
            embeddings = values.map { value -> FloatArray(128) { value } }.toMutableList()
        )
    }

    private fun record(name: String, status: String, explanation: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 1L,
            name = name,
            distance = 1f,
            confidence = 90f,
            status = status,
            explanation = explanation
        )
    }
}
