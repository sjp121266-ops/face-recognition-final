package com.example.facerecognitionfinal.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFaceClientTest {

    @Test
    fun parsesSubjectsResponse() {
        val subjects = CloudFaceClient.parseSubjectsResponse(
            """
                {
                  "subjects": ["张三", "李四"]
                }
            """.trimIndent()
        )

        assertEquals(listOf("张三", "李四"), subjects)
    }

    @Test
    fun missingSubjectsResponseIsEmpty() {
        val subjects = CloudFaceClient.parseSubjectsResponse("""{"message":"ok"}""")

        assertEquals(emptyList<String>(), subjects)
    }

    @Test
    fun parsesEnrolledSubject() {
        val subject = CloudFaceClient.parseEnrolledSubject(
            """
                {
                  "image_id": "abc",
                  "subject": "张三"
                }
            """.trimIndent()
        )

        assertEquals("张三", subject)
    }

    @Test
    fun parsesMatchedRecognitionResponse() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {
                      "subjects": [
                        {"subject": "张三", "similarity": 0.91}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertTrue(result.matched)
        assertEquals("张三", result.subject)
        assertEquals(0.91f, result.similarity, 0.001f)
        assertTrue(result.explanation.contains("高于阈值"))
    }

    @Test
    fun lowSimilarityIsUnknown() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {
                      "subjects": [
                        {"similarity": 0.52, "subject": "李四"}
                      ]
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
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {
                      "subjects": [
                        {"similarity": 0.74, "subject": "李四"}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            threshold = Float.NaN
        )

        assertFalse(result.matched)
        assertEquals("李四", result.subject)
        assertTrue(result.explanation.contains("阈值 75.0%"))
    }

    @Test
    fun nonFiniteSimilarityIsTreatedAsZero() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {
                      "subjects": [
                        {"similarity": "NaN", "subject": "李四"}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals(null, result.subject)
        assertEquals(0f, result.similarity, 0.001f)
        assertTrue(result.explanation.contains("0.0%"))
    }

    @Test
    fun emptyResultMeansNoFace() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """{"result": []}""",
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals(0, result.faceCount)
        assertTrue(result.explanation.contains("没有检测到"))
    }

    @Test
    fun resultWithoutSubjectIsUnknown() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {
                      "subjects": [
                        {"similarity": 0.88}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertFalse(result.matched)
        assertEquals(null, result.subject)
        assertEquals(1, result.faceCount)
        assertTrue(result.explanation.contains("未知人员"))
    }

    @Test
    fun choosesHighestSimilarityAcrossFaces() {
        val result = CloudFaceClient.parseRecognitionResponse(
            json = """
                {
                  "result": [
                    {"subjects": [{"subject": "张三", "similarity": 0.81}]},
                    {"subjects": [{"subject": "李四", "similarity": 0.93}]}
                  ]
                }
            """.trimIndent(),
            threshold = 0.75f
        )

        assertTrue(result.matched)
        assertEquals("李四", result.subject)
        assertEquals(0.93f, result.similarity, 0.001f)
        assertEquals(2, result.faceCount)
    }

    @Test(expected = IllegalStateException::class)
    fun malformedRecognitionJsonThrowsActionableError() {
        CloudFaceClient.parseRecognitionResponse(
            json = "{not-json",
            threshold = 0.75f
        )
    }

    @Test
    fun cloudHttpErrorsAreMappedToActionableChinese() {
        val message = CloudErrorMapper.httpFailure("云端识别失败", 401, """{"message":"bad key"}""")

        assertTrue(message.contains("API Key"))
        assertTrue(message.contains("HTTP 401"))
    }
}
