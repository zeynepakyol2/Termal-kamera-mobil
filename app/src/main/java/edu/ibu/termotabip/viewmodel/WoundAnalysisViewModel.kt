package edu.ibu.termotabip.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.ibu.termotabip.model.WoundAnalysisModel
import edu.ibu.termotabip.model.WoundAnalysisUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WoundAnalysisViewModel"

class WoundAnalysisViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WoundAnalysisUiState())
    val uiState: StateFlow<WoundAnalysisUiState> = _uiState.asStateFlow()

    private var model: WoundAnalysisModel? = null

    /** Modeli ilk kez (veya yoksa) başlatır */
    fun initModel(context: Context) {
        if (model != null) return
        viewModelScope.launch(Dispatchers.IO) {
            model = WoundAnalysisModel(context.applicationContext)
            val (v, e, r) = model!!.checkAvailability()
            Log.i(TAG, "Model init tamamlandı — var_yok:$v evre:$e risk:$r")
            /*if (!v || !e || !r) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = buildString {
                        append("Eksik model dosyası: ")
                        if (!v) append("var_yok_tespit.tflite ")
                        if (!e) append("evre_tespit.tflite ")
                        if (!r) append("risk_tespit.tflite")
                    }
                )
            }*/
        }
    }

    /** Eski checkModelAvailability çağrısıyla uyumluluk için (MainActivity'de kullanılıyor) */
    fun checkModelAvailability(context: Context): Boolean {
        val tmp = WoundAnalysisModel(context)
        return tmp.allModelsAvailable().also { tmp.close() }
    }

    /**
     * Bitmap al, 3-model pipeline'ı çalıştır.
     * [imageUriString] gösterilecek görsel URI'si (opsiyonel)
     */
    fun analyzeWoundImage(bitmap: Bitmap, context: Context, imageUriString: String = "") {
        val currentModel = model ?: WoundAnalysisModel(context.applicationContext).also { model = it }

        _uiState.value = _uiState.value.copy(
            isLoading      = true,
            errorMessage   = null,
            analysisResult = null,
            capturedImageUri = imageUriString.ifEmpty { _uiState.value.capturedImageUri }
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                currentModel.analyze(bitmap)
            }
            _uiState.value = _uiState.value.copy(
                isLoading      = false,
                analysisResult = result,
                errorMessage   = if (!result.isSuccess) result.errorMessage else null
            )
        }
    }

    /** Ana ekrana (HomeScreen) geri dön — state temizlenir */
    fun resetState() {
        _uiState.value = WoundAnalysisUiState()
    }

    override fun onCleared() {
        super.onCleared()
        model?.close()
    }
}
