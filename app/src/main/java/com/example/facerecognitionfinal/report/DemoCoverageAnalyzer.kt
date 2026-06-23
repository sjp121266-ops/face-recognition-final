package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus

class DemoCoverageAnalyzer {

    fun analyze(records: List<RecognitionRecord>): Coverage {
        val items = listOf(
            CoverageItem(
                label = "识别成功",
                covered = records.any { it.status == RecognitionStatus.LOCAL_SUCCESS || it.status == RecognitionStatus.CLOUD_SUCCESS },
                suggestion = "补充一张已录入人员的拍照识别成功截图"
            ),
            CoverageItem(
                label = "未知人员",
                covered = records.any { it.status == RecognitionStatus.LOCAL_UNKNOWN || it.status == RecognitionStatus.CLOUD_UNKNOWN },
                suggestion = "用未录入人员拍照识别，截图未知人员结果"
            ),
            CoverageItem(
                label = "边界/拒识",
                covered = records.any { it.explanation.contains("边界区") || it.explanation.contains("拒识区") },
                suggestion = "准备光线较差或距离较远的样例，展示边界区或拒识区说明"
            ),
            CoverageItem(
                label = "候选人过近",
                covered = records.any { it.explanation.contains("候选人过近") },
                suggestion = "若难以复现，可在报告中说明这是算法保护分支并引用单元测试"
            ),
            CoverageItem(
                label = "视频多人识别",
                covered = records.any { it.status == RecognitionStatus.LIVE_DEMO && liveFaceCount(it) >= MIN_LIVE_FACE_COUNT },
                suggestion = "开启多人视频演示，请至少两张人脸同时入镜，并等待画面标签确认后截图"
            )
        )
        return Coverage(
            items = items,
            coveredCount = items.count { it.covered },
            totalCount = items.size
        )
    }

    data class Coverage(
        val items: List<CoverageItem>,
        val coveredCount: Int,
        val totalCount: Int
    ) {
        val isComplete: Boolean get() = coveredCount == totalCount
    }

    data class CoverageItem(
        val label: String,
        val covered: Boolean,
        val suggestion: String
    )

    companion object {
        private const val MIN_LIVE_FACE_COUNT = 2
        private val LIVE_FACE_COUNT_REGEX = Regex("检测到\\s*(\\d+)\\s*张人脸")
    }

    private fun liveFaceCount(record: RecognitionRecord): Int {
        return LIVE_FACE_COUNT_REGEX.find(record.explanation)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}
