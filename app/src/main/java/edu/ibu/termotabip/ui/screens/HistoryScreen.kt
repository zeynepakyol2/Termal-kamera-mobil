package edu.ibu.termotabip.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import edu.ibu.termotabip.auth.User
import edu.ibu.termotabip.history.AnalysisHistoryManager
import edu.ibu.termotabip.history.AnalysisRecord
import edu.ibu.termotabip.ui.theme.*
import java.io.File
import androidx.compose.material.icons.filled.CheckCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    currentUser: User?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var records by remember {
        mutableStateOf(
            if (currentUser != null)
                AnalysisHistoryManager.loadForUser(context, currentUser.username)
            else
                AnalysisHistoryManager.loadAll(context)
        )
    }
    val stats   = remember(records) { AnalysisHistoryManager.stats(context, currentUser?.username) }
    var deleteTarget by remember { mutableStateOf<AnalysisRecord?>(null) }

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
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Analiz Geçmişi", fontWeight = FontWeight.Bold,
                                color = TextPrimary, fontSize = 18.sp)
                            Text(
                                if (currentUser != null) currentUser.fullName
                                else "Tüm kayıtlar",
                                fontSize = 11.sp, color = TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri",
                                tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NavyBase,
                        titleContentColor = TextPrimary
                    )
                )
            }
        ) { padding ->
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(NavySurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("📋", fontSize = 36.sp) }
                        Spacer(Modifier.height(16.dp))
                        Text("Kayıt bulunamadı", fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Analiz yaptıktan sonra geçmişiniz burada görünecek",
                            fontSize = 13.sp, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── İstatistik şeridi ─────────────────────────────────
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                StatPill("Toplam", stats.total.toString(), ThermalOrange)
                            }
                            item {
                                StatPill("Yara Var", stats.woundCount.toString(), ClinicalRed)
                            }
                            item {
                                StatPill("Yara Yok", stats.noWoundCount.toString(), ClinicalTeal)
                            }
                            stats.stageBreakdown.entries.sortedBy { it.key }.forEach { (stage, count) ->
                                item {
                                    StatPill("Evre ${stage + 1}", count.toString(), ThermalAmber)
                                }
                            }
                            stats.riskBreakdown.entries.sortedBy { it.key }.forEach { (level, count) ->
                                item {
                                    val name = when(level) { 0 -> "GR"; 1 -> "YR"; else -> "ÇYR" }
                                    StatPill(name, count.toString(), ClinicalBlue)
                                }
                            }
                        }
                    }

                    // ── Kayıt listesi ─────────────────────────────────────
                    items(records, key = { it.id }) { record ->
                        HistoryRecordCard(
                            record   = record,
                            onDelete = { deleteTarget = record }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Silme onay diyaloğu
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = NavySurface,
            title  = { Text("Kaydı Sil", color = TextPrimary) },
            text   = {
                Text("${target.date} tarihli analiz silinecek. Emin misiniz?",
                    color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        AnalysisHistoryManager.delete(context, target.id)
                        records = if (currentUser != null)
                            AnalysisHistoryManager.loadForUser(context, currentUser.username)
                        else AnalysisHistoryManager.loadAll(context)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalRed)
                ) { Text("Sil", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }
}

// ── İstatistik pill ───────────────────────────────────────────────────────
@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Kayıt kartı ───────────────────────────────────────────────────────────
@Composable

private fun HistoryRecordCard(record: AnalysisRecord, onDelete: () -> Unit) {
    val accent = when {
        record.hasWound && (record.stage ?: 0) >= 2 -> ClinicalRed
        record.hasWound                              -> ThermalAmber
        (record.riskLevel ?: 0) >= 2                -> ThermalAmber
        else                                         -> ClinicalTeal
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = NavySurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(3.dp)
                    // Alan ölçümü varsa kutu biraz daha uzun olur, yoksa kısa kalır
                    .height(if (record.woundArea != null) 100.dp else 88.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(accent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (record.imageUri.isNotBlank()) {
                    val model = if (record.imageUri.startsWith("content://")) record.imageUri
                    else File(record.imageUri)
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyElevated),
                        contentAlignment = Alignment.Center
                    ) { Text("📷", fontSize = 22.sp) }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(record.summary, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("%${record.confidencePct}", fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
                    }

                    Text(record.date, fontSize = 12.sp, color = TextSecondary)

                    // Alan ölçümü varsa gösterir
                    if (record.woundArea != null && record.woundArea!! > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ClinicalTeal, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("Alan: %.2f cm² (%.1f x %.1f cm)", record.woundArea, record.woundWidth, record.woundHeight),
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (record.username.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(4.dp).background(TextHint, CircleShape))
                            Text(record.username, fontSize = 11.sp, color = TextHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = TextHint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}