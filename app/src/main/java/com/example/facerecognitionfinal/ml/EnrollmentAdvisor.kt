package com.example.facerecognitionfinal.ml

class EnrollmentAdvisor(
    private val recommendedSamples: Int = RECOMMENDED_SAMPLES
) {

    fun advice(sampleCount: Int): String {
        return if (sampleCount < recommendedSamples) {
            "建议继续为该人员补录到 ${recommendedSamples} 组特征，覆盖正脸、轻微侧脸和不同表情，识别会更稳定。"
        } else {
            "该人员已达到推荐录入数量，可以直接进行识别演示。"
        }
    }

    companion object {
        const val RECOMMENDED_SAMPLES = 3
    }
}
