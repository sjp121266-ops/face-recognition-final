package com.example.facerecognitionfinal.ml

object LiveDemoRecordPolicy {

    private val ignoredLabels = setOf(
        LiveRecognitionStabilizer.LABEL_ANALYZING,
        "质量不足",
        "提取失败",
        "超出画面"
    )

    fun shouldRecord(faceCount: Int, observations: List<Observation>): Boolean {
        if (faceCount < MIN_CONFIRMED_FACE_COUNT) return false
        val confirmedLabels = confirmedLabels(observations)
        val hasKnownIdentity = observations.any { it.isConfirmed && it.isKnown }
        return confirmedLabels.size >= MIN_CONFIRMED_FACE_COUNT && hasKnownIdentity
    }

    fun confirmedLabels(observations: List<Observation>): List<String> {
        return observations
            .filter { it.isConfirmed }
            .distinctBy { it.identityKey }
            .map { it.label }
            .filter { it !in ignoredLabels }
    }

    data class Observation(
        val label: String,
        val isKnown: Boolean,
        val isConfirmed: Boolean,
        val identityKey: String = label
    )

    private const val MIN_CONFIRMED_FACE_COUNT = 2
}
