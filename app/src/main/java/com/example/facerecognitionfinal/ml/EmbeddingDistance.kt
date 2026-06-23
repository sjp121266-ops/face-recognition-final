package com.example.facerecognitionfinal.ml

import kotlin.math.sqrt

object EmbeddingDistance {

    /**
     * Euclidean (L2) distance. Lower = more similar.
     */
    fun l2(first: FloatArray, second: FloatArray): Float {
        require(first.size == second.size) { "人脸特征维度不一致，无法计算距离。" }
        var sum = 0f
        for (index in first.indices) {
            val diff = first[index] - second[index]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Cosine similarity. Higher = more similar (range roughly -1..1, typically 0..1 for FaceNet).
     */
    fun cosine(first: FloatArray, second: FloatArray): Float {
        require(first.size == second.size)
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in first.indices) {
            dot += first[i] * second[i]
            normA += first[i] * first[i]
            normB += second[i] * second[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }

    /**
     * Cosine distance = 1 - cosine_similarity. Lower = more similar (same scale direction as L2).
     */
    fun cosineDistance(first: FloatArray, second: FloatArray): Float {
        return 1f - cosine(first, second)
    }

    /**
     * L2-normalize an embedding to unit length. Makes cosine and L2 equivalent.
     */
    fun normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = sqrt(norm)
        val result = FloatArray(embedding.size)
        if (norm > 0f) {
            for (i in embedding.indices) result[i] = embedding[i] / norm
        }
        return result
    }

    /**
     * Hybrid metric: weighted blend of L2 distance and cosine distance.
     * Alpha controls the blend (0 = pure L2, 1 = pure cosine). Default 0.5.
     */
    fun hybrid(first: FloatArray, second: FloatArray, alpha: Float = 0.5f): Float {
        val l2Dist = l2(first, second) / 10f // Normalize L2 to similar scale as cosine
        val cosDist = cosineDistance(first, second)
        return alpha * cosDist + (1f - alpha) * l2Dist
    }
}
