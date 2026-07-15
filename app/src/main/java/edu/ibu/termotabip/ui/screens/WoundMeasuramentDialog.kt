package edu.ibu.termotabip.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import edu.ibu.termotabip.history.AnalysisHistoryManager
import java.io.File

@Composable
fun WoundMeasurementDialog(
    imageUri: String,
    recordId: String? = null,
    onDismiss: () -> Unit
) {
    val points = remember { mutableStateListOf<Offset>() }
    var calculatedResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var finalArea by remember { mutableStateOf<Double?>(null) }
    var finalWidth by remember { mutableStateOf<Double?>(null) }
    var finalHeight by remember { mutableStateOf<Double?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally // Tüm içeriği yatayda ortalar
            ) {
                // --- ÜST BAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                    Text(
                        text = "Yara Alanı Ölçümü",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { points.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = MaterialTheme.colorScheme.error)
                    }
                }

                // --- GÖRSEL VE ÇİZİM ALANI ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            if (imageUri.startsWith("content://")) imageUri else File(imageUri)
                        ),
                        contentDescription = "Yara Fotoğrafı",
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset -> points.add(offset) }
                            }
                    ) {
                        points.forEach { point ->
                            drawCircle(color = Color.Red, radius = 12f, center = point)
                        }
                        for (i in 0 until points.size - 1) {
                            drawLine(color = Color.Red, start = points[i], end = points[i + 1], strokeWidth = 6f)
                        }
                        if (points.size > 2) {
                            drawLine(
                                color = Color.Red,
                                start = points.last(),
                                end = points.first(),
                                strokeWidth = 6f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            )
                        }
                    }
                }

                // --- SİMETRİK YÜZEN ALT PANEL ---
                Card(
                    modifier = Modifier
                        .padding(horizontal = 24.dp) // Sağ ve sol boşlukları eşitlemek için
                        .padding(top = 20.dp, bottom = 40.dp) // Alt ve üst boşluk
                        .fillMaxWidth(), // Kartın kendisi dış paddinglerden sonra alanı doldurur
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally // Butonu ve metni kart içinde ortalar
                    ) {
                        Text(
                            text = if (points.isEmpty()) "Sınırları belirlemek için dokunun" else "${points.size} nokta seçildi",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    calculatedResult = "Hesaplanıyor..."
                                    val bitmap = if (imageUri.startsWith("content://")) {
                                        @Suppress("DEPRECATION")
                                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, android.net.Uri.parse(imageUri))
                                    } else {
                                        android.graphics.BitmapFactory.decodeFile(File(imageUri).absolutePath)
                                    }

                                    if (bitmap != null) {
                                        val bmp32 = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                                        val mmToPixelRatio = edu.ibu.termotabip.utils.RulerUtils.calculatePixelToMmRatioFromNumbers(bmp32)

                                        if (mmToPixelRatio > 0) {
                                            val displayedScreenWidth = 1080.0
                                            val displayedScreenHeight = 720.0
                                            val scaleX = bitmap.width.toDouble() / displayedScreenWidth
                                            val scaleY = bitmap.height.toDouble() / displayedScreenHeight
                                            val scaleFactor = Math.min(scaleX, scaleY)

                                            val pixelAreaOnScreen = calculatePolygonAreaInPixels(points)
                                            val pixelAreaOnOriginalBitmap = pixelAreaOnScreen * (scaleFactor * scaleFactor)
                                            val realAreaMm2 = pixelAreaOnOriginalBitmap * (mmToPixelRatio * mmToPixelRatio)

                                            finalArea = realAreaMm2 / 100.0
                                            val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
                                            val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
                                            finalWidth = ((maxX - minX) * scaleFactor * mmToPixelRatio) / 10.0
                                            finalHeight = ((maxY - minY) * scaleFactor * mmToPixelRatio) / 10.0

                                            calculatedResult = String.format("Gerçek Alan: %.2f cm²\nBoyutlar: %.1f x %.1f cm", finalArea, finalWidth, finalHeight)
                                        } else {
                                            calculatedResult = "HATA: Referans cetveli tespit edilemedi."
                                        }
                                    }
                                } catch (e: Exception) {
                                    calculatedResult = "HATA: İşlem başarısız."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.9f) // Kartın genişliğinin %90'ını kaplar, böylece içten de boşluklu durur
                                .height(54.dp),
                            enabled = points.size > 2,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Alanı Hesapla", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // --- SONUÇ DİALOGU ---
            calculatedResult?.let { resultText ->
                val isError = resultText.startsWith("HATA")
                Dialog(onDismissRequest = { calculatedResult = null }) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(resultText.replace("HATA: ", ""), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    if (!isError && recordId != null && finalArea != null) {
                                        AnalysisHistoryManager.updateMeasurement(context, recordId, finalArea!!, finalWidth!!, finalHeight!!)
                                    }
                                    calculatedResult = null
                                    if (!isError) onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(if (isError) "Kapat" else "Kaydet ve Kapat")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculatePolygonAreaInPixels(points: List<Offset>): Float {
    if (points.size < 3) return 0f
    var area = 0f
    var j = points.size - 1
    for (i in points.indices) {
        area += (points[j].x + points[i].x) * (points[j].y - points[i].y)
        j = i
    }
    return Math.abs(area / 2f)
}