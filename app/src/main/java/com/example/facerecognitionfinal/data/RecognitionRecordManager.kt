package com.example.facerecognitionfinal.data

class RecognitionRecordManager(
    private val maxRecords: Int = FaceStore.MAX_RECORDS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun add(
        records: MutableList<RecognitionRecord>,
        name: String,
        distance: Float,
        confidence: Float,
        status: String,
        explanation: String
    ): RecognitionRecord {
        val record = RecognitionRecord(
            timestamp = clock(),
            name = name,
            distance = distance,
            confidence = confidence,
            status = status,
            explanation = explanation
        )
        records.add(0, record)
        trim(records)
        return record
    }

    fun trim(records: MutableList<RecognitionRecord>) {
        while (records.size > maxRecords) {
            records.removeAt(records.lastIndex)
        }
    }
}
