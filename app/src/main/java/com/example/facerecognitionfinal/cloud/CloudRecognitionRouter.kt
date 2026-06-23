package com.example.facerecognitionfinal.cloud

class CloudRecognitionRouter(
    private val facePlusPlusFactory: (CloudFaceSettings.Config) -> CloudFaceGateway = { FacePlusPlusClient(it) },
    private val comprefaceFactory: (CloudFaceSettings.Config) -> CloudFaceGateway = { CloudFaceClient(it) }
) {

    fun testConnection(config: CloudFaceSettings.Config): CloudFaceClient.CloudResult.ConnectionTest {
        return gatewayFor(config).testConnection()
    }

    fun enroll(
        config: CloudFaceSettings.Config,
        subject: String,
        jpegBytes: ByteArray
    ): CloudFaceClient.CloudResult.Enrolled {
        return gatewayFor(config).enroll(subject, jpegBytes)
    }

    fun recognize(
        config: CloudFaceSettings.Config,
        jpegBytes: ByteArray
    ): CloudFaceClient.CloudResult.Recognized {
        return gatewayFor(config).recognize(jpegBytes)
    }

    private fun gatewayFor(config: CloudFaceSettings.Config): CloudFaceGateway {
        return when (config.provider) {
            CloudProvider.FACE_PLUS_PLUS -> facePlusPlusFactory(config)
            CloudProvider.COMPREFACE -> comprefaceFactory(config)
        }
    }
}
