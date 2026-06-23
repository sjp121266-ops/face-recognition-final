package com.example.facerecognitionfinal.workflow

import com.example.facerecognitionfinal.data.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecognitionCoordinatorTest {

    private val coordinator = LocalRecognitionCoordinator()

    @Test
    fun enrollCreatesNewProfile() {
        val profiles = mutableListOf<PersonProfile>()

        val result = coordinator.enroll(profiles, "张三", embedding(0f))

        assertTrue(result is LocalRecognitionCoordinator.EnrollmentResult.Enrolled)
        result as LocalRecognitionCoordinator.EnrollmentResult.Enrolled
        assertEquals("张三", result.name)
        assertEquals(1, result.sampleCount)
        assertEquals(1, profiles.size)
        assertEquals("张三", profiles.first().name)
        assertEquals(1, profiles.first().embeddings.size)
    }

    @Test
    fun enrollAppendsExistingProfileAndKeepsLatestFiveSamples() {
        val removed = embedding(0f)
        val profiles = mutableListOf(
            PersonProfile(
                name = "张三",
                embeddings = mutableListOf(
                    removed,
                    embedding(1f),
                    embedding(2f),
                    embedding(3f),
                    embedding(4f)
                )
            )
        )

        val result = coordinator.enroll(profiles, "张三", embedding(5f))

        assertTrue(result is LocalRecognitionCoordinator.EnrollmentResult.Enrolled)
        result as LocalRecognitionCoordinator.EnrollmentResult.Enrolled
        assertEquals(5, result.sampleCount)
        assertEquals(5, profiles.first().embeddings.size)
        assertEquals(1f, profiles.first().embeddings.first()[0])
        assertTrue(profiles.first().embeddings.none { it === removed })
    }

    @Test
    fun enrollRejectsOutlierForExistingProfile() {
        val profiles = mutableListOf(
            PersonProfile("张三", mutableListOf(embedding(0f)))
        )

        val result = coordinator.enroll(profiles, "张三", embedding(20f))

        assertEquals(LocalRecognitionCoordinator.EnrollmentResult.Outlier("张三"), result)
        assertEquals(1, profiles.first().embeddings.size)
    }

    @Test
    fun enrollRejectsInvalidEmbeddingForNewProfile() {
        val profiles = mutableListOf<PersonProfile>()

        val result = coordinator.enroll(profiles, "张三", FloatArray(64))

        assertEquals(LocalRecognitionCoordinator.EnrollmentResult.InvalidEmbedding("张三"), result)
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun enrollRejectsInvalidEmbeddingForExistingProfile() {
        val profiles = mutableListOf(
            PersonProfile("张三", mutableListOf(embedding(0f)))
        )

        val result = coordinator.enroll(profiles, "张三", embedding(Float.NaN))

        assertEquals(LocalRecognitionCoordinator.EnrollmentResult.InvalidEmbedding("张三"), result)
        assertEquals(1, profiles.first().embeddings.size)
    }

    @Test
    fun recognizeReturnsMatchResult() {
        val profiles = listOf(PersonProfile("张三", mutableListOf(embedding(0f))))

        val result = coordinator.recognize(embedding(0f), profiles)

        assertTrue(result is LocalRecognitionCoordinator.RecognitionResult.Match)
        result as LocalRecognitionCoordinator.RecognitionResult.Match
        assertEquals("张三", result.name)
        assertEquals(0f, result.distance, 0.001f)
    }

    @Test
    fun recognizeReturnsUnknownResult() {
        val profiles = listOf(PersonProfile("张三", mutableListOf(embedding(0f))))

        val result = coordinator.recognize(embedding(20f), profiles)

        assertTrue(result is LocalRecognitionCoordinator.RecognitionResult.Unknown)
        result as LocalRecognitionCoordinator.RecognitionResult.Unknown
        assertEquals("张三", result.nearestName)
    }

    private fun embedding(firstValue: Float): FloatArray {
        return FloatArray(128).also { it[0] = firstValue }
    }
}
