package com.example.facerecognitionfinal.cloud

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudConnectionStatusFormatter(
    private val timeFormatter: (Long) -> String = ::defaultTimeFormatter
) {

    fun format(
        status: CloudFaceSettings.ConnectionStatus?,
        currentProvider: CloudProvider
    ): String {
        if (status == null) {
            return "云端状态：尚未测试连接\n说明：本项目没有内置后端；Face++ 是第三方托管，CompreFace 需单独部署。\n建议：先完成本地离线演示，再把云端作为加分项测试。"
        }

        val stateText = if (status.success) "连接可用" else "连接失败"
        val providerNote = if (status.provider == currentProvider) {
            "当前选择：${currentProvider.displayName}"
        } else {
            "当前选择：${currentProvider.displayName}，上次测试的是 ${status.provider.displayName}，建议重新测试。"
        }
        val message = status.message.ifBlank { "没有返回详细说明。" }
        return listOf(
            "云端状态：$stateText",
            "最近测试：${timeFormatter(status.testedAtMillis)}",
            providerNote,
            "说明：本项目没有内置后端；Face++ 是第三方托管，CompreFace 需单独部署。",
            "结果：$message"
        ).joinToString(separator = "\n")
    }

    companion object {
        private fun defaultTimeFormatter(timestamp: Long): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
        }
    }
}
