package com.example.facerecognitionfinal.data

class PersonNameValidator(
    private val maxLength: Int = MAX_NAME_LENGTH
) {

    fun validate(rawName: String): Result {
        val normalized = normalize(rawName)
        return when {
            normalized.isBlank() -> Result.Invalid("状态：请先输入要录入的姓名。")
            normalized.length > maxLength -> Result.Invalid("状态：姓名过长，请控制在 ${maxLength} 个字符以内。")
            normalized in RESERVED_NAMES -> Result.Invalid("状态：“$normalized”是系统保留名称，请换一个真实姓名。")
            else -> Result.Valid(normalized)
        }
    }

    private fun normalize(rawName: String): String {
        return rawName.trim().replace(WHITESPACE_REGEX, " ")
    }

    sealed class Result {
        data class Valid(val name: String) : Result()
        data class Invalid(val message: String) : Result()
    }

    companion object {
        const val MAX_NAME_LENGTH = 12
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val RESERVED_NAMES = setOf(
            RecognitionStatus.UNKNOWN_PERSON,
            RecognitionStatus.LIVE_DEMO_PERSON,
            RecognitionStatus.LIVE_DEMO,
            RecognitionStatus.LOCAL_SUCCESS,
            RecognitionStatus.LOCAL_UNKNOWN,
            "null"
        )
    }
}
