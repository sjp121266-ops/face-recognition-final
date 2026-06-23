package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmEvaluationBuilderTest {

    private val builder = AlgorithmEvaluationBuilder()

    @Test
    fun explainsInsufficientSamples() {
        val evaluation = builder.build(emptyList(), emptyList())

        assertTrue(evaluation.contains("算法样本评测说明"))
        assertTrue(evaluation.contains("默认 L2 阈值 10.000"))
        assertTrue(evaluation.contains("人员不足"))
        assertTrue(evaluation.contains("样本不足"))
        assertTrue(evaluation.contains("至少 2-3 人"))
    }

    @Test
    fun summarizesDistancesAndRiskSignals() {
        val profiles = listOf(
            PersonProfile("张三", mutableListOf(vector(0f), vector(0.05f), vector(0.1f))),
            PersonProfile("李四", mutableListOf(vector(20f), vector(20.05f), vector(20.1f)))
        )
        val records = listOf(
            record("张三", RecognitionStatus.LOCAL_SUCCESS, 3f, "安全区：识别成功"),
            record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, 11f, "拒识区：距离高于阈值"),
            record(RecognitionStatus.UNKNOWN_PERSON, RecognitionStatus.LOCAL_UNKNOWN, 4f, "候选人过近：拒绝确认")
        )

        val evaluation = builder.build(profiles, records)

        assertTrue(evaluation.contains("当前样本：2 人 / 6 组特征"))
        assertTrue(evaluation.contains("跨人区分度：张三/李四 最近跨人 L2"))
        assertTrue(evaluation.contains("本地成功记录：1 次，平均 L2 3.000"))
        assertTrue(evaluation.contains("本地未知记录：2 次"))
        assertTrue(evaluation.contains("拒识区 1 次，候选人过近拒绝确认 1 次"))
    }

    private fun record(name: String, status: String, distance: Float, explanation: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = name,
            distance = distance,
            confidence = 80f,
            status = status,
            explanation = explanation
        )
    }

    private fun vector(value: Float): FloatArray {
        return FloatArray(128) { value }
    }
}
