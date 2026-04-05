package edu.ibu.termotabip.model

data class WoundPresenceResult(
    val hasWound: Boolean,
    val confidence: Float
)

data class WoundStageResult(
    val stage: Int,
    val confidence: Float
) {
    val stageName: String get() = when (stage) {
        0 -> "Evre I"
        1 -> "Evre II"
        2 -> "Evre III"
        3 -> "Evre IV"
        else -> "Bilinmiyor"
    }
    val stageDescription: String get() = when (stage) {
        0 -> "Solmayan eritem — deri bütünlüğü sağlam"
        1 -> "Kısmi doku kaybı — açık yüzeyel yara"
        2 -> "Tam doku kaybı — deri altı dokuya uzanan"
        3 -> "Tam doku kaybı — kas/tendon/kemik görünür"
        else -> ""
    }
}

data class WoundRiskResult(
    val riskLevel: Int,
    val confidence: Float
) {
    val riskName: String get() = when (riskLevel) {
        0 -> "Göreceli Risk (GR)"
        1 -> "Yüksek Risk (YR)"
        2 -> "Çok Yüksek Risk (ÇYR)"
        else -> "Bilinmiyor"
    }
}

data class WoundAnalysisResult(
    val presenceResult: WoundPresenceResult? = null,
    val stageResult: WoundStageResult? = null,
    val riskResult: WoundRiskResult? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
) {
    // GalleryScreen / ResultScreen uyumluluğu için
    val woundLevel: Int get() = riskResult?.riskLevel ?: stageResult?.stage ?: -1
    val confidence: Float get() = riskResult?.confidence ?: stageResult?.confidence
    ?: presenceResult?.confidence ?: 0f
}
