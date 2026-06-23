package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import com.example.facerecognitionfinal.ml.FaceLibraryHealthAnalyzer
import com.example.facerecognitionfinal.ml.RecognitionEngine
import java.util.Locale

class AlgorithmEvaluationBuilder(
    private val healthAnalyzer: FaceLibraryHealthAnalyzer = FaceLibraryHealthAnalyzer()
) {

    fun build(profiles: List<PersonProfile>, records: List<RecognitionRecord>): String {
        val health = healthAnalyzer.analyze(profiles)
        val localSuccess = records.filter { it.status == RecognitionStatus.LOCAL_SUCCESS && it.distance != Float.MAX_VALUE }
        val localUnknown = records.filter { it.status == RecognitionStatus.LOCAL_UNKNOWN && it.distance != Float.MAX_VALUE }
        val ambiguous = records.count { it.explanation.contains("候选人过近") }
        val boundary = records.count { it.explanation.contains("边界区") }
        val reject = records.count { it.explanation.contains("拒识区") }

        return listOf(
            "算法样本评测说明",
            "评测目标：用真机样本解释默认 L2 阈值 ${formatDistance(RecognitionEngine.DEFAULT_L2_THRESHOLD)} 是否适合当前人脸库，而不是临场盲调。",
            "当前样本：${profiles.size} 人 / ${profiles.sumOf { it.embeddings.size }} 组特征。",
            "同人一致性：${formatConsistency(health)}",
            "跨人区分度：${formatSeparation(health)}",
            "阈值建议：${formatThresholdAdvice(health)}",
            "本地成功记录：${localSuccess.size} 次，平均 L2 ${formatAverageDistance(localSuccess)}。",
            "本地未知记录：${localUnknown.size} 次，平均最近 L2 ${formatAverageDistance(localUnknown)}。",
            "风险控制信号：边界区 $boundary 次，拒识区 $reject 次，候选人过近拒绝确认 $ambiguous 次。",
            "真机补测建议：至少 2-3 人，每人 3-5 组；记录正脸、轻微侧脸、弱光、模糊、未知人员和多人同框场景。"
        ).joinToString(separator = "\n")
    }

    private fun formatConsistency(health: FaceLibraryHealthAnalyzer.Health): String {
        if (health.unstablePeople.isEmpty()) return "未发现明显同人样本异常。"
        return health.unstablePeople.joinToString(separator = "；") {
            "${it.name} 最大组内 L2 ${formatDistance(it.maxPairDistance)}，建议重新补录。"
        }
    }

    private fun formatSeparation(health: FaceLibraryHealthAnalyzer.Health): String {
        val separation = health.nearestSeparation ?: return "人员不足，暂无法评估跨人距离。"
        val detail = "${separation.firstName}/${separation.secondName} 最近跨人 L2 ${formatDistance(separation.distance)}"
        return if (separation.distance < FaceLibraryHealthAnalyzer.CONFUSED_PERSON_DISTANCE) {
            "$detail，存在混淆风险。"
        } else {
            "$detail，区分度正常。"
        }
    }

    private fun formatThresholdAdvice(health: FaceLibraryHealthAnalyzer.Health): String {
        val advice = health.thresholdAdvice ?: return "样本不足，完成 A/B 各 ${FaceLibraryHealthAnalyzer.RECOMMENDED_SAMPLES_PER_PERSON} 组录入后再评估。"
        return "同人最大 L2 ${formatDistance(advice.maxIntraDistance)}，跨人最近 L2 ${formatDistance(advice.nearestCrossPersonDistance)}；${advice.message}"
    }

    private fun formatAverageDistance(records: List<RecognitionRecord>): String {
        if (records.isEmpty()) return "--"
        return formatDistance(records.map { it.distance }.average())
    }

    private fun formatDistance(value: Float): String {
        return String.format(Locale.CHINA, "%.3f", value)
    }

    private fun formatDistance(value: Double): String {
        return String.format(Locale.CHINA, "%.3f", value)
    }
}
