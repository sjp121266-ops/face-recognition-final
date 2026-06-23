package com.example.facerecognitionfinal.data

class RecordRepository(
    initialRecords: List<RecognitionRecord>,
    private val saveRecords: (List<RecognitionRecord>) -> Unit,
    private val clearRecords: () -> Unit,
    private val recordManager: RecognitionRecordManager = RecognitionRecordManager()
) {

    val records: MutableList<RecognitionRecord> = initialRecords.toMutableList()

    fun add(
        name: String,
        distance: Float,
        confidence: Float,
        status: String,
        explanation: String
    ): RecognitionRecord {
        val record = recordManager.add(records, name, distance, confidence, status, explanation)
        saveRecords(records)
        return record
    }

    fun clear() {
        records.clear()
        clearRecords()
    }
}
