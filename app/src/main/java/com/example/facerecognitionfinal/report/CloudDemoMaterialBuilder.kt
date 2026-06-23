package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus

class CloudDemoMaterialBuilder {

    fun build(records: List<RecognitionRecord>): String {
        val cloudRecords = records.filter { it.status.startsWith(RecognitionStatus.CLOUD_PREFIX) }
        val successCount = cloudRecords.count { it.status == RecognitionStatus.CLOUD_SUCCESS }
        val unknownCount = cloudRecords.count { it.status == RecognitionStatus.CLOUD_UNKNOWN }
        val enrollCount = cloudRecords.count { it.status == RecognitionStatus.CLOUD_ENROLLED }

        return listOf(
            "云端 API 演示材料",
            "定位：云端模式是第二演示模式，本机离线识别仍是最低交付主线。",
            "推荐选择：优先使用 Face++ 托管云端 API；CompreFace 作为开源自部署备选。",
            "不使用 GPT 多模态模型做人脸身份识别：通用模型适合图片理解和说明，不适合承担“这个人是谁”的人脸库比对。",
            "当前云端记录：录入 ${enrollCount} 条，识别成功 ${successCount} 条，未知人员 ${unknownCount} 条。",
            "",
            "演示步骤模板",
            "1. 切换到“云端对比”模式。",
            "2. 选择 Face++ 托管云端；若展示开源自部署架构，再选择 CompreFace。",
            "3. 填写 API Key、API Secret 和 FaceSet outer_id，或填写 CompreFace 服务地址和 Recognition API Key。",
            "4. 先点击“测试云端连接”，截图保留最近测试时间、提供商和成功/失败原因。",
            "5. 连接成功后再拍照录入和拍照识别，说明 App 仍负责拍照、单脸检测和质量过滤，云端负责人脸库搜索。",
            "",
            "取舍说明",
            "- Face++：不需要本地部署，适合低成本课程演示；免费额度和 QPS 以官方控制台为准。",
            "- CompreFace：软件本身免费，适合说明开源服务化架构；但需要 Docker、多个服务组件和一定机器资源。",
            "- 本地离线：不依赖网络和账号，隐私更可控，是现场答辩的兜底主流程。",
            "",
            "风险说明",
            "- 云端 API Key 不应硬编码到 App，课程演示中只临时填写。",
            "- 云端模式会上传裁剪后的人脸图片，应征得测试人员同意。",
            "- 如果现场网络、额度或服务不可用，应切回本机演示模式完成核心验收。"
        ).joinToString(separator = "\n")
    }
}
