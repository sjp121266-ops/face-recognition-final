package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import com.example.facerecognitionfinal.ml.FaceLibraryHealthAnalyzer
import com.example.facerecognitionfinal.ml.RecognitionEngine
import java.util.Locale

class TestSummaryBuilder {

    private val healthAnalyzer = FaceLibraryHealthAnalyzer()
    private val demoCoverageAnalyzer = DemoCoverageAnalyzer()
    private val evidenceChecklistBuilder = EvidenceChecklistBuilder()
    private val algorithmEvaluationBuilder = AlgorithmEvaluationBuilder()
    private val advancedReadinessBuilder = AdvancedReadinessBuilder()

    fun build(profiles: List<PersonProfile>, records: List<RecognitionRecord>): String {
        val health = healthAnalyzer.analyze(profiles)
        val coverage = demoCoverageAnalyzer.analyze(records)
        val successCount = records.count { it.status == RecognitionStatus.LOCAL_SUCCESS || it.status == RecognitionStatus.CLOUD_SUCCESS }
        val unknownCount = records.count { it.status == RecognitionStatus.LOCAL_UNKNOWN || it.status == RecognitionStatus.CLOUD_UNKNOWN }
        val cloudCount = records.count { it.status.startsWith(RecognitionStatus.CLOUD_PREFIX) }
        val boundaryCount = records.count { it.explanation.contains("边界区") }
        val rejectCount = records.count { it.explanation.contains("拒识区") }
        val ambiguousCount = records.count { it.explanation.contains("候选人过近") }
        val totalEmbeddings = profiles.sumOf { it.embeddings.size }
        val recognitionRecords = records.filter { it.isRecognitionResult() }
        val averageConfidence = recognitionRecords
            .takeIf { it.isNotEmpty() }
            ?.map { it.confidence }
            ?.average()
            ?: 0.0
        val successAverageDistance = records
            .filter { it.status == RecognitionStatus.LOCAL_SUCCESS && it.distance != Float.MAX_VALUE }
            .takeIf { it.isNotEmpty() }
            ?.map { it.distance }
            ?.average()
            ?: 0.0

        val screenshotSummary = listOf(
            "截图摘要",
            "录入：${profiles.size} 人 / ${totalEmbeddings} 组特征",
            "识别：成功 ${successCount} 次，未知人员 ${unknownCount} 次",
            "演示状态：${health.level.label} - ${health.message}",
            "验收覆盖：${formatCoverage(coverage)}",
            "下一步：${nextSuggestion(health, coverage)}"
        )
        val technicalDetail = listOf(
            "",
            "技术详情",
            "离线证明：本机 TFLite FaceNet 模型 + 本机 JSON 人脸库；识别流程不调用云 API，关闭网络也可演示。",
            "云端 API 记录：${cloudCount} 条",
            "平均相似度：${formatPercent(averageConfidence)}",
            "成功平均 L2 距离：${formatDistance(successAverageDistance)}",
            "当前 L2 阈值：${formatDistance(RecognitionEngine.DEFAULT_L2_THRESHOLD)}",
            "样本一致性风险：${formatConsistencyRisk(health)}",
            "人员区分度风险：${formatSeparationRisk(health)}",
            "阈值校准建议：${formatThresholdAdvice(health)}",
            "边界匹配：${boundaryCount} 次",
            "拒识区结果：${rejectCount} 次",
            "候选人过近拒绝确认：${ambiguousCount} 次",
            "",
            algorithmEvaluationBuilder.build(profiles, records),
            "",
            advancedReadinessBuilder.build(profiles, records),
            "",
            evidenceChecklistBuilder.build(records)
        )
        return (screenshotSummary + technicalDetail).joinToString(separator = "\n")
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.CHINA, "%.1f%%", value)
    }

    private fun formatDistance(value: Float): String {
        return String.format(Locale.CHINA, "%.3f", value)
    }

    private fun formatDistance(value: Double): String {
        return String.format(Locale.CHINA, "%.3f", value)
    }

    private fun formatConsistencyRisk(health: FaceLibraryHealthAnalyzer.Health): String {
        if (health.unstablePeople.isEmpty()) return "未发现明显异常"
        return health.unstablePeople.joinToString(separator = "；") {
            "${it.name} 最大组内 L2 ${formatDistance(it.maxPairDistance)}"
        }
    }

    private fun formatSeparationRisk(health: FaceLibraryHealthAnalyzer.Health): String {
        val separation = health.nearestSeparation ?: return "人员不足，暂无法评估"
        val detail = "${separation.firstName}/${separation.secondName} 最近跨人 L2 ${formatDistance(separation.distance)}"
        return if (separation.distance < FaceLibraryHealthAnalyzer.CONFUSED_PERSON_DISTANCE) {
            "$detail，存在混淆风险"
        } else {
            "$detail，区分度正常"
        }
    }

    private fun formatThresholdAdvice(health: FaceLibraryHealthAnalyzer.Health): String {
        val advice = health.thresholdAdvice ?: return "样本不足，完成 A/B 各 ${FaceLibraryHealthAnalyzer.RECOMMENDED_SAMPLES_PER_PERSON} 组录入后再评估"
        return "同人最大 L2 ${formatDistance(advice.maxIntraDistance)}，跨人最近 L2 ${formatDistance(advice.nearestCrossPersonDistance)}；${advice.message}"
    }

    private fun RecognitionRecord.isRecognitionResult(): Boolean {
        return status == RecognitionStatus.LOCAL_SUCCESS ||
            status == RecognitionStatus.LOCAL_UNKNOWN ||
            status == RecognitionStatus.CLOUD_SUCCESS ||
            status == RecognitionStatus.CLOUD_UNKNOWN
    }

    private fun formatCoverage(coverage: DemoCoverageAnalyzer.Coverage): String {
        val detail = coverage.items.joinToString(separator = "、") {
            "${it.label}=${if (it.covered) "已完成" else "待补充"}"
        }
        return "${coverage.coveredCount}/${coverage.totalCount}，$detail"
    }

    private fun nextSuggestion(
        health: FaceLibraryHealthAnalyzer.Health,
        coverage: DemoCoverageAnalyzer.Coverage
    ): String {
        if (health.level == FaceLibraryHealthAnalyzer.Level.BLOCKED) {
            return "先修复人脸库：${health.message}。"
        }
        if (health.peopleNeedSamples.isNotEmpty()) {
            return "先补强录入样本：${health.peopleNeedSamples.joinToString("、")} 每人至少补到 ${FaceLibraryHealthAnalyzer.RECOMMENDED_SAMPLES_PER_PERSON} 组，再进行验收截图。"
        }
        if (health.unstablePeople.isNotEmpty()) {
            return "先处理不稳定样本：${health.unstablePeople.joinToString("、") { it.name }} 建议重新录入光线稳定的正脸照片。"
        }
        if (health.nearestSeparation != null &&
            health.nearestSeparation.distance < FaceLibraryHealthAnalyzer.CONFUSED_PERSON_DISTANCE
        ) {
            return "先处理人员区分度风险：${health.nearestSeparation.firstName} 与 ${health.nearestSeparation.secondName} 太接近，建议补录正脸样本。"
        }
        if (coverage.isComplete) {
            return "核心验收场景已覆盖，可补充断网识别截图。"
        }
        return coverage.items
            .firstOrNull { !it.covered }
            ?.suggestion
            ?: "每人录入 2-3 次，演示拍照识别、视频多人识别、未知人员和候选人过近等场景。"
    }

    private val FaceLibraryHealthAnalyzer.Level.label: String
        get() = when (this) {
            FaceLibraryHealthAnalyzer.Level.READY -> "可演示"
            FaceLibraryHealthAnalyzer.Level.WARNING -> "需补强"
            FaceLibraryHealthAnalyzer.Level.BLOCKED -> "需修复"
        }
}
