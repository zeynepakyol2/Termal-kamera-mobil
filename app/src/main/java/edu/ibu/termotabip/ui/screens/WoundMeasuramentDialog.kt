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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import edu.ibu.termotabip.history.AnalysisHistoryManager
import java.io.File
import androidx.compose.material.icons.filled.Undo

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

    // Kullanıcının ekranda dokunduğu Canvas'ın GERÇEK piksel boyutu.
    // Sabit 1080x720 varsayımı yerine bunu kullanıyoruz; farklı ekran/cihazlarda
    // ölçüm hatası yapmamak için şart.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
                horizontalAlignment = Alignment.CenterHorizontally
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
                        .onSizeChanged { canvasSize = it }
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
                        .padding(horizontal = 24.dp)
                        .padding(top = 20.dp, bottom = 40.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (points.isEmpty()) "Sınırları belirlemek için dokunun" else "${points.size} nokta seçildi",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // ── SON HAMLEYİ GERİ AL ──
                            OutlinedButton(
                                onClick = {
                                    if (points.isNotEmpty()) {
                                        points.removeAt(points.lastIndex)
                                        calculatedResult = null
                                    }
                                },
                                enabled = points.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = "Son hamleyi geri al"
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "Geri Al",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // ── ALANI HESAPLA ──
                            Button(
                                onClick = {
                                    try {
                                        calculatedResult = "Hesaplanıyor..."

                                        val bitmap = if (imageUri.startsWith("content://")) {
                                            @Suppress("DEPRECATION")
                                            android.provider.MediaStore.Images.Media.getBitmap(
                                                context.contentResolver,
                                                android.net.Uri.parse(imageUri)
                                            )
                                        } else {
                                            android.graphics.BitmapFactory.decodeFile(
                                                File(imageUri).absolutePath
                                            )
                                        }

                                        if (
                                            bitmap != null &&
                                            canvasSize.width > 0 &&
                                            canvasSize.height > 0
                                        ) {

                                            val bmp32 = bitmap.copy(
                                                android.graphics.Bitmap.Config.ARGB_8888,
                                                true
                                            )

                                            val mmToPixelRatio =
                                                edu.ibu.termotabip.utils.RulerUtils
                                                    .calculatePixelToMmRatioFromScalpel(bmp32)

                                            if (mmToPixelRatio > 0) {

                                                val displayedWidth =
                                                    canvasSize.width.toDouble()

                                                val displayedHeight =
                                                    canvasSize.height.toDouble()

                                                val scaleX =
                                                    bitmap.width.toDouble() / displayedWidth

                                                val scaleY =
                                                    bitmap.height.toDouble() / displayedHeight

                                                val scaleFactor =
                                                    Math.min(scaleX, scaleY)

                                                val pixelAreaOnScreen =
                                                    calculatePolygonAreaInPixels(points)

                                                val pixelAreaOnOriginalBitmap =
                                                    pixelAreaOnScreen *
                                                            (scaleFactor * scaleFactor)

                                                val realAreaMm2 =
                                                    pixelAreaOnOriginalBitmap *
                                                            (mmToPixelRatio * mmToPixelRatio)

                                                finalArea = realAreaMm2 / 100.0

                                                val minX = points.minOf { it.x }
                                                val maxX = points.maxOf { it.x }
                                                val minY = points.minOf { it.y }
                                                val maxY = points.maxOf { it.y }

                                                finalWidth =
                                                    ((maxX - minX) *
                                                            scaleFactor *
                                                            mmToPixelRatio) / 10.0

                                                finalHeight =
                                                    ((maxY - minY) *
                                                            scaleFactor *
                                                            mmToPixelRatio) / 10.0

                                                calculatedResult = String.format(
                                                    "Gerçek Alan: %.2f cm²\nBoyutlar: %.1f x %.1f cm",
                                                    finalArea,
                                                    finalWidth,
                                                    finalHeight
                                                )

                                            } else {
                                                calculatedResult =
                                                    "HATA: Referans nesne (bisturi) bulunamadı."
                                            }

                                        } else {
                                            calculatedResult =
                                                "HATA: Görsel işlenemedi."
                                        }

                                    } catch (e: Exception) {
                                        calculatedResult =
                                            "HATA: İşlem başarısız."
                                    }
                                },

                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(54.dp),

                                enabled = points.size > 2,
                                shape = RoundedCornerShape(16.dp)

                            ) {
                                Text(
                                    "Alanı Hesapla",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
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