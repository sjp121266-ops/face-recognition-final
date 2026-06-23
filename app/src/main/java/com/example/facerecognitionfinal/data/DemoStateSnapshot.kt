package com.example.facerecognitionfinal.data

data class DemoStateSnapshot(
    val profiles: List<PersonProfile>,
    val records: List<RecognitionRecord>,
    val isCloudMode: Boolean,
    val isLiveRecognitionEnabled: Boolean
) {
    val hasProfiles: Boolean = profiles.isNotEmpty()
    val hasRecords: Boolean = records.isNotEmpty()
    val profileCount: Int = profiles.size
    val recordCount: Int = records.size
    val totalEmbeddings: Int = profiles.sumOf { it.embeddings.size }
}
