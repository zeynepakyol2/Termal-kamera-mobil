package edu.ibu.termotabip

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import edu.ibu.termotabip.ui.screens.GalleryScreen
import edu.ibu.termotabip.ui.theme.TermoTabipTheme
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = WoundAnalysisViewModel()
        val modelAvailable = viewModel.checkModelAvailability(this)
        Log.d("MainActivity", "TFLite model durumu: ${if (modelAvailable) "Mevcut" else "Bulunamadı"}")
        setContent {
            TermoTabipTheme {
                val viewModel: WoundAnalysisViewModel = viewModel
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GalleryScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}