package com.example.facerecognitionfinal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoStateSnapshotTest {

    @Test
    fun exposesDerivedStateForUi() {
        val snapshot = DemoStateSnapshot(
            profiles = listOf(
                PersonProfile("张三", mutableListOf(FloatArray(128), FloatArray(128))),
                PersonProfile("李四", mutableListOf(FloatArray(128)))
            ),
            records = listOf(RecognitionRecord(1L, "张三", 0f, 100f, RecognitionStatus.LOCAL_SUCCESS, "ok")),
            isCloudMode = false,
            isLiveRecognitionEnabled = true
        )

        assertTrue(snapshot.hasProfiles)
        assertTrue(snapshot.hasRecords)
        assertEquals(2, snapshot.profileCount)
        assertEquals(1, snapshot.recordCount)
        assertEquals(3, snapshot.totalEmbeddings)
        assertFalse(snapshot.isCloudMode)
        assertTrue(snapshot.isLiveRecognitionEnabled)
    }
}
