package com.example.facerecognitionfinal.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException

class FacePlusPlusClientTest {

    @Test
    fun parsesFirstFaceTokenFromDetectResponse() {
        val token = FacePlusPlusClient.parseFirstFaceToken(
            """
                {
                  "faces": [
                    {"face_token": "abc123"}
                  ]
                }
            """.trimIndent()
        )

        assertEquals("abc123", token)
    }

    @Test
    fun missingFaceTokenReturnsNull() {
        val token = FacePlusPlusClient.parseFirstFaceToken("""{"faces": []}""")

        assertEquals(null, token)
    }

    @Test
    fun parsesMatchedSearchResponse() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {
                      "user_id": "张三",
                      "confidence": 82.5,
                      "face_token": "face-token"
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertTrue(result.matched)
        assertEquals("张三", result.subject)
        assertEquals(0.825f, result.similarity, 0.001f)
        assertTrue(result.explanation.contains("高于阈值"))
    }

    @Test
    fun lowConfidenceSearchResponseIsUnknown() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {
                      "confidence": 48.0,
                      "user_id": "李四"
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals("李四", result.subject)
        assertTrue(result.explanation.contains("未知人员"))
    }

    @Test
    fun invalidThresholdFallsBackToDefaultThreshold() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {
                      "confidence": 74.0,
                      "user_id": "李四"
                    }
                  ]
                }
            """.trimIndent(),
            threshold = Float.POSITIVE_INFINITY
        )

        assertFalse(result.matched)
        assertEquals("李四", result.subject)
        assertTrue(result.explanation.contains("阈值 75.0%"))
    }

    @Test
    fun nonFiniteConfidenceIsTreatedAsZero() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {
                      "confidence": "NaN",
                      "user_id": "李四"
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals("李四", result.subject)
        assertEquals(0f, result.similarity, 0.001f)
        assertTrue(result.explanation.contains("0.0%"))
    }

    @Test
    fun emptySearchResultsAreUnknown() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """{"results": []}""",
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals(null, result.subject)
        assertEquals(0f, result.similarity, 0.001f)
        assertEquals(0, result.faceCount)
    }

    @Test
    fun searchResultWithoutUserIdIsUnknown() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {
                      "face_token": "face-token",
                      "confidence": 91.0
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals(null, result.subject)
        assertEquals(0.91f, result.similarity, 0.001f)
        assertEquals(1, result.faceCount)
    }

    @Test
    fun choosesHighestConfidenceResultWithUserIdBeforeAnonymousResult() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {"confidence": 99.0, "face_token": "anonymous"},
                    {"confidence": 86.0, "user_id": "张三"}
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertTrue(result.matched)
        assertEquals("张三", result.subject)
        assertEquals(0.86f, result.similarity, 0.001f)
        assertEquals(2, result.faceCount)
    }

    @Test
    fun choosesHighestConfidenceResult() {
        val result = FacePlusPlusClient.parseSearchResponse(
            json = """
                {
                  "results": [
                    {"user_id": "张三", "confidence": 76.0},
                    {"confidence": 88.0, "user_id": "李四"}
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertTrue(result.matched)
        assertEquals("李四", result.subject)
        assertEquals(0.88f, result.similarity, 0.001f)
        assertEquals(2, result.faceCount)
    }

    @Test(expected = IllegalStateException::class)
    fun malformedSearchJsonThrowsActionableError() {
        FacePlusPlusClient.parseSearchResponse(
            json = "{not-json",
            threshold = 0.75f
        )
    }

    @Test
    fun quotaErrorsAreMappedToActionableChinese() {
        val message = CloudErrorMapper.httpFailure("Face++ 搜索失败", 429, "")

        assertTrue(message.contains("额度"))
        assertTrue(message.contains("切回本地模式"))
    }

    @Test
    fun connectionAndSslErrorsAreMappedToActionableChinese() {
        val connection = CloudErrorMapper.exception("Face++ 请求失败", ConnectException("failed"))
        val ssl = CloudErrorMapper.exception("Face++ 请求失败", SSLHandshakeException("bad cert"))

        assertTrue(connection.contains("无法连接云端服务"))
        assertTrue(connection.contains("切回本机演示模式"))
        assertTrue(ssl.contains("证书"))
        assertTrue(ssl.contains("http/https"))
    }
}
