package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus

class EvidenceChecklistBuilder {

    fun build(records: List<RecognitionRecord>): String {
        val evidenceItems = listOf(
            EvidenceItem("S1", "设备与首次启动", true, "记录设备型号、Android 版本、安装方式和相机权限截图。"),
            EvidenceItem("S2", "A/B 录入", records.any { it.status == RecognitionStatus.LOCAL_SUCCESS }, "每人 2-3 组样本，截图人脸库状态。"),
            EvidenceItem("S3", "已录入人员识别成功", records.any { it.status == RecognitionStatus.LOCAL_SUCCESS }, "截图姓名、相似度、L2 距离和判定说明。"),
            EvidenceItem("S4", "未知人员", records.any { it.status == RecognitionStatus.LOCAL_UNKNOWN }, "使用未录入人员截图未知人员结果。"),
            EvidenceItem("S5", "异常场景", records.any { it.explanation.contains("质量不足") || it.explanation.contains("未检测到") || it.explanation.contains("多张人脸") }, "保留无人脸、多张人脸、质量不足的截图或录屏。"),
            EvidenceItem("S6", "多人视频", records.any { it.status == RecognitionStatus.LIVE_DEMO }, "两张以上人脸稳定入镜，保留全屏录屏和稳定标签截图。"),
            EvidenceItem("S7", "断网识别", false, "关闭网络后再次本地识别，截图证明最低交付不依赖云端。"),
            EvidenceItem("S8", "摘要与完整报告", true, "设置与材料页生成截图摘要，并复制完整报告素材。"),
            EvidenceItem("S9", "小屏不重叠", false, "小屏或窄屏设备截图：首页、设置页、全屏多人视频。"),
            EvidenceItem("S10", "云端对比状态", records.any { it.status.startsWith(RecognitionStatus.CLOUD_PREFIX) }, "若演示云端，记录连接测试时间、提供商、成功/失败原因；不演示则标为未启用。")
        )
        val completed = evidenceItems.count { it.covered }
        return listOf(
            "真机证据链清单",
            "自动覆盖：$completed/${evidenceItems.size}；需要真机补证据的项目不能仅凭预期描述写成已通过。",
            evidenceItems.joinToString(separator = "\n") { item ->
                val state = if (item.covered) "已有记录/可整理" else "待真机补证据"
                "- ${item.id} ${item.label}：$state。${item.instruction}"
            }
        ).joinToString(separator = "\n")
    }

    private data class EvidenceItem(
        val id: String,
        val label: String,
        val covered: Boolean,
        val instruction: String
    )
}
