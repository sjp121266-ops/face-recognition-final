package com.example.facerecognitionfinal.cloud

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class FacePlusPlusClient(
    private val config: CloudFaceSettings.Config,
    private val timeoutMs: Int = 15_000
) : CloudFaceGateway {

    override fun testConnection(): CloudFaceClient.CloudResult.ConnectionTest {
        requireConfigured()
        val response = postForm(
            endpoint = "/facepp/v3/faceset/create",
            fields = mapOf(
                FIELD_API_KEY to config.apiKey,
                FIELD_API_SECRET to config.apiSecret,
                "outer_id" to config.faceSetOuterId,
                "force_merge" to "1"
            )
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("Face++ 连接失败", response.code, response.body))
        }
        return CloudFaceClient.CloudResult.ConnectionTest(
            subjectCount = 0,
            explanation = "Face++ 连接成功，FaceSet ${config.faceSetOuterId} 已可用于云端录入和搜索。"
        )
    }

    override fun enroll(subject: String, jpegBytes: ByteArray): CloudFaceClient.CloudResult.Enrolled {
        requireConfigured()
        ensureFaceSet()
        val faceToken = detectFaceToken(jpegBytes)
        setUserId(faceToken, subject)
        addFace(faceToken)
        return CloudFaceClient.CloudResult.Enrolled(
            subject = subject,
            explanation = "Face++ 云端人脸库已保存 $subject 的一张样本，FaceSet：${config.faceSetOuterId}。"
        )
    }

    override fun recognize(jpegBytes: ByteArray): CloudFaceClient.CloudResult.Recognized {
        requireConfigured()
        val response = postMultipart(
            endpoint = "/facepp/v3/search",
            fields = mapOf(
                FIELD_API_KEY to config.apiKey,
                FIELD_API_SECRET to config.apiSecret,
                "outer_id" to config.faceSetOuterId,
                "return_result_count" to "1"
            ),
            jpegBytes = jpegBytes
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("Face++ 搜索失败", response.code, response.body))
        }
        return parseSearchResponse(response.body, config.threshold)
    }

    private fun requireConfigured() {
        require(config.isConfigured) { "请填写 Face++ API Key、API Secret 和 FaceSet outer_id。" }
    }

    private fun ensureFaceSet() {
        testConnection()
    }

    private fun detectFaceToken(jpegBytes: ByteArray): String {
        val response = postMultipart(
            endpoint = "/facepp/v3/detect",
            fields = mapOf(
                FIELD_API_KEY to config.apiKey,
                FIELD_API_SECRET to config.apiSecret
            ),
            jpegBytes = jpegBytes
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("Face++ 检测失败", response.code, response.body))
        }
        return parseFirstFaceToken(response.body)
            ?: throw IllegalStateException("Face++ 没有返回可加入人脸库的 face_token，请重新拍照。")
    }

    private fun setUserId(faceToken: String, subject: String) {
        val response = postForm(
            endpoint = "/facepp/v3/face/setuserid",
            fields = mapOf(
                FIELD_API_KEY to config.apiKey,
                FIELD_API_SECRET to config.apiSecret,
                "face_token" to faceToken,
                "user_id" to subject
            )
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("Face++ 设置人员名称失败", response.code, response.body))
        }
    }

    private fun addFace(faceToken: String) {
        val response = postForm(
            endpoint = "/facepp/v3/faceset/addface",
            fields = mapOf(
                FIELD_API_KEY to config.apiKey,
                FIELD_API_SECRET to config.apiSecret,
                "outer_id" to config.faceSetOuterId,
                "face_tokens" to faceToken
            )
        )
        if (response.code !in 200..299) {
            throw IllegalStateException(CloudErrorMapper.httpFailure("Face++ 加入人脸库失败", response.code, response.body))
        }
    }

    private fun postForm(endpoint: String, fields: Map<String, String>): HttpResponse {
        val body = fields.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }.toByteArray()
        val connection = openConnection(endpoint, "POST")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(body) }
            readResponse(connection)
        } catch (error: Exception) {
            connection.disconnect()
            throw IllegalStateException(CloudErrorMapper.exception("Face++ 请求失败", error), error)
        }
    }

    private fun postMultipart(
        endpoint: String,
        fields: Map<String, String>,
        jpegBytes: ByteArray
    ): HttpResponse {
        val boundary = "FacePlusPlusBoundary${System.currentTimeMillis()}"
        val body = ByteArrayOutputStream()
        fields.forEach { (name, value) ->
            body.write("--$boundary\r\n".toByteArray())
            body.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            body.write(value.toByteArray())
            body.write("\r\n".toByteArray())
        }
        body.write("--$boundary\r\n".toByteArray())
        body.write("Content-Disposition: form-data; name=\"image_file\"; filename=\"face.jpg\"\r\n".toByteArray())
        body.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
        body.write(jpegBytes)
        body.write("\r\n--$boundary--\r\n".toByteArray())

        val connection = openConnection(endpoint, "POST")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            readResponse(connection)
        } catch (error: Exception) {
            connection.disconnect()
            throw IllegalStateException(CloudErrorMapper.exception("Face++ 请求失败", error), error)
        }
    }

    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        val connection = try {
            (URL("${config.baseUrl.trimEnd('/')}$endpoint").openConnection() as HttpURLConnection)
        } catch (error: Exception) {
            throw IllegalStateException(CloudErrorMapper.exception("Face++ 请求失败", error), error)
        }
        connection.requestMethod = method
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        return connection
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code, responseBody)
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    companion object {
        private const val FIELD_API_KEY = "api_key"
        private const val FIELD_API_SECRET = "api_secret"

        fun parseFirstFaceToken(json: String): String? {
            return runCatching {
                JSONObject(json)
                    .optJSONArray("faces")
                    ?.optJSONObject(0)
                    ?.optString("face_token")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        fun parseSearchResponse(
            json: String,
            threshold: Float
        ): CloudFaceClient.CloudResult.Recognized {
            val effectiveThreshold = normalizeThreshold(threshold)
            val results = runCatching {
                JSONObject(json).optJSONArray("results")
            }.getOrElse {
                throw IllegalStateException(CloudErrorMapper.invalidJson("Face++ 搜索失败"))
            }
            val best = results.bestMatchableResult() ?: results.bestResult()
            val confidence = normalizeConfidence(best?.optDouble("confidence", 0.0)?.toFloat() ?: 0f)
            val similarity = confidence / 100f
            val userId = best?.optString("user_id")?.takeIf { it.isNotBlank() }
            val faceCount = results?.length() ?: 0
            val matched = !userId.isNullOrBlank() && similarity >= effectiveThreshold
            val percent = String.format(Locale.CHINA, "%.1f%%", confidence)
            val thresholdText = String.format(Locale.CHINA, "%.1f%%", effectiveThreshold * 100f)
            val explanation = if (matched) {
                "Face++ 返回可匹配最高置信度 $percent，高于阈值 $thresholdText，判定为 $userId。"
            } else {
                val nearest = userId?.let { "，最接近 $it" }.orEmpty()
                "Face++ 返回可匹配最高置信度 $percent，低于阈值 $thresholdText$nearest，判定为未知人员。"
            }
            return CloudFaceClient.CloudResult.Recognized(
                matched = matched,
                subject = userId,
                similarity = similarity,
                faceCount = faceCount,
                explanation = explanation
            )
        }

        private fun JSONArray?.bestMatchableResult(): JSONObject? {
            return bestResult { candidate ->
                candidate.optString("user_id").isNotBlank()
            }
        }

        private fun JSONArray?.bestResult(): JSONObject? {
            return bestResult { true }
        }

        private fun JSONArray?.bestResult(predicate: (JSONObject) -> Boolean): JSONObject? {
            if (this == null) return null
            var best: JSONObject? = null
            var bestConfidence = Float.NEGATIVE_INFINITY
            for (index in 0 until length()) {
                val candidate = optJSONObject(index) ?: continue
                if (!predicate(candidate)) continue
                val confidence = normalizeConfidence(candidate.optDouble("confidence", 0.0).toFloat())
                if (confidence > bestConfidence) {
                    best = candidate
                    bestConfidence = confidence
                }
            }
            return best
        }

        private fun normalizeThreshold(threshold: Float): Float {
            return threshold.takeIf { it.isFinite() && it > 0f && it <= 1f }
                ?: CloudFaceSettings.DEFAULT_THRESHOLD
        }

        private fun normalizeConfidence(confidence: Float): Float {
            return confidence.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
        }
    }
}
