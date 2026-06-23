package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecognitionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGuideBuilderTest {

    private val builder = DemoGuideBuilder()

    @Test
    fun guidesFirstEnrollmentWhenLibraryIsEmpty() {
        val guide = builder.build(emptyList(), emptyList(), cloudMode = false)

        assertTrue(guide.contains("录入样本"))
    }

    @Test
    fun guidesSecondPersonBeforeRecognitionDemo() {
        val guide = builder.build(listOf(profile("张三", 2)), emptyList(), cloudMode = false)

        assertTrue(guide.contains("第 2 位测试人员"))
    }

    @Test
    fun guidesSampleTopUpBeforeRecognitionDemo() {
        val guide = builder.build(
            listOf(profile("张三", 1), profile("李四", 2)),
            emptyList(),
            cloudMode = false
        )

        assertTrue(guide.contains("张三"))
        assertTrue(guide.contains("补录"))
    }

    @Test
    fun guidesUnknownThenLiveThenSummary() {
        val profiles = listOf(profile("张三", 2), profile("李四", 2))

        val afterSuccess = builder.build(
            profiles,
            listOf(record(RecognitionStatus.LOCAL_SUCCESS)),
            cloudMode = false
        )
        assertTrue(afterSuccess.contains("未知人员"))

        val afterUnknown = builder.build(
            profiles,
            listOf(record(RecognitionStatus.LOCAL_SUCCESS), record(RecognitionStatus.LOCAL_UNKNOWN)),
            cloudMode = false
        )
        assertTrue(afterUnknown.contains("多人视频"))

        val complete = builder.build(
            profiles,
            listOf(record(RecognitionStatus.LOCAL_SUCCESS), record(RecognitionStatus.LOCAL_UNKNOWN), record(RecognitionStatus.LIVE_DEMO)),
            cloudMode = false
        )
        assertTrue(complete.contains("生成截图摘要"))
    }

    @Test
    fun cloudModeStartsWithConnectionTest() {
        val guide = builder.build(emptyList(), emptyList(), cloudMode = true)

        assertTrue(guide.contains("测试云端连接"))
    }

    private fun profile(name: String, samples: Int): PersonProfile {
        return PersonProfile(name, MutableList(samples) { FloatArray(128) { samples.toFloat() } })
    }

    private fun record(status: String): RecognitionRecord {
        return RecognitionRecord(
            timestamp = 0L,
            name = "测试",
            distance = 0f,
            confidence = 90f,
            status = status,
            explanation = ""
        )
    }
}
