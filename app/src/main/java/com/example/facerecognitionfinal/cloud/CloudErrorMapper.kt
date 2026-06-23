package com.example.facerecognitionfinal.cloud

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

object CloudErrorMapper {

    fun httpFailure(action: String, code: Int, body: String): String {
        val reason = when (code) {
            401, 403 -> "API Key 或 API Secret 可能不正确，或当前账号没有访问权限。"
            404 -> "服务地址、接口路径或 FaceSet/subject 不存在，请检查 baseUrl 和云端配置。"
            408 -> "云端请求超时，请检查网络后重试。"
            429 -> "云端 QPS、额度或频率限制已触发，建议稍等后重试或切回本地模式。"
            in 500..599 -> "云端服务暂时不可用，答辩时可先切回本地离线模式。"
            else -> "云端返回异常状态，请检查服务地址、账号配置和网络。"
        }
        return "$action：HTTP $code。$reason${bodySnippet(body)}"
    }

    fun exception(action: String, error: Throwable): String {
        val reason = when (error) {
            is SocketTimeoutException -> "请求超时，请检查网络或云端服务是否可访问。"
            is UnknownHostException -> "无法解析云端服务地址，请检查网络、baseUrl 或 DNS。"
            is ConnectException -> "无法连接云端服务，请确认服务已启动、端口可访问，或直接切回本机演示模式。"
            is SSLHandshakeException -> "HTTPS 证书或协议握手失败，请检查服务地址是否应使用 http/https，以及证书配置是否正确。"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "请检查服务地址、API Key 和网络。"
        }
        return "$action：$reason"
    }

    fun invalidJson(action: String): String {
        return "$action：云端返回格式异常，请确认服务类型、接口版本和 API Key 是否匹配。"
    }

    private fun bodySnippet(body: String): String {
        val trimmed = body.trim()
        return if (trimmed.isBlank()) {
            " 返回体为空。"
        } else {
            " 返回摘要：${trimmed.take(120)}"
        }
    }
}
