package edu.ibu.termotabip.ui.screens

import android.Manifest
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import edu.ibu.termotabip.model.WoundAnalysisUiState
import edu.ibu.termotabip.model.WoundRecommendation
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel
import java.io.File

val level1Recommendations = WoundRecommendation(
    riskLevel = "Göreceli Risk (GR)",
    confidenceRange = "%60 - %70",
    suggestions = listOf(
        "Cildin temiz ve hidrate tutun",
        "İnkontinans sonrası cildi temizleyin",
        "Cilt temizliğinde alkali sabunları ve temizleyicileri kullanmayın",
        "Bariyer ürünler kullanarak ciltte fazla nemden kaçının",
        "Çinko ve petrolatum içerikli bariyer kremler kullanın",
        "Düzenli pozisyon değişimi sağlayın",
        "90° yerine 30° lateral yan yatma pozisyonu tercih edin",
        "Yatak başını mümkün olduğunca düz tutun",
        "Destek yüzey kullanın",
        "Oksijenasyon, albümin, hemoglobin eksikliği, vücut ısısı artışı, dolaşım bozuklukları gibi faktörleri göz önünde bulundurun",
        "Yumuşak silikon ya da çok-katlı köpük yara örtüsünü düşünün"
    )
)

val level2Recommendations = WoundRecommendation(
    riskLevel = "Yüksek Risk (YR)",
    confidenceRange = "%70 - %85",
    suggestions = listOf(
        "Cildin temiz ve hidrate tutun",
        "İnkontinans sonrası cildi temizleyin",
        "Cilt temizliğinde alkali sabunları ve temizleyicileri kullanmayın",
        "Bariyer ürünler kullanarak ciltte fazla nemden kaçının",
        "İnkontinansı olan bireylerde özellikle çinko ve petrolatum içerikli bariyer kremler kullanın",
        "Düzenli pozisyon değişimi sağlayın. Saat yönünde sol lateral, sırt üstü, sağ lateral ve kontrendike değilse prone pozisyonunu verin",
        "Pozisyonda 90° lateral yan yatma pozisyonu yerine 30° lateral yan yatma pozisyonu tercih edin",
        "Yatak başını mümkün olabildiği kadar düz tutun",
        "Aktif bir destek yüzey kullanın",
        "Nütrisyonun beslenme uzmanlarıyla birlikte değerlendirilmesi (yetersiz beslenen yetişkin hastalar için yüksek kalorili, yüksek protein, arjinin, çinko ve antioksidan içeren besin takviyeleri veya enteral besin solüsyonlarının sağlanması)",
        "Günlük 30 - 35 kcal/kg enerji ve 1.25 - 1.5 g/kg/gün protein alımının sağlanmasını destekleyin",
        "Basınçtan korumada yumuşak silikon ya da çok-katlı köpük yara örtüsünü düşünün",
        "Oksijenasyon, albümin, hemoglobin eksikliği, vücut ısısı artışı, dolaşım bozukluğunun basınç yarası gelişimi üzerindeki etkisini göz önünde bulundurun"
    )
)

