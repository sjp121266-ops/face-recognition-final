package com.example.facerecognitionfinal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionRecordManagerTest {

    @Test
    fun addInsertsNewestRecordAtTop() {
        val manager = RecognitionRecordManager(maxRecords = 5, clock = { 123L })
        val records = mutableListOf(record("旧记录", timestamp = 1L))

        val added = manager.add(
            records = records,
            name = "张三",
            distance = 1.2f,
            confidence = 88f,
            status = RecognitionStatus.LOCAL_SUCCESS,
            explanation = "安全区"
        )

        assertEquals(added, records.first())
        assertEquals(123L, added.timestamp)
        assertEquals("张三", added.name)
        assertEquals("旧记录", records.last().name)
    }

    @Test
    fun addTrimsOldestRecords() {
        val manager = RecognitionRecordManager(maxRecords = 2, clock = { 10L })
        val records = mutableListOf(
            record("旧 1", timestamp = 1L),
            record("旧 2", timestamp = 2L)
        )

        manager.add(records, "新记录", 0f, 100f, RecognitionStatus.LOCAL_SUCCESS, "ok")

        assertEquals(2, records.size)
        assertEquals("新记录", records[0].name)
        assertEquals("旧 1", records[1].name)
    }

    @Test
    fun trimRemovesRecordsFromEnd() {
        val manager = RecognitionRecordManager(maxRecords = 2)
        val records = mutableListOf(
            record("保留 1", timestamp = 1L),
            record("保留 2", timestamp = 2L),
            record("删除", timestamp = 3L)
        )

        manager.trim(records)

        assertEquals(listOf("保留 1", "保留 2"), records.map { it.name })
    }

    private fun record(name: String, timestamp: Long): RecognitionRecord {
        return RecognitionRecord(
            timestamp = timestamp,
            name = name,
            distance = 0f,
            confidence = 0f,
            status = "测试",
            explanation = "测试"
        )
    }
}
