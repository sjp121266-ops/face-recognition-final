package com.example.facerecognitionfinal.workflow

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.ml.EnrollmentAdvisor
import com.example.facerecognitionfinal.ml.FaceEmbeddingGuard
import com.example.facerecognitionfinal.ml.RecognitionEngine

class LocalRecognitionCoordinator(
    private val recognitionEngine: RecognitionEngine = RecognitionEngine(),
    private val enrollmentAdvisor: EnrollmentAdvisor = EnrollmentAdvisor(),
    private val faceEmbeddingGuard: FaceEmbeddingGuard = FaceEmbeddingGuard(),
    private val maxEmbeddingsPerPerson: Int = MAX_EMBEDDINGS_PER_PERSON
) {

    fun setThreshold(threshold: Float) {
        recognitionEngine.threshold = threshold
    }

    fun getThreshold(): Float = recognitionEngine.threshold

    fun enroll(
        profiles: MutableList<PersonProfile>,
        name: String,
        embedding: FloatArray
    ): EnrollmentResult {
        if (!faceEmbeddingGuard.isValid(embedding)) {
            return EnrollmentResult.InvalidEmbedding(name)
        }
        val profile = profiles.firstOrNull { it.name == name }
        if (profile == null) {
            profiles.add(PersonProfile(name, mutableListOf(embedding)))
        } else {
            if (faceEmbeddingGuard.isOutlierForProfile(embedding, profile)) {
                return EnrollmentResult.Outlier(name)
            }
            profile.embeddings.add(embedding)
            while (profile.embeddings.size > maxEmbeddingsPerPerson) {
                profile.embeddings.removeAt(0)
            }
        }
        val sampleCount = profiles.first { it.name == name }.embeddings.size
        return EnrollmentResult.Enrolled(
            name = name,
            sampleCount = sampleCount,
            advice = enrollmentAdvisor.advice(sampleCount)
        )
    }

    fun recognize(
        embedding: FloatArray,
        profiles: List<PersonProfile>
    ): RecognitionResult {
        return when (val result = recognitionEngine.recognize(embedding, profiles)) {
            is RecognitionEngine.RecognitionResult.Match -> RecognitionResult.Match(
                name = result.name,
                distance = result.distance,
                confidence = result.confidence,
                explanation = result.explanation
            )
            is RecognitionEngine.RecognitionResult.Unknown -> RecognitionResult.Unknown(
                distance = result.distance,
                confidence = result.confidence,
                nearestName = result.nearestName,
                explanation = result.explanation
            )
        }
    }

    fun getVpTreeStats(): VpTreeStats {
        val tree = recognitionEngine.vpTree
        return VpTreeStats(
            size = tree.size,
            linearScans = tree.lastQueryLinearScans,
            indexScans = tree.lastQueryIndexScans,
            savingsPercent = tree.lastQuerySavingsPercent,
            durationMs = tree.lastQueryDurationNs / 1_000_000f,
            route = tree.lastQueryRoute
        )
    }

    data class VpTreeStats(
        val size: Int,
        val linearScans: Int,
        val indexScans: Int,
        val savingsPercent: Int,
        val durationMs: Float,
        val route: String
    )

    sealed class EnrollmentResult {
        data class Enrolled(
            val name: String,
            val sampleCount: Int,
            val advice: String
        ) : EnrollmentResult()

        data class Outlier(val name: String) : EnrollmentResult()

        data class InvalidEmbedding(val name: String) : EnrollmentResult()
    }

    sealed class RecognitionResult {
        data class Match(
            val name: String,
            val distance: Float,
            val confidence: Float,
            val explanation: String
        ) : RecognitionResult()

        data class Unknown(
            val distance: Float,
            val confidence: Float,
            val nearestName: String?,
            val explanation: String
        ) : RecognitionResult()
    }

    companion object {
        const val MAX_EMBEDDINGS_PER_PERSON = 5
    }
}
