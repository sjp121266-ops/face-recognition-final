package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus

class DemoGuideBuilder {

    fun build(
        profiles: List<PersonProfile>,
        records: List<RecognitionRecord>,
        cloudMode: Boolean
    ): String {
        if (cloudMode) {
            return "下一步：先测试云端连接；连接成功后再录入和识别。"
        }

        if (profiles.isEmpty()) {
            return "下一步：输入姓名，点击“录入样本”。建议先准备 2 位测试人员。"
        }

        if (profiles.size < RECOMMENDED_PERSON_COUNT) {
            return "下一步：继续录入第 2 位测试人员，方便展示 A/B 区分。"
        }

        val weakProfile = profiles.firstOrNull { it.embeddings.size < RECOMMENDED_SAMPLE_COUNT }
        if (weakProfile != null) {
            return "下一步：给“${weakProfile.name}”补录到 2-3 次，提高现场识别稳定性。"
        }

        if (!records.hasStatus(RecognitionStatus.LOCAL_SUCCESS, RecognitionStatus.CLOUD_SUCCESS)) {
            return "下一步：点击“拍照识别”，完成一次识别成功截图。"
        }

        if (!records.hasStatus(RecognitionStatus.LOCAL_UNKNOWN, RecognitionStatus.CLOUD_UNKNOWN)) {
            return "下一步：让未录入人员出镜，完成一次“未知人员”截图。"
        }

        if (!records.hasStatus(RecognitionStatus.LIVE_DEMO)) {
            return "下一步：点击“多人视频”，完成多人检测记录。"
        }

        return "下一步：生成截图摘要，并复制完整报告素材。"
    }

    private fun List<RecognitionRecord>.hasStatus(vararg statuses: String): Boolean {
        return any { record -> statuses.any { it == record.status } }
    }

    companion object {
        private const val RECOMMENDED_PERSON_COUNT = 2
        private const val RECOMMENDED_SAMPLE_COUNT = 2
    }
}
