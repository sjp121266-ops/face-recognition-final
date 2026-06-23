package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSummaryBuilderTest {

    private val builder = TestSummaryBuilder()

    @Test
    fun buildsSummaryForEmptyData() {
        val summary = builder.build(emptyList(), emptyList())

        assertTrue(summary.contains("截图摘要"))
        assertTrue(summary.contains("录入：0 人 / 0 组特征"))
        assertTrue(summary.contains("离线证明：本机 TFLite FaceNet 模型"))
        assertTrue(summary.contains("不调用云 API"))
        assertTrue(summary.contains("技术详情"))
        assertTrue(summary.contains("算法样本评测说明"))
        assertTrue(summary.contains("长期算法与安全增强边界"))
        assertTrue(summary.contains("真机证据链清单"))
        assertTrue(summary.contains("平均相似度：0.0%"))
    }

    @Test
    fun countsSuccessUnknownAndEmbeddings() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f), vector(0.1f))),
            PersonProfile("李四", mutableListOf(vector(20f)))
        )
        val records = listOf(
            record("张三", 90f, RecognitionStatus.LOCAL_SUCCESS),
            record(RecognitionStatus.UNKNOWN_PERSON, 0f, RecognitionStatus.LOCAL_UNKNOWN),
            record("李四", 60f, RecognitionStatus.LOCAL_SUCCESS)
        )

        val summary = builder.build(profiles, records)

        assertTrue(summary.contains("录入：2 人 / 3 组特征"))
        assertTrue(summary.contains("演示状态：需补强"))
        assertTrue(summary.contains("样本一致性风险：未发现明显异常"))
        assertTrue(summary.contains("人员区分度风险：张三/李四 最近跨人 L2"))
        assertTrue(summary.contains("阈值校准建议：同人最大 L2"))
        assertTrue(summary.contains("识别：成功 2 次，未知人员 1 次"))
        assertTrue(summary.contains("云端 API 记录：0 条"))
        assertTrue(summary.contains("平均相似度：50.0%"))
        assertTrue(summary.contains("成功平均 L2 距离：0.000"))
    }

    @Test
    fun countsCloudRecordsInSummary() {
        val records = listOf(
            record("王五", 92f, RecognitionStatus.CLOUD_SUCCESS),
            record(RecognitionStatus.UNKNOWN_PERSON, 20f, RecognitionStatus.CLOUD_UNKNOWN)
        )

        val summary = builder.build(emptyList(), records)

        assertTrue(summary.contains("识别：成功 1 次，未知人员 1 次"))
        assertTrue(summary.contains("云端 API 记录：2 条"))
    }

    @Test
    fun countsRiskControlSignals() {
        val records = listOf(
            record("张三", 70f, RecognitionStatus.LOCAL_SUCCESS, 7f, "边界区：接近阈值"),
            record(RecognitionStatus.UNKNOWN_PERSON, 0f, RecognitionStatus.LOCAL_UNKNOWN, 11f, "拒识区：距离高于阈值"),
            record(RecognitionStatus.UNKNOWN_PERSON, 65f, RecognitionStatus.LOCAL_UNKNOWN, 4f, "候选人过近：拒绝确认")
        )

        val summary = builder.build(emptyList(), records)

        assertTrue(summary.contains("边界匹配：1 次"))
        assertTrue(summary.contains("拒识区结果：1 次"))
        assertTrue(summary.contains("候选人过近拒绝确认：1 次"))
        assertTrue(summary.contains("验收覆盖：4/5"))
        assertTrue(summary.contains("识别成功=已完成"))
        assertTrue(summary.contains("未知人员=已完成"))
    }

    @Test
    fun includesReadyLibraryHealth() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f), vector(0.05f), vector(0.1f))),
            PersonProfile("李四", mutableListOf(vector(20f), vector(20.05f), vector(20.1f)))
        )

        val summary = builder.build(profiles, emptyList())

        assertTrue(summary.contains("演示状态：可演示"))
        assertTrue(summary.contains("可以演示"))
        assertTrue(summary.contains("区分度正常"))
        assertTrue(summary.contains("建议阈值"))
    }

    @Test
    fun includesConsistencyRiskWhenSamplesAreFarApart() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(FloatArray(128) { 0f }, FloatArray(128) { 3f })),
            PersonProfile("李四", mutableListOf(vector(20f), vector(20.1f)))
        )

        val summary = builder.build(profiles, emptyList())

        assertTrue(summary.contains("样本一致性风险：张三 最大组内 L2"))
    }

    @Test
    fun includesSeparationRiskWhenPeopleAreTooClose() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f), vector(0.1f))),
            PersonProfile("李四", mutableListOf(vector(0.2f), vector(0.3f)))
        )

        val summary = builder.build(profiles, emptyList())

        assertTrue(summary.contains("人员区分度风险：张三/李四 最近跨人 L2"))
        assertTrue(summary.contains("存在混淆风险"))
    }

    @Test
    fun thresholdAdviceExplainsInsufficientSamples() {
        val summary = builder.build(emptyList(), emptyList())

        assertTrue(summary.contains("阈值校准建议：样本不足"))
        assertTrue(summary.contains("验收覆盖：0/5"))
        assertTrue(summary.contains("下一步：先修复人脸库"))
    }

    @Test
    fun completeCoverageChangesSuggestion() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f), vector(0.05f), vector(0.1f))),
            PersonProfile("李四", mutableListOf(vector(20f), vector(20.05f), vector(20.1f)))
        )
        val records = listOf(
            record("张三", 90f, RecognitionStatus.LOCAL_SUCCESS, 3f, "安全区：识别成功"),
            record(RecognitionStatus.UNKNOWN_PERSON, 0f, RecognitionStatus.LOCAL_UNKNOWN, 11f, "拒识区：距离高于阈值"),
            record(RecognitionStatus.UNKNOWN_PERSON, 30f, RecognitionStatus.LOCAL_UNKNOWN, 4f, "候选人过近：拒绝确认"),
            record(RecognitionStatus.LIVE_DEMO_PERSON, 0f, RecognitionStatus.LIVE_DEMO, Float.MAX_VALUE, "已完成多人视频识别演示：检测到 2 张人脸")
        )

        val summary = builder.build(profiles, records)

        assertTrue(summary.contains("验收覆盖：5/5"))
        assertTrue(summary.contains("核心验收场景已覆盖"))
        assertTrue(summary.contains("可在真机上评估 NNAPI/GPU/XNNPACK"))
        assertTrue(summary.contains("S6 多人视频：已有记录/可整理"))
    }

    @Test
    fun averageConfidenceExcludesEnrollmentAndLiveDemoRecords() {
        val records = listOf(
            record("张三", 80f, RecognitionStatus.LOCAL_SUCCESS),
            record(RecognitionStatus.UNKNOWN_PERSON, 20f, RecognitionStatus.LOCAL_UNKNOWN),
            record("云端录入", 100f, RecognitionStatus.CLOUD_ENROLLED),
            record(RecognitionStatus.LIVE_DEMO_PERSON, 0f, RecognitionStatus.LIVE_DEMO)
        )

        val summary = builder.build(emptyList(), records)

        assertTrue(summary.contains("平均相似度：50.0%"))
    }

    @Test
    fun nextSuggestionPrioritizesLibrarySamplesBeforeCoverage() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f))),
            PersonProfile("李四", mutableListOf(vector(20f), vector(20.1f)))
        )
        val records = listOf(record("张三", 90f, RecognitionStatus.LOCAL_SUCCESS, 3f, "安全区：识别成功"))

        val summary = builder.build(profiles, records)

        assertTrue(summary.contains("下一步：先补强录入样本"))
        assertTrue(summary.contains("张三"))
    }

    private fun record(
        name: String,
        confidence: Float,
        status: String,
        distance: Float = 0f,
        explanation: String = ""
    ): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = name,
            distance = distance,
            confidence = confidence,
            status = status,
            explanation = explanation
        )
    }

    private fun vector(value: Float): FloatArray {
        return FloatArray(128) { value }
    }
}
