package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import com.example.facerecognitionfinal.ml.FaceLibraryHealthAnalyzer

class AdvancedReadinessBuilder(
    private val healthAnalyzer: FaceLibraryHealthAnalyzer = FaceLibraryHealthAnalyzer()
) {

    fun build(profiles: List<PersonProfile>, records: List<RecognitionRecord>): String {
        val health = healthAnalyzer.analyze(profiles)
        return listOf(
            "长期算法与安全增强边界",
            "活体检测：${livenessReadiness(health, records)}",
            "模型升级：${modelUpgradeReadiness(health)}",
            "性能 delegate：${delegateReadiness(records)}",
            "正式发布：${releaseReadiness()}",
            "执行原则：任何活体、模型或 delegate 改动都必须重新记录 A/B 真机样本、阈值建议、误识/拒识次数和性能体感，不直接覆盖当前稳定主流程。"
        ).joinToString(separator = "\n")
    }

    private fun livenessReadiness(
        health: FaceLibraryHealthAnalyzer.Health,
        records: List<RecognitionRecord>
    ): String {
        val hasCoreEvidence = records.any { it.status == RecognitionStatus.LOCAL_SUCCESS } &&
            records.any { it.status == RecognitionStatus.LOCAL_UNKNOWN }
        return when {
            health.level == FaceLibraryHealthAnalyzer.Level.BLOCKED ->
                "暂不建议接入；当前人脸库仍需修复，先保证身份识别主流程稳定。"
            !hasCoreEvidence ->
                "暂不建议接入；请先补齐成功识别和未知人员证据，再评估静态/动作活体。"
            else ->
                "可作为后续加分模块调研，但当前版本未实现，不能声明可防照片翻拍。建议先做只读演示或报告说明，不压入最低交付。"
        }
    }

    private fun modelUpgradeReadiness(health: FaceLibraryHealthAnalyzer.Health): String {
        return when {
            health.thresholdAdvice == null ->
                "暂不建议换模型；当前样本不足，无法重新标定默认阈值。"
            health.unstablePeople.isNotEmpty() || health.nearestSeparation?.let {
                it.distance < FaceLibraryHealthAnalyzer.CONFUSED_PERSON_DISTANCE
            } == true ->
                "暂不建议换模型；当前人脸库已有一致性或区分度风险，应先补录稳定样本。"
            else ->
                "可进入 A/B 评测准备：保留当前 FaceNet 作为基线，新模型必须重新统计同人最大 L2、跨人最近 L2、未知人员 L2 和误识/拒识次数。"
        }
    }

    private fun delegateReadiness(records: List<RecognitionRecord>): String {
        val hasLiveDemo = records.any { it.status == RecognitionStatus.LIVE_DEMO }
        return if (hasLiveDemo) {
            "可在真机上评估 NNAPI/GPU/XNNPACK 对多人视频的帧率和发热影响；当前代码保留 XNNPACK CPU 路线作为稳定基线。"
        } else {
            "暂不建议启用新 delegate；请先完成多人视频真机录屏，再比较帧率、发热、黑屏和模型输出一致性。"
        }
    }

    private fun releaseReadiness(): String {
        return "Debug 包用于课堂演示；Release 包已关闭全局明文 HTTP 并开启压缩/混淆，但正式发布前仍需后端代理云端 Key、SHA-256 记录、隐私授权和真机 connected 测试。"
    }
}
