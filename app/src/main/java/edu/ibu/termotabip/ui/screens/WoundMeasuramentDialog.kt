package edu.ibu.termotabip.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.unit.sp
import edu.ibu.termotabip.history.AnalysisHistoryManager // JSON Manager'ı import ettik

@Composable
fun WoundMeasurementDialog(
    imageUri: String,
    recordId: String? = null,
    onDismiss: () -> Unit
) {
    val points = remember { mutableStateListOf<Offset>() }
    var calculatedResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // YENİ: JSON'a kaydetmek üzere sayısal değerleri tutacağımız değişkenler
    var finalArea by remember { mutableStateOf<Double?>(null) }
    var finalWidth by remember { mutableStateOf<Double?>(null) }
    var finalHeight by remember { mutableStateOf<Double?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {


                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                    Text("Yara Alanı Ölçümü", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { points.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Temizle")
                    }
                }


                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        points.forEach { point -> drawCircle(color = Color.Red, radius = 12f, center = point) }
                        for (i in 0 until points.size - 1) {
                            drawLine(color = Color.Red, start = points[i], end = points[i + 1], strokeWidth = 6f)
                        }
                        if (points.size > 2) {
                            drawLine(color = Color.Red, start = points.last(), end = points.first(), strokeWidth = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
                        }
                    }
                }


                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (points.isEmpty()) "Yara alanını sınırlarından seçin" else "${points.size} nokta seçildi",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            try {
                                calculatedResult = "Hesaplanıyor... Lütfen bekleyin."

                                val bitmap = if (imageUri.startsWith("content://")) {
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

                                        // Sayısal değerleri bul
                                        val realAreaCm2 = realAreaMm2 / 100.0
                                        val minX = points.minOf { it.x }
                                        val maxX = points.maxOf { it.x }
                                        val minY = points.minOf { it.y }
                                        val maxY = points.maxOf { it.y }
                                        val realWidthCm = ((maxX - minX) * scaleFactor * mmToPixelRatio) / 10.0
                                        val realHeightCm = ((maxY - minY) * scaleFactor * mmToPixelRatio) / 10.0

                                        // YENİ: JSON'a yazmak için durumu kaydet
                                        finalArea = realAreaCm2
                                        finalWidth = realWidthCm
                                        finalHeight = realHeightCm

                                        calculatedResult = String.format("Yaranın Gerçek Alanı:\n%.2f cm²\n\nTahmini Genişlik: %.1f cm\nTahmini Uzunluk: %.1f cm", realAreaCm2, realWidthCm, realHeightCm)
                                    } else {
                                        calculatedResult = "HATA: Görselde ölçüm cetveli tespit edilemedi. Lütfen mavi referans cetvelinin net göründüğü bir fotoğraf kullanın."
                                    }
                                } else {
                                    calculatedResult = "HATA: Resim okunamadı."
                                }
                            } catch (e: Exception) {
                                calculatedResult = "HATA ÇIKTI: İşlem sırasında beklenmeyen bir hata oluştu."
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = points.size > 2
                    ) {
                        Text("Alanı Hesapla", fontWeight = FontWeight.Bold)
                    }
                }


                calculatedResult?.let { resultText ->
                    val isError = resultText.startsWith("HATA")

                    Dialog(onDismissRequest = { calculatedResult = null }) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = "Durum İkonu",
                                    tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isError) "Ölçüm Başarısız" else "Ölçüm Tamamlandı!",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = resultText.replace("HATA: ", ""),
                                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        // YENİ: JSON'A KAYDETME İŞLEMİ BURADA YAPILIYOR!
                                        if (!isError && recordId != null && finalArea != null) {
                                            AnalysisHistoryManager.updateMeasurement(
                                                context = context,
                                                id = recordId,
                                                area = finalArea!!,
                                                width = finalWidth!!,
                                                height = finalHeight!!
                                            )
                                        }
                                        calculatedResult = null
                                        onDismiss() // Ölçüm ekranını kapatıp geçmişe döndürür
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(text = if (isError) "Kapat" else "Kaydet ve Kapat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculatePolygonAreaInPixels(points: List<androidx.compose.ui.geometry.Offset>): Float {
    if (points.size < 3) return 0f
    var area = 0f
    var j = points.size - 1
    for (i in points.indices) {
        area += (points[j].x + points[i].x) * (points[j].y - points[i].y)
        j = i
    }
    return Math.abs(area / 2f)
}