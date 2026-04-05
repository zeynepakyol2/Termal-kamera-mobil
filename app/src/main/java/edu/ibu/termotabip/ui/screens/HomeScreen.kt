package edu.ibu.termotabip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.ibu.termotabip.auth.User
import edu.ibu.termotabip.history.AnalysisHistoryManager
import edu.ibu.termotabip.ui.theme.*
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel

@Composable
fun HomeScreen(
    viewModel: WoundAnalysisViewModel,
    currentUser: User?,
    onGalleryClick: () -> Unit,
    onThermalCameraClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val historyCount = remember {
        if (currentUser != null) AnalysisHistoryManager.loadForUser(context, currentUser.username).size
        else AnalysisHistoryManager.loadAll(context).size
    }

    LaunchedEffect(Unit) { viewModel.initModel(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep)
            .drawBehind {
                val c = Color(0xFF1A3050).copy(alpha = 0.3f)
                var x = 0f; while (x <= size.width) { drawLine(c, Offset(x,0f), Offset(x,size.height), 0.5f); x += 40f }
                var y = 0f; while (y <= size.height) { drawLine(c, Offset(0f,y), Offset(size.width,y), 0.5f); y += 40f }
            }
    ) {
        // Turuncu parıltı sağ üst
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = 100.dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(listOf(ThermalOrange.copy(0.07f), Color.Transparent)),
                    CircleShape
                )
                .align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Üst bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentUser?.let { user ->
                    Column {
                        Text("Hoş geldin", fontSize = 12.sp, color = TextSecondary)
                        Text(user.fullName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ThermalOrange.copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(user.role, fontSize = 10.sp, color = ThermalOrange,
                                fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BadgedBox(badge = {
                        if (historyCount > 0) Badge(
                            containerColor = ThermalOrange,
                            contentColor   = NavyDeep
                        ) { Text(historyCount.toString(), fontSize = 9.sp) }
                    }) {
                        IconButton(onClick = onHistoryClick) {
                            Icon(Icons.Default.List, contentDescription = "Geçmiş",
                                tint = TextSecondary)
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Çıkış",
                            tint = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.weight(0.8f))

            // ── Logo & Başlık ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        Brush.radialGradient(listOf(ThermalOrange.copy(0.2f), NavySurface)),
                        CircleShape
                    )
                    .drawBehind {
                        drawCircle(ThermalOrange.copy(0.3f), size.minDimension/2,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🌡", fontSize = 38.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text("TermoInjury", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = TextPrimary, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(4.dp))
            Text("TERMAL YARA ANALİZ SİSTEMİ", fontSize = 10.sp,
                color = ThermalOrange, letterSpacing = 2.sp)

            Spacer(Modifier.weight(1f))

            // ── Ana seçenekler ────────────────────────────────────────────
            ClinicalActionCard(
                emoji    = "🖼",
                title    = "Galeriden Analiz",
                subtitle = "Telefondaki fotoğrafı analiz et",
                accent   = ThermalOrange,
                onClick  = onGalleryClick
            )
            Spacer(Modifier.height(12.dp))
            ClinicalActionCard(
                emoji    = "📸",
                title    = "Termal Kamera",
                subtitle = "HIKMICRO Mini3 ile görüntü al",
                accent   = ClinicalTeal,
                onClick  = onThermalCameraClick
            )
            Spacer(Modifier.height(12.dp))

            // Geçmiş butonu — daha sade
            OutlinedButton(
                onClick  = onHistoryClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                )
            ) {
                Icon(Icons.Default.List, contentDescription = null,
                    tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analiz Geçmişi", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                if (historyCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ThermalOrange.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("$historyCount", color = ThermalOrange, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Model hata mesajı
            uiState.errorMessage?.let { err ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClinicalRed.copy(0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", fontSize = 14.sp)
                    Text(err, color = ClinicalRed, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.weight(0.5f))
        }
    }

    // Çıkış diyaloğu
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor   = NavySurface,
            title  = { Text("Çıkış Yap", color = TextPrimary) },
            text   = { Text("Oturumu kapatmak istediğinize emin misiniz?",
                color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors  = ButtonDefaults.buttonColors(containerColor = ClinicalRed)
                ) { Text("Çıkış", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }
}

// ── Klinik aksiyon kartı ─────────────────────────────────────────────────
@Composable
private fun ClinicalActionCard(
    emoji: String, title: String, subtitle: String,
    accent: Color, onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(88.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = NavySurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Sol aksan çizgisi
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(accent)
            )
            // Sağ arka plan parıltısı
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(100.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(0.05f)))
                    )
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary)
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
                Text("→", fontSize = 18.sp, color = accent.copy(0.7f))
            }
        }
    }
}