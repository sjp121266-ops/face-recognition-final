package com.example.facerecognitionfinal.workflow

import com.example.facerecognitionfinal.cloud.CloudFaceSettings
import com.example.facerecognitionfinal.cloud.CloudRecognitionRouter

class CloudRecognitionCoordinator(
    private val router: CloudRecognitionRouter = CloudRecognitionRouter()
) {

    suspend fun enroll(
        config: CloudFaceSettings.Config,
        name: String,
        jpegBytes: ByteArray
    ): EnrollmentResult {
        val result = router.enroll(config, name, jpegBytes)
        return EnrollmentResult(
            subject = result.subject,
            explanation = result.explanation
        )
    }

    suspend fun recognize(
        config: CloudFaceSettings.Config,
        jpegBytes: ByteArray
    ): RecognitionResult {
        val result = router.recognize(config, jpegBytes)
        return RecognitionResult(
            matched = result.matched,
            subject = result.subject,
            similarity = result.similarity,
            explanation = result.explanation
        )
    }

    suspend fun testConnection(config: CloudFaceSettings.Config): ConnectionTestResult {
        val result = router.testConnection(config)
        return ConnectionTestResult(result.explanation)
    }

    data class EnrollmentResult(
        val subject: String,
        val explanation: String
    )

    data class RecognitionResult(
        val matched: Boolean,
        val subject: String?,
        val similarity: Float,
        val explanation: String
    )

    data class ConnectionTestResult(
        val explanation: String
    )
}
