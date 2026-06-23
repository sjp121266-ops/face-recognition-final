package com.example.facerecognitionfinal.cloud

interface CloudFaceGateway {
    fun testConnection(): CloudFaceClient.CloudResult.ConnectionTest

    fun enroll(subject: String, jpegBytes: ByteArray): CloudFaceClient.CloudResult.Enrolled

    fun recognize(jpegBytes: ByteArray): CloudFaceClient.CloudResult.Recognized
}
