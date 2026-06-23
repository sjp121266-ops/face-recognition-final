package com.example.facerecognitionfinal.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FaceStoreTest {

    private lateinit var faceStore: FaceStore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        faceStore = FaceStore(context)
        faceStore.clearProfiles()
        faceStore.clearRecords()
    }

    @Test
    fun saveAndLoadProfilesRoundtrip() {
        val profiles = mutableListOf(
            PersonProfile("Alice", mutableListOf(FloatArray(128) { it.toFloat() }))
        )
        faceStore.saveProfiles(profiles)
        val loaded = faceStore.loadProfiles()
        assertEquals(1, loaded.size)
        assertEquals("Alice", loaded[0].name)
        assertEquals(128, loaded[0].embeddings[0].size)
        assertEquals(0f, loaded[0].embeddings[0][0])
        assertEquals(127f, loaded[0].embeddings[0][127])
    }

    @Test
    fun saveAndLoadRecordsRoundtrip() {
        val records = mutableListOf(
            RecognitionRecord(1000L, "Bob", 5.0f, 80.0f, "识别成功", "test explanation")
        )
        faceStore.saveRecords(records)
        val loaded = faceStore.loadRecords()
        assertEquals(1, loaded.size)
        assertEquals("Bob", loaded[0].name)
        assertEquals(5.0f, loaded[0].distance, 0.001f)
    }

    @Test
    fun loadProfilesReturnsEmptyListForFreshStore() {
        val loaded = faceStore.loadProfiles()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun clearProfilesRemovesAllProfiles() {
        faceStore.saveProfiles(mutableListOf(PersonProfile("Test")))
        faceStore.clearProfiles()
        assertTrue(faceStore.loadProfiles().isEmpty())
    }

    @Test
    fun clearRecordsRemovesAllRecords() {
        faceStore.saveRecords(
            mutableListOf(RecognitionRecord(1L, "X", 1f, 1f, "s", "e"))
        )
        faceStore.clearRecords()
        assertTrue(faceStore.loadRecords().isEmpty())
    }

    @Test
    fun recordsAreCappedAtMaxRecords() {
        val records = (1..50).map {
            RecognitionRecord(it.toLong(), "P$it", 1f, 1f, "s", "e")
        }.toMutableList()
        faceStore.saveRecords(records)
        val loaded = faceStore.loadRecords()
        assertTrue(loaded.size <= FaceStore.MAX_RECORDS)
    }

    @Test
    fun deletePersonRemovesSinglePerson() {
        val profiles = mutableListOf(
            PersonProfile("Alice", mutableListOf(FloatArray(128) { 0f })),
            PersonProfile("Bob", mutableListOf(FloatArray(128) { 1f }))
        )
        faceStore.saveProfiles(profiles)
        faceStore.deletePerson("Alice")
        val loaded = faceStore.loadProfiles()
        assertEquals(1, loaded.size)
        assertEquals("Bob", loaded[0].name)
    }
}
