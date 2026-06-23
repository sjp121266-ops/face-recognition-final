package com.example.facerecognitionfinal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {

    @Test
    fun replaceUpdatesProfilesAndPersists() {
        val saved = mutableListOf<List<PersonProfile>>()
        val repository = ProfileRepository(emptyList(), saveProfiles = { saved.add(it.toList()) }, clearProfiles = {})

        repository.replace(listOf(PersonProfile("张三", mutableListOf(FloatArray(128)))))

        assertEquals(listOf("张三"), repository.profiles.map { it.name })
        assertEquals(listOf("张三"), saved.single().map { it.name })
    }

    @Test
    fun updatePersistsMutatedProfiles() {
        val saved = mutableListOf<List<PersonProfile>>()
        val repository = ProfileRepository(emptyList(), saveProfiles = { saved.add(it.toList()) }, clearProfiles = {})

        repository.update { profiles ->
            profiles.add(PersonProfile("李四"))
        }

        assertEquals("李四", repository.profiles.single().name)
        assertEquals("李四", saved.single().single().name)
    }

    @Test
    fun deletePersonPersistsOnlyWhenRemoved() {
        var saveCount = 0
        val repository = ProfileRepository(
            initialProfiles = listOf(PersonProfile("张三"), PersonProfile("李四")),
            saveProfiles = { saveCount++ },
            clearProfiles = {}
        )

        assertTrue(repository.deletePerson("张三"))
        assertFalse(repository.deletePerson("王五"))

        assertEquals(listOf("李四"), repository.profiles.map { it.name })
        assertEquals(1, saveCount)
    }

    @Test
    fun clearClearsMemoryAndStorage() {
        var cleared = false
        val repository = ProfileRepository(
            initialProfiles = listOf(PersonProfile("张三")),
            saveProfiles = {},
            clearProfiles = { cleared = true }
        )

        repository.clear()

        assertTrue(repository.profiles.isEmpty())
        assertTrue(cleared)
    }
}
