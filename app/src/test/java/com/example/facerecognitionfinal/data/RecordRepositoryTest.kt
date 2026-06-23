package com.example.facerecognitionfinal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordRepositoryTest {

    @Test
    fun addCreatesRecordAndPersists() {
        val saved = mutableListOf<List<RecognitionRecord>>()
        val repository = RecordRepository(
            initialRecords = emptyList(),
            saveRecords = { saved.add(it.toList()) },
            clearRecords = {},
            recordManager = RecognitionRecordManager(clock = { 42L })
        )

        val record = repository.add("张三", 1.2f, 90f, RecognitionStatus.LOCAL_SUCCESS, "安全区")

        assertEquals(42L, record.timestamp)
        assertEquals(record, repository.records.single())
        assertEquals(record, saved.single().single())
    }

    @Test
    fun addTrimsThroughRecordManager() {
        val repository = RecordRepository(
            initialRecords = listOf(record("旧 1"), record("旧 2")),
            saveRecords = {},
            clearRecords = {},
            recordManager = RecognitionRecordManager(maxRecords = 2, clock = { 10L })
        )

        repository.add("新记录", 0f, 100f, RecognitionStatus.LOCAL_SUCCESS, "ok")

        assertEquals(listOf("新记录", "旧 1"), repository.records.map { it.name })
    }

    @Test
    fun clearClearsMemoryAndStorage() {
        var cleared = false
        val repository = RecordRepository(
            initialRecords = listOf(record("旧记录")),
            saveRecords = {},
            clearRecords = { cleared = true }
        )

        repository.clear()

        assertTrue(repository.records.isEmpty())
        assertTrue(cleared)
    }

    private fun record(name: String): RecognitionRecord {
        return RecognitionRecord(1L, name, 0f, 0f, "测试", "测试")
    }
}
