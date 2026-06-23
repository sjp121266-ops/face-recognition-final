package com.example.facerecognitionfinal.ml

import com.example.facerecognitionfinal.data.PersonProfile

class FaceLibraryHealthAnalyzer(
    private val expectedEmbeddingSize: Int = EXPECTED_EMBEDDING_SIZE,
    private val recommendedPeople: Int = RECOMMENDED_PEOPLE,
    private val recommendedSamplesPerPerson: Int = RECOMMENDED_SAMPLES_PER_PERSON,
    private val unstableSampleDistance: Float = UNSTABLE_SAMPLE_DISTANCE,
    private val confusedPersonDistance: Float = CONFUSED_PERSON_DISTANCE
) {

    fun analyze(profiles: List<PersonProfile>): Health {
        val issues = mutableListOf<String>()
        val invalidPeople = mutableListOf<String>()
        val peopleNeedSamples = mutableListOf<String>()
        val unstablePeople = mutableListOf<PersonConsistency>()
        val separation = separationOf(profiles)
        val maxIntraDistance = maxIntraDistanceOf(profiles)
        val thresholdAdvice = thresholdAdviceOf(maxIntraDistance, separation)

        profiles.forEach { profile ->
            if (profile.embeddings.any { !isValidEmbedding(it) }) {
                invalidPeople.add(profile.name)
            }
            if (profile.embeddings.size < recommendedSamplesPerPerson) {
                peopleNeedSamples.add(profile.name)
            }
            val consistency = consistencyOf(profile)
            if (consistency.maxPairDistance > unstableSampleDistance) {
                unstablePeople.add(consistency)
            }
        }

        if (profiles.isEmpty()) {
            issues.add("还没有录入人员")
        }
        if (profiles.size in 1 until recommendedPeople) {
            issues.add("建议至少录入 ${recommendedPeople} 位测试人员，方便演示区分 A/B")
        }
        if (peopleNeedSamples.isNotEmpty()) {
            issues.add("${peopleNeedSamples.joinToString("、")} 样本偏少，建议每人至少 ${recommendedSamplesPerPerson} 组")
        }
        if (invalidPeople.isNotEmpty()) {
            issues.add("${invalidPeople.joinToString("、")} 存在异常特征向量，建议删除后重新录入")
        }
        if (unstablePeople.isNotEmpty()) {
            issues.add(
                unstablePeople.joinToString("；") {
                    "${it.name} 样本差异偏大，最大 L2 ${formatDistance(it.maxPairDistance)}，建议重新补录"
                }
            )
        }
        if (separation != null && separation.distance < confusedPersonDistance) {
            issues.add(
                "${separation.firstName} 与 ${separation.secondName} 区分度偏低，最近跨人 L2 ${formatDistance(separation.distance)}，建议增加正脸样本或重新录入"
            )
        }

        return Health(
            level = when {
                profiles.isEmpty() || invalidPeople.isNotEmpty() -> Level.BLOCKED
                peopleNeedSamples.isNotEmpty() ||
                    profiles.size < recommendedPeople ||
                    unstablePeople.isNotEmpty() ||
                    (separation != null && separation.distance < confusedPersonDistance) -> Level.WARNING
                else -> Level.READY
            },
            totalPeople = profiles.size,
            totalEmbeddings = profiles.sumOf { it.embeddings.size },
            invalidPeople = invalidPeople,
            peopleNeedSamples = peopleNeedSamples,
            unstablePeople = unstablePeople,
            nearestSeparation = separation,
            thresholdAdvice = thresholdAdvice,
            message = if (issues.isEmpty()) {
                "可以演示，建议再准备未知人员和断网识别截图"
            } else {
                issues.joinToString("；")
            }
        )
    }

    private fun consistencyOf(profile: PersonProfile): PersonConsistency {
        val validEmbeddings = profile.embeddings.filter { isValidEmbedding(it) }
        if (validEmbeddings.size < 2) {
            return PersonConsistency(profile.name, 0f)
        }
        var maxDistance = 0f
        for (firstIndex in validEmbeddings.indices) {
            for (secondIndex in firstIndex + 1 until validEmbeddings.size) {
                maxDistance = maxOf(
                    maxDistance,
                    l2Distance(validEmbeddings[firstIndex], validEmbeddings[secondIndex])
                )
            }
        }
        return PersonConsistency(profile.name, maxDistance)
    }

    private fun separationOf(profiles: List<PersonProfile>): PersonSeparation? {
        val validProfiles = profiles
            .map { profile ->
                profile to profile.embeddings.filter { isValidEmbedding(it) }
            }
            .filter { (_, embeddings) -> embeddings.isNotEmpty() }

        if (validProfiles.size < 2) return null

        var nearest: PersonSeparation? = null
        for (firstIndex in validProfiles.indices) {
            for (secondIndex in firstIndex + 1 until validProfiles.size) {
                val (firstProfile, firstEmbeddings) = validProfiles[firstIndex]
                val (secondProfile, secondEmbeddings) = validProfiles[secondIndex]
                firstEmbeddings.forEach { first ->
                    secondEmbeddings.forEach { second ->
                        val distance = l2Distance(first, second)
                        if (nearest == null || distance < nearest!!.distance) {
                            nearest = PersonSeparation(firstProfile.name, secondProfile.name, distance)
                        }
                    }
                }
            }
        }
        return nearest
    }

    private fun maxIntraDistanceOf(profiles: List<PersonProfile>): Float? {
        val consistencies = profiles
            .map { consistencyOf(it) }
            .filter { it.maxPairDistance > 0f }
        return consistencies.maxOfOrNull { it.maxPairDistance }
    }

    private fun thresholdAdviceOf(
        maxIntraDistance: Float?,
        separation: PersonSeparation?
    ): ThresholdAdvice? {
        if (maxIntraDistance == null || separation == null) return null
        return if (maxIntraDistance < separation.distance) {
            val suggested = (maxIntraDistance + separation.distance) / 2f
            ThresholdAdvice(
                maxIntraDistance = maxIntraDistance,
                nearestCrossPersonDistance = separation.distance,
                suggestedThreshold = suggested,
                message = "建议阈值约 ${formatDistance(suggested)}，位于同人最大距离和跨人最近距离之间。"
            )
        } else {
            ThresholdAdvice(
                maxIntraDistance = maxIntraDistance,
                nearestCrossPersonDistance = separation.distance,
                suggestedThreshold = null,
                message = "同人样本距离已接近或超过跨人距离，不建议调高阈值，建议先重新补录。"
            )
        }
    }

    private fun l2Distance(first: FloatArray, second: FloatArray): Float {
        return EmbeddingDistance.l2(first, second)
    }

    private fun isValidEmbedding(embedding: FloatArray): Boolean {
        return embedding.size == expectedEmbeddingSize && embedding.all { it.isFinite() }
    }

    private fun formatDistance(value: Float): String {
        return String.format(java.util.Locale.CHINA, "%.3f", value)
    }

    fun sanitize(profiles: List<PersonProfile>): SanitizeResult {
        var removedEmbeddings = 0
        val sanitized = profiles.mapNotNull { profile ->
            val validEmbeddings = profile.embeddings.filter { isValidEmbedding(it) }
            removedEmbeddings += profile.embeddings.size - validEmbeddings.size
            if (validEmbeddings.isEmpty()) {
                null
            } else {
                PersonProfile(profile.name, validEmbeddings.toMutableList())
            }
        }.toMutableList()
        return SanitizeResult(
            profiles = sanitized,
            removedEmbeddings = removedEmbeddings,
            removedPeople = profiles.size - sanitized.size
        )
    }

    data class Health(
        val level: Level,
        val totalPeople: Int,
        val totalEmbeddings: Int,
        val invalidPeople: List<String>,
        val peopleNeedSamples: List<String>,
        val unstablePeople: List<PersonConsistency>,
        val nearestSeparation: PersonSeparation?,
        val thresholdAdvice: ThresholdAdvice?,
        val message: String
    )

    data class PersonConsistency(
        val name: String,
        val maxPairDistance: Float
    )

    data class PersonSeparation(
        val firstName: String,
        val secondName: String,
        val distance: Float
    )

    data class ThresholdAdvice(
        val maxIntraDistance: Float,
        val nearestCrossPersonDistance: Float,
        val suggestedThreshold: Float?,
        val message: String
    )

    data class SanitizeResult(
        val profiles: MutableList<PersonProfile>,
        val removedEmbeddings: Int,
        val removedPeople: Int
    )

    enum class Level {
        READY,
        WARNING,
        BLOCKED
    }

    companion object {
        const val EXPECTED_EMBEDDING_SIZE = RecognitionEngine.EXPECTED_EMBEDDING_SIZE
        const val RECOMMENDED_PEOPLE = 2
        const val RECOMMENDED_SAMPLES_PER_PERSON = EnrollmentAdvisor.RECOMMENDED_SAMPLES
        const val UNSTABLE_SAMPLE_DISTANCE = RecognitionEngine.DEFAULT_L2_THRESHOLD
        const val CONFUSED_PERSON_DISTANCE = RecognitionEngine.DEFAULT_L2_THRESHOLD
    }
}
