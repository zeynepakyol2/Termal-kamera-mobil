package edu.ibu.termotabip.model

data class WoundRecommendation(
    val riskLevel: String,
    val confidenceRange: String,
    val suggestions: List<String>
)
