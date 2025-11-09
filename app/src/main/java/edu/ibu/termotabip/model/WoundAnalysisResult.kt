package edu.ibu.termotabip.model

data class WoundAnalysisResult(
    val woundLevel: Int,
    val confidence: Float,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
