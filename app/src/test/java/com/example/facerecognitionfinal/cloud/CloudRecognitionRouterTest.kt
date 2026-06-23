package com.example.facerecognitionfinal.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudRecognitionRouterTest {

    @Test
    fun facePlusPlusProviderUsesFacePlusGateway() {
        val router = CloudRecognitionRouter(
            facePlusPlusFactory = { FakeGateway("face++") },
            comprefaceFactory = { FakeGateway("compreface") }
        )

        val result = router.testConnection(config(CloudProvider.FACE_PLUS_PLUS))

        assertEquals("face++", result.explanation)
    }

    @Test
    fun comprefaceProviderUsesCompreFaceGateway() {
        val router = CloudRecognitionRouter(
            facePlusPlusFactory = { FakeGateway("face++") },
            comprefaceFactory = { FakeGateway("compreface") }
        )

        val result = router.enroll(config(CloudProvider.COMPREFACE), "张三", byteArrayOf(1, 2, 3))

        assertEquals("compreface", result.explanation)
    }

    @Test
    fun recognizeDelegatesToSelectedGateway() {
        val router = CloudRecognitionRouter(
            facePlusPlusFactory = { FakeGateway("face++") },
            comprefaceFactory = { FakeGateway("compreface") }
        )

        val result = router.recognize(config(CloudProvider.FACE_PLUS_PLUS), byteArrayOf(1, 2, 3))

        assertEquals("face++", result.explanation)
    }

    private fun config(provider: CloudProvider): CloudFaceSettings.Config {
        return CloudFaceSettings.Config(
            provider = provider,
            baseUrl = "https://example.com",
            apiKey = "key",
            apiSecret = "secret",
            faceSetOuterId = "faces"
        )
    }

    private class FakeGateway(private val name: String) : CloudFaceGateway {
        override fun testConnection(): CloudFaceClient.CloudResult.ConnectionTest {
            return CloudFaceClient.CloudResult.ConnectionTest(subjectCount = 0, explanation = name)
        }

        override fun enroll(
            subject: String,
            jpegBytes: ByteArray
        ): CloudFaceClient.CloudResult.Enrolled {
            return CloudFaceClient.CloudResult.Enrolled(subject = subject, explanation = name)
        }

        override fun recognize(jpegBytes: ByteArray): CloudFaceClient.CloudResult.Recognized {
            return CloudFaceClient.CloudResult.Recognized(
                matched = true,
                subject = name,
                similarity = 1f,
                faceCount = 1,
                explanation = name
            )
        }
    }
}
