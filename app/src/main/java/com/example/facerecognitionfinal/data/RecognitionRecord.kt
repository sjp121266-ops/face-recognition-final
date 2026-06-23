package com.example.facerecognitionfinal.data

data class RecognitionRecord(
    val timestamp: Long,
    val name: String,
    val distance: Float,
    val confidence: Float,
    val status: String,
    val explanation: String
)
