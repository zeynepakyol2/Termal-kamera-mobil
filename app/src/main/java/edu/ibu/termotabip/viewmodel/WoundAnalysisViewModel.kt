package edu.ibu.termotabip.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.ibu.termotabip.model.WoundAnalysisModel
import edu.ibu.termotabip.model.WoundAnalysisUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class WoundAnalysisViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WoundAnalysisUiState())
    val uiState: StateFlow<WoundAnalysisUiState> = _uiState.asStateFlow()
    
    private var woundAnalysisModel: WoundAnalysisModel? = null
    
    fun initModel(context: Context) {
        woundAnalysisModel = WoundAnalysisModel(context)
    }
    
    fun analyzeWoundImage(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Resmi kaydet
                val imageFile = saveImageToInternalStorage(bitmap, context)
                
                // Modeli başlat (eğer başlatılmadıysa)
                if (woundAnalysisModel == null) {
                    initModel(context)
                }
                
                // Analiz et
                val result = woundAnalysisModel?.analyzeWound(bitmap)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    capturedImageUri = imageFile.absolutePath,
                    analysisResult = result,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Analiz sırasında hata oluştu: ${e.message}"
                )
            }
        }
    }
    
    private fun saveImageToInternalStorage(bitmap: Bitmap, context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "WOUND_$timeStamp.jpg"
        val file = File(context.filesDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
        
        return file
    }
    
    override fun onCleared() {
        super.onCleared()
        woundAnalysisModel?.close()
    }
    
    fun checkModelAvailability(context: Context): Boolean {
        if (woundAnalysisModel == null) {
            woundAnalysisModel = WoundAnalysisModel(context)
        }
        return woundAnalysisModel?.isModelAvailable(context) == true
    }
} 