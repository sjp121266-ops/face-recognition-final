package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDemoRecordPolicyTest {

    @Test
    fun unconfirmedFacesAreNotRecorded() {
        assertFalse(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("张三", isKnown = true, isConfirmed = false),
                    observation("未知人员", isKnown = false, isConfirmed = false)
                )
            )
        )
    }

    @Test
    fun twoConfirmedUnknownFacesAreNotRecorded() {
        assertFalse(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("未知人员", isKnown = false),
                    observation("未知人员", isKnown = false)
                )
            )
        )
    }

    @Test
    fun oneKnownAndOneUnknownConfirmedFaceCanBeRecorded() {
        assertTrue(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("张三 86.0%", isKnown = true),
                    observation("未知人员", isKnown = false)
                )
            )
        )
    }

    @Test
    fun duplicateConfirmedIdentityDoesNotRecordMultiPersonDemo() {
        assertFalse(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("张三 86.0%", isKnown = true, identityKey = "张三"),
                    observation("张三 84.0%", isKnown = true, identityKey = "张三")
                )
            )
        )
    }

    @Test
    fun differentConfirmedIdentitiesRecordMultiPersonDemo() {
        assertTrue(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("张三 86.0%", isKnown = true, identityKey = "张三"),
                    observation("李四 84.0%", isKnown = true, identityKey = "李四")
                )
            )
        )
    }

    @Test
    fun qualityFailureLabelsDoNotCountAsConfirmedFaces() {
        assertFalse(
            LiveDemoRecordPolicy.shouldRecord(
                faceCount = 2,
                observations = listOf(
                    observation("张三 86.0%", isKnown = true),
                    observation("质量不足", isKnown = false)
                )
            )
        )
    }

    private fun observation(
        label: String,
        isKnown: Boolean,
        isConfirmed: Boolean = true,
        identityKey: String = label
    ): LiveDemoRecordPolicy.Observation {
        return LiveDemoRecordPolicy.Observation(
            label = label,
            isKnown = isKnown,
            isConfirmed = isConfirmed,
            identityKey = identityKey
        )
    }
}
