package com.example.facerecognitionfinal.cloud

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class CloudFaceClient(
    private val config: CloudFaceSettings.Config,
    private val timeoutMs: Int = 15_000
) : CloudFaceGateway {

    override fun enroll(subject: String, jpegBytes: ByteArray): CloudResult.Enrolled {
        requireConfigured()
        val response = postMultipart(
            endpoint = "/api/v1/recognition/faces?subject=${encode(subject)}",
            jpegBytes = jpegBytes
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("云端录入失败", response.code, response.body))
        }
        return CloudResult.Enrolled(
            subject = parseEnrolledSubject(response.body) ?: subject,
            explanation = "云端人脸库已保存 $subject 的一张样本。"
        )
    }

    override fun recognize(jpegBytes: ByteArray): CloudResult.Recognized {
        requireConfigured()
        val response = postMultipart(
            endpoint = "/api/v1/recognition/recognize?limit=1&prediction_count=1",
            jpegBytes = jpegBytes
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("云端识别失败", response.code, response.body))
        }
        return parseRecognitionResponse(response.body, config.threshold)
    }

    override fun testConnection(): CloudResult.ConnectionTest {
        requireConfigured()
        val response = get(endpoint = "/api/v1/recognition/subjects/")
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("云端连接失败", response.code, response.body))
        }
        val subjects = parseSubjectsResponse(response.body)
        return CloudResult.ConnectionTest(
            subjectCount = subjects.size,
            explanation = "云端连接成功，CompreFace 人脸库当前有 ${subjects.size} 个 subject。"
        )
    }

    private fun requireConfigured() {
        require(config.isConfigured) { "请先填写云端服务地址和 API Key。" }
    }

    private fun postMultipart(endpoint: String, jpegBytes: ByteArray): HttpResponse {
        val boundary = "FaceRecognitionBoundary${System.currentTimeMillis()}"
        val connection = try {
            (URL("${config.baseUrl.trimEnd('/')}$endpoint").openConnection() as HttpURLConnection)
        } catch (error: Exception) {
            throw IllegalStateException(CloudErrorMapper.exception("云端请求失败", error), error)
        }
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true
        connection.setRequestProperty("x-api-key", config.apiKey)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val body = ByteArrayOutputStream()
        body.write("--$boundary\r\n".toByteArray())
        body.write("Content-Disposition: form-data; name=\"file\"; filename=\"face.jpg\"\r\n".toByteArray())
        body.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
        body.write(jpegBytes)
        body.write("\r\n--$boundary--\r\n".toByteArray())

        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            readResponse(connection)
        } catch (error: Exception) {
            connection.disconnect()
            throw IllegalStateException(CloudErrorMapper.exception("云端请求失败", error), error)
        }
    }

    private fun get(endpoint: String): HttpResponse {
        val connection = try {
            (URL("${config.baseUrl.trimEnd('/')}$endpoint").openConnection() as HttpURLConnection)
        } catch (error: Exception) {
            throw IllegalStateException(CloudErrorMapper.exception("云端请求失败", error), error)
        }
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("x-api-key", config.apiKey)

        return try {
            readResponse(connection)
        } catch (error: Exception) {
            connection.disconnect()
            throw IllegalStateException(CloudErrorMapper.exception("云端请求失败", error), error)
        }
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code, responseBody)
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    sealed class CloudResult {
        data class Enrolled(
            val subject: String,
            val explanation: String
        ) : CloudResult()

        data class Recognized(
            val matched: Boolean,
            val subject: String?,
            val similarity: Float,
            val faceCount: Int,
            val explanation: String
        ) : CloudResult()

        data class ConnectionTest(
            val subjectCount: Int,
            val explanation: String
        ) : CloudResult()
    }

    companion object {
        fun parseEnrolledSubject(json: String): String? {
            return runCatching {
                JSONObject(json).optString("subject").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        fun parseSubjectsResponse(json: String): List<String> {
            return runCatching {
                JSONObject(json)
                    .optJSONArray("subjects")
                    .toStringList()
            }.getOrDefault(emptyList())
        }

        fun parseRecognitionResponse(json: String, threshold: Float): CloudResult.Recognized {
            val effectiveThreshold = normalizeThreshold(threshold)
            val results = runCatching {
                JSONObject(json).optJSONArray("result")
            }.getOrElse {
                throw IllegalStateException(CloudErrorMapper.invalidJson("云端识别失败"))
            }
            val faceCount = results?.length() ?: 0
            if (faceCount == 0) {
                return CloudResult.Recognized(
                    matched = false,
                    subject = null,
                    similarity = 0f,
                    faceCount = 0,
                    explanation = "云端没有检测到可识别的人脸。"
                )
            }

            var bestSubject: String? = null
            var bestSimilarity = 0f
            results.forEachObject { result ->
                result.optJSONArray("subjects").forEachObject { candidate ->
                    val subject = candidate.optString("subject")
                    val similarity = normalizeSimilarity(candidate.optDouble("similarity", 0.0).toFloat())
                    if (subject.isNotBlank() && similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestSubject = subject
                    }
                }
            }

            val matched = !bestSubject.isNullOrBlank() && bestSimilarity >= effectiveThreshold
            val percent = String.format(Locale.CHINA, "%.1f%%", bestSimilarity * 100f)
            val explanation = if (matched) {
                "云端 CompreFace 返回最高相似度 $percent，高于阈值 ${formatThreshold(effectiveThreshold)}，判定为 $bestSubject。"
            } else {
                val nearest = bestSubject?.let { "，最接近 $it" }.orEmpty()
                "云端 CompreFace 返回最高相似度 $percent，低于阈值 ${formatThreshold(effectiveThreshold)}$nearest，判定为未知人员。"
            }
            return CloudResult.Recognized(
                matched = matched,
                subject = bestSubject,
                similarity = bestSimilarity,
                faceCount = faceCount,
                explanation = explanation
            )
        }

        private fun formatThreshold(threshold: Float): String {
            return String.format(Locale.CHINA, "%.1f%%", threshold * 100f)
        }

        private fun normalizeThreshold(threshold: Float): Float {
            return threshold.takeIf { it.isFinite() && it > 0f && it <= 1f }
                ?: CloudFaceSettings.DEFAULT_THRESHOLD
        }

        private fun normalizeSimilarity(similarity: Float): Float {
            return similarity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optString(index).takeIf { it.isNotBlank() }
            }
        }

        private fun JSONArray?.forEachObject(action: (JSONObject) -> Unit) {
            if (this == null) return
            for (index in 0 until length()) {
                optJSONObject(index)?.let(action)
            }
        }
    }
}
