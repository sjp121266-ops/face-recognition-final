package com.example.facerecognitionfinal.data

data class PersonProfile(
    val name: String,
    val embeddings: MutableList<FloatArray> = mutableListOf(),
    val group: String = "默认"
)
