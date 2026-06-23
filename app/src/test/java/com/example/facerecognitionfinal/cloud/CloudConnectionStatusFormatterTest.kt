package com.example.facerecognitionfinal.cloud

import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConnectionStatusFormatterTest {

    private val formatter = CloudConnectionStatusFormatter { "2026-05-28 10:30" }

    @Test
    fun emptyStatusGuidesUserToTestConnection() {
        val text = formatter.format(
            status = null,
            currentProvider = CloudProvider.FACE_PLUS_PLUS
        )

        assertTrue(text.contains("尚未测试连接"))
        assertTrue(text.contains("没有内置后端"))
        assertTrue(text.contains("本地离线演示"))
    }

    @Test
    fun successStatusShowsProviderTimeAndResult() {
        val text = formatter.format(
            status = CloudFaceSettings.ConnectionStatus(
                provider = CloudProvider.FACE_PLUS_PLUS,
                success = true,
                testedAtMillis = 1000L,
                message = "FaceSet 已可用于云端录入和搜索。"
            ),
            currentProvider = CloudProvider.FACE_PLUS_PLUS
        )

        assertTrue(text.contains("连接可用"))
        assertTrue(text.contains("2026-05-28 10:30"))
        assertTrue(text.contains("Face++ 托管云端"))
        assertTrue(text.contains("没有内置后端"))
        assertTrue(text.contains("FaceSet 已可用于云端录入和搜索"))
    }

    @Test
    fun failedStatusShowsFailureReason() {
        val text = formatter.format(
            status = CloudFaceSettings.ConnectionStatus(
                provider = CloudProvider.COMPREFACE,
                success = false,
                testedAtMillis = 1000L,
                message = "HTTP 401"
            ),
            currentProvider = CloudProvider.COMPREFACE
        )

        assertTrue(text.contains("连接失败"))
        assertTrue(text.contains("HTTP 401"))
    }

    @Test
    fun providerMismatchAsksForRetest() {
        val text = formatter.format(
            status = CloudFaceSettings.ConnectionStatus(
                provider = CloudProvider.COMPREFACE,
                success = true,
                testedAtMillis = 1000L,
                message = "CompreFace 人脸库当前有 2 个 subject。"
            ),
            currentProvider = CloudProvider.FACE_PLUS_PLUS
        )

        assertTrue(text.contains("上次测试的是 CompreFace 自部署"))
        assertTrue(text.contains("建议重新测试"))
    }
}
