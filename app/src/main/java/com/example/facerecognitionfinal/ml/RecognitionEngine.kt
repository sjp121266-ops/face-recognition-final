package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile

class RecognitionEngine(
    threshold: Float = DEFAULT_L2_THRESHOLD
) {
    var threshold: Float = threshold.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_L2_THRESHOLD
        set(value) {
            field = value.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_L2_THRESHOLD
        }

    val vpTree: VpTree = VpTree()

    /**
     * Recognize a face embedding using VP-Tree accelerated search.
     *
     * The VP-Tree finds the single nearest neighbor across all embeddings.
     * We then compute per-profile aggregate stats only for the top match
     * to enable confidence scoring and ambiguity checking.
     */
    fun recognize(embedding: FloatArray, profiles: List<PersonProfile>): RecognitionResult {
        if (!isValidEmbedding(embedding)) {
            return RecognitionResult.Unknown(
                distance = Float.MAX_VALUE,
                confidence = 0f,
                nearestName = null,
                explanation = "当前人脸特征向量异常，无法进行匹配，请重新拍照。"
            )
        }
        if (profiles.isEmpty()) {
            return RecognitionResult.Unknown(
                distance = Float.MAX_VALUE,
                confidence = 0f,
                nearestName = null,
                explanation = "本地人脸库为空，无法进行匹配。"
            )
        }

        // Build VP-Tree index with normalized + fused embeddings
        val validEntries = profiles.flatMap { profile ->
            val validEmbs = profile.embeddings.filter { isValidEmbedding(it) }.map { EmbeddingDistance.normalize(it) }
            val entries = mutableListOf<VpTree.Entry>()
            validEmbs.forEach { emb -> entries.add(VpTree.Entry(profile.name, emb)) }
            if (validEmbs.size >= 2) {
                val fused = FloatArray(EXPECTED_EMBEDDING_SIZE)
                for (i in 0 until EXPECTED_EMBEDDING_SIZE) { fused[i] = validEmbs.map { it[i] }.average().toFloat() }
                entries.add(VpTree.Entry(profile.name, EmbeddingDistance.normalize(fused)))
            }
            entries
        }
        if (vpTree.size != validEntries.size) { vpTree.build(validEntries) }

        // Normalize query for cosine-compatible comparison
        val normalizedQuery = EmbeddingDistance.normalize(embedding)

        // ---- VP-Tree accelerated search ----
        val vpResult = vpTree.findNearest(normalizedQuery)

        if (vpResult == null) {
            return RecognitionResult.Unknown(
                distance = Float.MAX_VALUE,
                confidence = 0f,
                nearestName = null,
                explanation = "VP-Tree 未检索到有效条目，本地人脸库可能为空。"
            )
        }

        val bestName = vpResult.entry.name
        val bestDistance = vpResult.distance

        // Compute per-profile aggregate stats for the matched person
        val matchedProfile = profiles.find { it.name == bestName }
        val bestCandidate = if (matchedProfile != null) {
            buildCandidate(matchedProfile, normalizedQuery)
        } else { null }

        // For ambiguity checking, find the runner-up via targeted scan
        val runnerUp = findRunnerUp(normalizedQuery, profiles, bestName)

        // Use the VP-Tree distance directly; fall back to candidate score if available
        val effectiveScore = bestCandidate?.score ?: bestDistance
        val confidence = distanceToConfidence(effectiveScore)
        val riskLevel = riskLevel(effectiveScore)

        val skippedEmbeddings = profiles.sumOf { profile ->
            profile.embeddings.count { !isValidStoredEmbedding(embedding, it) }
        }
        val skippedExplanation = skippedExplanation(skippedEmbeddings)

        // Adaptive per-person threshold based on intra-class variance
        val adaptiveThreshold = if (matchedProfile != null) {
            computePersonThreshold(matchedProfile, threshold)
        } else { threshold }

        return if (effectiveScore <= adaptiveThreshold && isAmbiguous(effectiveScore, runnerUp)) {
            RecognitionResult.Unknown(
                distance = bestDistance,
                confidence = confidence,
                nearestName = bestName,
                explanation = "候选人过近：最接近 ${bestName}，但与 ${runnerUp?.name ?: "?"} 的 L2 差距只有 ${formatDistance((runnerUp?.score ?: 0f) - effectiveScore)}，低于安全间隔 ${formatDistance(AMBIGUOUS_MARGIN)}。为降低误识别风险，本次拒绝确认。$skippedExplanation"
            )
        } else if (effectiveScore <= adaptiveThreshold) {
            RecognitionResult.Match(
                name = bestName,
                distance = bestDistance,
                confidence = confidence,
                explanation = "${riskLevel.label}：VP-Tree 搜索命中 ${bestName}，最近 L2 距离 ${formatDistance(bestDistance)}，综合得分 ${formatDistance(effectiveScore)} 低于自适应阈值 ${formatDistance(adaptiveThreshold)}，判定为同一人。${bestCandidate?.let { sampleExplanation(it) } ?: ""}${runnerUp?.let { runnerUpExplanation(it) } ?: ""}${vpTreeStatsSummary()}$skippedExplanation"
            )
        } else {
            RecognitionResult.Unknown(
                distance = bestDistance,
                confidence = confidence,
                nearestName = bestName,
                explanation = "${riskLevel.label}：最接近 ${bestName}，综合 L2 距离 ${formatDistance(effectiveScore)} 高于阈值 ${formatDistance(threshold)}，判定为未知人员。${bestCandidate?.let { sampleExplanation(it) } ?: ""}$skippedExplanation"
            )
        }
    }

    /**
     * Build a Candidate (aggregate stats) for a single person profile.
     */
    private fun buildCandidate(profile: PersonProfile, query: FloatArray): Candidate? {
        val validEmbeddings = profile.embeddings
            .filter { isValidStoredEmbedding(query, it) }
        if (validEmbeddings.isEmpty()) return null

        val distances = validEmbeddings.map { l2Distance(query, it) }
        val minDistance = distances.minOrNull() ?: Float.MAX_VALUE
        val averageDistance = distances.average().toFloat()
        return Candidate(
            name = profile.name,
            minDistance = minDistance,
            averageDistance = averageDistance,
            sampleCount = distances.size
        )
    }

    /**
     * Find the runner-up (second best match) by scanning profiles excluding bestName.
     * This is a focused linear scan over per-profile aggregates, not a full embedding scan.
     */
    private fun findRunnerUp(
        embedding: FloatArray,
        profiles: List<PersonProfile>,
        excludeName: String
    ): Candidate? {
        return profiles
            .filter { it.name != excludeName }
            .mapNotNull { buildCandidate(it, embedding) }
            .minByOrNull { it.score }
    }

    private fun l2Distance(first: FloatArray, second: FloatArray): Float {
        return EmbeddingDistance.l2(first, second)
    }

    private fun isValidStoredEmbedding(query: FloatArray, stored: FloatArray): Boolean {
        return stored.size == query.size && isValidEmbedding(stored)
    }

    private fun isValidEmbedding(embedding: FloatArray): Boolean {
        return embedding.size == EXPECTED_EMBEDDING_SIZE && embedding.all { it.isFinite() }
    }

    private fun distanceToConfidence(distance: Float): Float {
        // Sigmoid calibration: maps distance to well-calibrated confidence
        // Center at threshold/2, steepness controlled by threshold
        val centered = (distance - threshold / 2f) / (threshold / 4f)
        val sigmoid = 1f / (1f + kotlin.math.exp(centered))
        return (sigmoid * 100f).coerceIn(0f, 100f)
    }

    private fun isAmbiguous(score: Float, runnerUp: Candidate?): Boolean {
        if (runnerUp == null) return false
        val distanceGap = runnerUp.score - score
        val scoreRatio = if (runnerUp.score == 0f) 1f else score / runnerUp.score
        return distanceGap < AMBIGUOUS_MARGIN || scoreRatio > AMBIGUOUS_RATIO
    }

    private fun riskLevel(distance: Float): RiskLevel {
        val ratio = distance / threshold
        return when {
            ratio <= HIGH_CONFIDENCE_RATIO -> RiskLevel("安全区")
            ratio <= STABLE_CONFIDENCE_RATIO -> RiskLevel("稳定区")
            ratio <= 1f -> RiskLevel("边界区")
            else -> RiskLevel("拒识区")
        }
    }

    private fun sampleExplanation(candidate: Candidate): String {
        return " 最近样本距离 ${formatDistance(candidate.minDistance)}，平均距离 ${formatDistance(candidate.averageDistance)}，样本数 ${candidate.sampleCount}。"
    }

    private fun runnerUpExplanation(runnerUp: Candidate): String {
        return " 第二候选 ${runnerUp.name}，综合距离 ${formatDistance(runnerUp.score)}。"
    }

    private fun vpTreeStatsSummary(): String {
        val savings = vpTree.lastQuerySavingsPercent
        if (savings <= 0) return ""
        return " VP-Tree 搜索路径 ${vpTree.lastQueryRoute}，跳过 ${savings}% 条目。"
    }

    /**
     * Compute adaptive per-person threshold based on intra-class variance.
     * Lower variance → stricter threshold. Higher variance → looser threshold.
     * Range: [baseThreshold * 0.5, baseThreshold * 1.5]
     */
    private fun computePersonThreshold(profile: PersonProfile, baseThreshold: Float): Float {
        val validEmbs = profile.embeddings.filter { isValidEmbedding(it) }
        if (validEmbs.size < 2) return baseThreshold

        // Compute average pairwise L2 distance within this person's samples
        var totalDist = 0f
        var count = 0
        for (i in validEmbs.indices) {
            for (j in i + 1 until validEmbs.size) {
                totalDist += EmbeddingDistance.l2(validEmbs[i], validEmbs[j])
                count++
            }
        }
        if (count == 0) return baseThreshold

        val avgIntraDist = totalDist / count
        // Scale factor: tight clusters get multiplier 0.6, loose clusters get 1.4
        val scale = (avgIntraDist / 5f).coerceIn(0.6f, 1.4f)
        return (baseThreshold * scale).coerceIn(baseThreshold * 0.5f, baseThreshold * 1.5f)
    }

    private fun skippedExplanation(skippedEmbeddings: Int): String {
        return if (skippedEmbeddings > 0) {
            " 已跳过 ${skippedEmbeddings} 组异常特征。"
        } else {
            ""
        }
    }

    private fun formatDistance(distance: Float): String {
        return String.format(java.util.Locale.CHINA, "%.3f", distance)
    }

    private data class Candidate(
        val name: String,
        val minDistance: Float,
        val averageDistance: Float,
        val sampleCount: Int
    ) {
        val score: Float = minDistance + (averageDistance - minDistance) * STABILITY_PENALTY
    }

    private data class RiskLevel(
        val label: String
    )

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
        const val DEFAULT_L2_THRESHOLD = 10f
        const val EXPECTED_EMBEDDING_SIZE = 128
        private const val HIGH_CONFIDENCE_RATIO = 0.6f
        private const val STABLE_CONFIDENCE_RATIO = 0.85f
        private const val AMBIGUOUS_MARGIN = 0.75f
        private const val AMBIGUOUS_RATIO = 0.92f
        private const val STABILITY_PENALTY = 0.35f
    }
}
