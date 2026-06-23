package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class FullReportBuilderTest {

    private val builder = FullReportBuilder()

    @Test
    fun buildsReportWithSummaryFullRecordsAndUsageAdvice() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(FloatArray(128) { 0f }, FloatArray(128) { 0.1f })),
            PersonProfile("李四", mutableListOf(FloatArray(128) { 1f }))
        )
        val records = listOf(
            record("张三", RecognitionStatus.LOCAL_SUCCESS, "安全区：识别成功"),
            record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, "拒识区：距离高于阈值")
        )

        val report = builder.build(profiles, records)

        assertTrue(report.contains("人脸识别期末作业完整报告素材"))
        assertTrue(report.contains("截图摘要"))
        assertTrue(report.contains("人脸库明细"))
        assertTrue(report.contains("算法样本评测说明"))
        assertTrue(report.contains("长期算法与安全增强边界"))
        assertTrue(report.contains("真机证据链清单"))
        assertTrue(report.contains("张三：2 组特征，样本较充分"))
        assertTrue(report.contains("李四：1 组特征，建议补录到 2-3 次"))
        assertTrue(report.contains("完整识别记录"))
        assertTrue(report.contains("安全区：识别成功"))
        assertTrue(report.contains("拒识区：距离高于阈值"))
        assertTrue(report.contains("云端 API 演示材料"))
        assertTrue(report.contains("Face++"))
        assertTrue(report.contains("CompreFace"))
        assertTrue(report.contains("不使用 GPT 多模态模型"))
        assertTrue(report.contains("隐私与风险说明"))
        assertTrue(report.contains("本机私有存储"))
        assertTrue(report.contains("敏感生物特征数据"))
        assertTrue(report.contains("未实现活体检测"))
        assertTrue(report.contains("模型升级、NNAPI/GPU delegate 和活体检测"))
        assertTrue(report.contains("报告使用建议"))
        assertTrue(report.contains("真机证据链清单"))
    }

    @Test
    fun emptyProfilesAreExplainedInReport() {
        val report = builder.build(emptyList(), emptyList())

        assertTrue(report.contains("人脸库明细"))
        assertTrue(report.contains("暂无已录入人员"))
    }

    private fun record(name: String, status: String, explanation: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = name,
            distance = 1f,
            confidence = 88f,
            status = status,
            explanation = explanation
        )
    }
}
