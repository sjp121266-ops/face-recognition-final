package com.example.facerecognitionfinal.data

/**
 * A single attendance check-in record.
 */
data class AttendanceRecord(
    val name: String,
    val timestamp: String,       // "yyyy-MM-dd HH:mm:ss"
    val date: String,            // "yyyy-MM-dd" for grouping
    val confidence: Float,       // 0..100
    val method: String = "人脸识别"
)
