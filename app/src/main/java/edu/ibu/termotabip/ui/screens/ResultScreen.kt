package edu.ibu.termotabip.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import edu.ibu.termotabip.auth.User
import edu.ibu.termotabip.history.AnalysisHistoryManager
import edu.ibu.termotabip.model.*
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlin.math.roundToInt

// ─────────────── Risk önerileri ───────────────────────────────────────────
private val riskRecommendations = mapOf(
    0 to WoundRecommendation(
        riskLevel = "Göreceli Risk (GR)", confidenceRange = "%60 - %70",
        suggestions = listOf(
            "Cildin temiz ve hidrate tutun",
            "İnkontinans sonrası cildi temizleyin",
            "Alkali sabun/temizleyicilerden kaçının",
            "Bariyer ürünler kullanarak fazla nemden kaçının",
            "Çinko ve petrolatum içerikli bariyer kremler kullanın",
            "Düzenli pozisyon değişimi — 90° yerine 30° lateral",
            "Yatak başını mümkün olduğunca düz tutun",
            "Destek yüzey kullanın",
            "Yumuşak silikon ya da çok-katlı köpük yara örtüsünü düşünün"
        )
    ),
    1 to WoundRecommendation(
        riskLevel = "Yüksek Risk (YR)", confidenceRange = "%70 - %85",
        suggestions = listOf(
            "Cildin temiz ve hidrate tutun",
            "İnkontinans sonrası cildi temizleyin",
            "Alkali sabun/temizleyicilerden kaçının",
            "Bariyer ürünler kullanarak fazla nemden kaçının",
            "Düzenli pozisyon değişimi (sol lateral, sırt üstü, sağ lateral, prone)",
            "Aktif destek yüzey kullanın",
            "Nütrisyon: yüksek kalori, yüksek protein, arjinin, çinko, antioksidan",
            "Günlük 30-35 kcal/kg enerji ve 1.25-1.5 g/kg/gün protein"
        )
    ),
    2 to WoundRecommendation(
        riskLevel = "Çok Yüksek Risk (ÇYR)", confidenceRange = "%85 - %100",
        suggestions = listOf(
            "Cildin temiz ve hidrate tutun",
            "İnkontinans sonrası cildi temizleyin",
            "Alkali sabun/temizleyicilerden kaçının",
            "Aktif destek yüzey kullanın",
            "Düzenli pozisyon değişimi (30° lateral)",
            "Basmakla solan eritem: şeffaf disk yöntemi kullanın",
            "Nütrisyon beslenme uzmanıyla değerlendirilmeli",
            "Günlük 30-35 kcal/kg enerji ve 1.25-1.5 g/kg/gün protein"
        )
    )
)

private val stageRecommendations = mapOf(
    0 to WoundRecommendation(
        riskLevel = "Evre I — Solmayan Eritem", confidenceRange = "Deri bütünlüğü sağlam",
        suggestions = listOf(
            "Bası uygulanan bölgeyi 2 saatte bir değerlendirin",
            "Yeniden konumlandırma sıklığını artırın",
            "Köpük veya silikon yara örtüsü ile koruyun",
            "Nemlendiricili bariyer krem uygulayın",
            "Protein ve vitamin takviyesi ekleyin"
        )
    ),
    1 to WoundRecommendation(
        riskLevel = "Evre II — Kısmi Doku Kaybı", confidenceRange = "Açık yüzeyel yara / seröz içerikli blister",
        suggestions = listOf(
            "Yarayı nemli ortamda tutun (hidrokoloid veya köpük örtü)",
            "Bası azaltılmalı — hava veya sıvı dolu yüzey kullanın",
            "Enfeksiyon belirtilerini (kızarıklık, ısı, akıntı) izleyin",
            "Yumuşak temizleme — SF veya yaraya uygun solüsyon",
            "Nütrisyon değerlendirmesi"
        )
    ),
    2 to WoundRecommendation(
        riskLevel = "Evre III — Tam Doku Kaybı", confidenceRange = "Deri altı dokuya uzanan",
        suggestions = listOf(
            "Yara bakım ekibiyle konsültasyon",
            "Debridman gerekebilir (hekim kararıyla)",
            "Derin yarayı boşluk bırakmadan doldurun",
            "Enfeksiyon kontrolü — topikal veya sistemik antibiyotik",
            "Aktif yüzey değiştirme — sürekli düşük basınçlı destek"
        )
    ),
    3 to WoundRecommendation(
        riskLevel = "Evre IV — Tam Doku Kaybı + Yapı Hasarı", confidenceRange = "Kas, tendon veya kemik görünür",
        suggestions = listOf(
            "ACİL yara bakım uzmanı veya plastik cerrah konsültasyonu",
            "Cerrahi debridman/onarım değerlendirilmeli",
            "Negatif basınçlı yara tedavisi (VAC) düşünün",
            "Enfeksiyon riski yüksek — yoğun takip",
            "Nutrisyon desteği zorunlu"
        )
    )
)

