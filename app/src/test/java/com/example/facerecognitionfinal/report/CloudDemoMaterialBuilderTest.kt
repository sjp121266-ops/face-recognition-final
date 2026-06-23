package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDemoMaterialBuilderTest {

    private val builder = CloudDemoMaterialBuilder()

    @Test
    fun buildsCloudDemoMaterialWithProviderTradeoffs() {
        val material = builder.build(
            listOf(
                record("张三", RecognitionStatus.CLOUD_ENROLLED),
                record("张三", RecognitionStatus.CLOUD_SUCCESS),
                record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.CLOUD_UNKNOWN)
            )
        )

        assertTrue(material.contains("云端 API 演示材料"))
        assertTrue(material.contains("Face++ 托管云端 API"))
        assertTrue(material.contains("CompreFace"))
        assertTrue(material.contains("不使用 GPT 多模态模型"))
        assertTrue(material.contains("录入 1 条，识别成功 1 条，未知人员 1 条"))
        assertTrue(material.contains("测试云端连接"))
        assertTrue(material.contains("本机离线识别仍是最低交付主线"))
    }

    @Test
    fun emptyCloudRecordsAreStillExplained() {
        val material = builder.build(emptyList())

        assertTrue(material.contains("当前云端记录：录入 0 条，识别成功 0 条，未知人员 0 条"))
        assertTrue(material.contains("如果现场网络、额度或服务不可用"))
    }

    private fun record(name: String, status: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = name,
            distance = Float.MAX_VALUE,
            confidence = 90f,
            status = status,
            explanation = status
        )
    }
}
