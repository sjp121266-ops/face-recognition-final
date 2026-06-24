package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile

class FaceEmbeddingGuard(
    private val expectedSize: Int = RecognitionEngine.EXPECTED_EMBEDDING_SIZE,
    private val outlierThreshold: Float = Float.MAX_VALUE
) {

    fun isValid(embedding: FloatArray): Boolean {
        return embedding.size == expectedSize && embedding.all { it.isFinite() }
    }

    fun isOutlierForProfile(embedding: FloatArray, profile: PersonProfile): Boolean {
        if (!isValid(embedding) || profile.embeddings.isEmpty()) return false
        val nearestDistance = profile.embeddings
            .asSequence()
            .filter { isValid(it) }
            .map { distance(embedding, it) }
            .minOrNull()
            ?: return false
        return nearestDistance > outlierThreshold
    }

    fun distance(first: FloatArray, second: FloatArray): Float {
        return EmbeddingDistance.l2(first, second)
    }
}
