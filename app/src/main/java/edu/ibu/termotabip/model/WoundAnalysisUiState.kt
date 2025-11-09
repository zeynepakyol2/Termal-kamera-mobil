package edu.ibu.termotabip.model

data class WoundAnalysisUiState(
    val isLoading: Boolean = false,
    val capturedImageUri: String? = null,
    val analysisResult: WoundAnalysisResult? = null,
    val errorMessage: String? = null
)