package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullReportBuilder(
    private val summaryBuilder: TestSummaryBuilder = TestSummaryBuilder(),
    private val recordFormatter: RecognitionRecordFormatter = RecognitionRecordFormatter(),
    private val cloudDemoMaterialBuilder: CloudDemoMaterialBuilder = CloudDemoMaterialBuilder(),
    private val evidenceChecklistBuilder: EvidenceChecklistBuilder = EvidenceChecklistBuilder(),
    private val algorithmEvaluationBuilder: AlgorithmEvaluationBuilder = AlgorithmEvaluationBuilder(),
    private val advancedReadinessBuilder: AdvancedReadinessBuilder = AdvancedReadinessBuilder(),
    private val locale: Locale = Locale.CHINA
) {

    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)

    fun build(profiles: List<PersonProfile>, records: List<RecognitionRecord>): String {
        return listOf(
            "人脸识别期末作业完整报告素材",
            "生成时间：${timeFormatter.format(Date())}",
            "",
            summaryBuilder.build(profiles, records),
            "",
            "人脸库明细",
            formatProfileDetails(profiles),
            "",
            algorithmEvaluationBuilder.build(profiles, records),
            "",
            advancedReadinessBuilder.build(profiles, records),
            "",
            evidenceChecklistBuilder.build(records),
            "",
            "完整识别记录",
            recordFormatter.formatFull(records),
            "",
            cloudDemoMaterialBuilder.build(records),
            "",
            "隐私与风险说明",
            privacyAndRiskText(),
            "",
            "报告使用建议",
            "1. 将“截图摘要”放入测试结果小节。",
            "2. 将“技术详情”用于算法原理和验收说明。",
            "3. 将“人脸库明细”用于说明测试人员和每人录入次数。",
            "4. 将“完整识别记录”作为附录或答辩备用材料。",
            "5. 将“云端 API 演示材料”用于说明 Face++、CompreFace、本地离线和 GPT 多模态模型的取舍。",
            "6. 将“真机证据链清单”用于区分已验证、待真机补证据和云端可选项目。",
            "7. 将“隐私与风险说明”放入项目不足或伦理说明部分。"
        ).joinToString(separator = "\n")
    }

    private fun formatProfileDetails(profiles: List<PersonProfile>): String {
        if (profiles.isEmpty()) return "暂无已录入人员。"

        return profiles.joinToString(separator = "\n") { profile ->
            val sampleCount = profile.embeddings.size
            val readiness = if (sampleCount >= RECOMMENDED_SAMPLE_COUNT) {
                "样本较充分"
            } else {
                "建议补录到 2-3 次"
            }
            "- ${profile.name}：${sampleCount} 组特征，$readiness"
        }
    }

    private fun privacyAndRiskText(): String {
        return listOf(
            "- 本地演示模式下，人脸特征向量和识别记录保存在 App 本机私有存储中，识别流程不调用云 API。",
            "- 当前 APK 已关闭 Android 自动备份，降低演示数据被系统备份带走的风险；正式系统仍建议使用加密存储和更细的数据清理策略。",
            "- 云端对比模式只有在用户主动切换并填写 API 信息后才会上传裁剪后的人脸图片，用于课程演示中的云端人脸库录入和搜索。",
            "- 云端 API Key/API Secret 当前仅为课程演示临时保存在本机配置中，正式产品不应把密钥保存在客户端，应改为服务端代理或加密密钥管理。",
            "- 人脸图片和特征属于敏感生物特征数据，课程演示应征得测试人员同意，不采集无关人员，不用于商业、门禁、金融核身等高风险场景。",
            "- 当前项目未实现活体检测，无法完全防止照片翻拍攻击，因此报告中应将活体检测列为后续安全增强。",
            "- 模型升级、NNAPI/GPU delegate 和活体检测都属于正式化增强项，必须有真机 A/B 数据和阈值重标定后再启用。"
        ).joinToString(separator = "\n")
    }

    companion object {
        private const val RECOMMENDED_SAMPLE_COUNT = 2
    }
}