// ─────────────── Ana composable ───────────────────────────────────────────
@Composable
fun ResultScreen(
    viewModel: WoundAnalysisViewModel,
    currentUser: User? = null,
    onNewAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val result  = uiState.analysisResult

    var showReportDialog by remember { mutableStateOf(false) }
    var savedToHistory   by remember { mutableStateOf(false) }
    var showMeasurementDialog by remember { mutableStateOf(false) }
    var savedRecordId by remember { mutableStateOf<String?>(null) }

    // Analiz bitince otomatik geçmişe kaydet (sadece bir kez)
    LaunchedEffect(result) {
        if (result != null && result.isSuccess && !savedToHistory) {
            val record=AnalysisHistoryManager.save(
                context   = context,
                result    = result,
                imageUri  = uiState.capturedImageUri ?: "",
                username  = currentUser?.username ?: ""
            )
            savedRecordId = record.id
            savedToHistory = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Analiz Sonucu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Fotoğraf
            uiState.capturedImageUri?.let { uri ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                if (uri.startsWith("content://")) uri else File(uri)
                            ),
                            contentDescription = "Analiz edilen fotoğraf",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (result == null) {
                item {
                    Text("Sonuç bulunamadı.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
            } else if (!result.isSuccess) {
                item { ErrorCard(result.errorMessage ?: "Bilinmeyen hata") }
            } else {
                result.presenceResult?.let { item { PresenceResultCard(it) } }
                result.stageResult?.let { stage ->
                    val rec = stageRecommendations[stage.stage] ?: stageRecommendations[0]!!
                    item { RiskRecommendationCard(rec, confidence = stage.confidence) }
                }
                result.riskResult?.let { risk ->
                    val rec = riskRecommendations[risk.riskLevel] ?: riskRecommendations[0]!!
                    item { RiskRecommendationCard(rec, confidence = risk.confidence) }
                }
            }

            // Geçmişe kaydedildi bildirimi
            if (savedToHistory) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Geçmişe kaydedildi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (result != null && result.isSuccess && uiState.capturedImageUri != null) {
                item {
                    Button(
                        onClick = { showMeasurementDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Yara Boyutunu Ölç", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // PDF rapor butonu
            item {
                OutlinedButton(
                    onClick  = { showReportDialog = true },
                    enabled  = result != null && result.isSuccess,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PDF Rapor Oluştur") }
            }



            // Yeni analiz butonu
            item {
                Button(
                    onClick = { viewModel.resetState(); onNewAnalysis() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni Analiz")
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Rapor diyaloğu
        if (showReportDialog && result != null) {
            ReportDialog(
                result       = result,
                thermalBitmap = null,
                onDismiss    = { showReportDialog = false }
            )
        }

        if (showMeasurementDialog && uiState.capturedImageUri != null) {
            WoundMeasurementDialog(
                imageUri = uiState.capturedImageUri!!,
                recordId = savedRecordId,
                onDismiss = { showMeasurementDialog = false }
            )
        }
    }
}

// ─────────────── Alt composable'lar ──────────────────────────────────────

@Composable
private fun PresenceResultCard(presence: WoundPresenceResult) {
    val isWound   = presence.hasWound
    val color     = if (isWound) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val textColor = if (isWound) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isWound) "Yara Tespit Edildi" else "Yara Tespit Edilmedi",
                    fontWeight = FontWeight.Bold, color = textColor,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (isWound) "Evre analizi yapıldı" else "Risk değerlendirmesi yapıldı",
                    style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f)
                )
            }
            Text("${(presence.confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun RiskRecommendationCard(
    recommendation: WoundRecommendation,
    confidence: Float = 0f,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(recommendation.riskLevel, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(recommendation.confidenceRange, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (confidence > 0f) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)) {
                        Text("${(confidence * 100).roundToInt()}%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Öneriler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            recommendation.suggestions.forEach { suggestion ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("• ", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Hata: $message", modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
