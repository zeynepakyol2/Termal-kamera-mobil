package edu.ibu.termotabip.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
/**
 * Galeriden fotoğraf seçip analiz eder.
 * Analiz tamamlandığında [onAnalysisDone] çağrılır → MainActivity ResultScreen'e yönlendirir.
 */
@Composable
fun GalleryScreen(
    viewModel: WoundAnalysisViewModel,
    onAnalysisDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Analiz bittiğinde sonuç ekranına geç
    LaunchedEffect(uiState.analysisResult) {
        if (uiState.analysisResult != null && !uiState.isLoading) {
            onAnalysisDone()
        }
    }

    // Depolama izni durumu
    var hasPermission by remember {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
                viewModel.analyzeWoundImage(bitmap, context, it.toString())
            } catch (e: Exception) {
                Toast.makeText(context, "Fotoğraf yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // İlk açılışta izin iste
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
            permissionLauncher.launch(perm)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasPermission) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Galeriden Fotoğraf Seç")
                }
            } else {
                Text(
                    "Galeri erişim izni gerekli",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_IMAGES
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    permissionLauncher.launch(perm)
                }) { Text("İzin Ver") }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Geri") }
        }

        // Yükleniyor overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