val level3Recommendations = WoundRecommendation(
    riskLevel = "Çok Yüksek Risk (ÇYR)",
    confidenceRange = "%85 - %100",
    suggestions = listOf(
        "Cildin temiz ve hidrate tutun",
        "İnkontinans sonrası cildi temizleyin",
        "Cilt temizliğinde alkali sabunları ve temizleyicileri kullanmayın",
        "Bariyer ürünler kullanın, ciltte fazla nemden kaçının",
        "İnkontinansı olan bireylerde özellikle çinko ve petrolatum içerikli bariyer kremleri kullanın",
        "Düzenli pozisyon değişimi sağlayın. Saat yönünde sol lateral, sırt üstü, sağ lateral ve kontrendike değilse prone pozisyonunu verin",
        "Aktif bir destek yüzey kullanın",
        "Pozisyonda 90° lateral yan yatma pozisyonu yerine 30° lateral yan yatma pozisyonu tercih edin",
        "Yatak başını mümkün olabildiği kadar düz tutun",
        "Basınçtan korumada yumuşak silikon ya da çok-katlı köpük yara örtüsünü düşünün",
        "Nütrisyonun beslenme uzmanlarıyla birlikte değerlendirilmesi (yetersiz beslenen yetişkin hastalar için yüksek kalorili, yüksek protein, arjinin, çinko ve antioksidan içeren besin takviyeleri veya enteral besin solüsyonlarının sağlanması)",
        "Günlük 30 - 35 kcal/kg enerji ve 1.25 - 1.5 g/kg/gün protein alımının sağlanmasını destekleyin",
        "Oksijenasyon, albümin, hemoglobin eksikliği, vücut ısısı artışı, dolaşım bozukluğunun basınç yarası gelişimi üzerindeki etkisini göz önünde bulundurun",
        "Basmakla solan eritem değerlendirmesinde parmak basısı yerine şeffaf disk yöntemini kullanın"
    )
)

@Composable
fun GalleryScreen(
    viewModel: WoundAnalysisViewModel = viewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                viewModel.analyzeWoundImage(bitmap, context)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Fotoğraf yüklenemedi: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(key1 = true) {
        if (!hasStoragePermission) {
            try {
                permissionLauncher.launch(READ_MEDIA_IMAGES)
            } catch (_: Exception) {
                permissionLauncher.launch(READ_EXTERNAL_STORAGE)
            }
        }
        viewModel.initModel(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.capturedImageUri == null) {

            val multiplePermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                // İzinler verildiyse hasStoragePermission'ı güncelle
                val allGranted = permissions.entries.all { it.value }
                hasStoragePermission = allGranted
                if (allGranted) {
                    Toast.makeText(context, "İzinler verildi", Toast.LENGTH_SHORT).show()
                }
            }
            // Galeriden seçim görünümü
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (hasStoragePermission) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Galeriden Seç"
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Galeriden Fotoğraf Seç")
                    }
                } else {
                    Text("Galeri erişim izni gerekli")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {

                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> { // API 33 - Android 13
                                multiplePermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO
                                    )
                                )
                            }
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> { // API 23+ Android 6+
                                multiplePermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    )
                                )
                            }

                            else -> {
                                multiplePermissionsLauncher.launch(arrayOf(READ_EXTERNAL_STORAGE))
                            }
                        }
                    }) {
                        Text("İzin Ver")
                    }
                }
            }
        } else {
            // Analiz sonucu görünümü
            AnalysisResultScreen(
                uiState = uiState,
                onReset = { galleryLauncher.launch("image/*") }
            )
        }

        // Yükleniyor göstergesi
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AnalysisResultScreen(
    uiState: WoundAnalysisUiState,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Başlık
        Text(
            text = "Yara Analiz Sonucu",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Seçilen fotoğraf
        uiState.capturedImageUri?.let { uri ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(File(uri)),
                    contentDescription = "Seçilen Fotoğraf",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Yeni seçim butonu
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Yeni Fotoğraf Seç"
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Yeni Fotoğraf Seç")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Analiz sonucu
        uiState.analysisResult?.let { result ->
            // Sonuç seviyesine göre uygun öneriyi seç
            val recommendation = when (result.woundLevel) {
                0 -> level1Recommendations // Düşük Risk
                1 -> level2Recommendations // Orta Risk
                2 -> level3Recommendations // Yüksek Risk
                else -> level3Recommendations // güvenli fallback
            }


            // Uygun öneri kartını göster
            RiskRecommendationCard(recommendation)
        }



    }
}

@Composable
fun RiskRecommendationCard(recommendation: WoundRecommendation, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Seviye: ${recommendation.riskLevel}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Güven Aralığı: ${recommendation.confidenceRange}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Öneriler:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                recommendation.suggestions.forEach {
                    item { Row(verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                        Spacer(modifier = Modifier.height(6.dp)) }
                }

            }
        }
    }
}
