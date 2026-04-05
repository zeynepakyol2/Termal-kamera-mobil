package edu.ibu.termotabip.ui.screens

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.ibu.termotabip.usb.UvcCamera
import edu.ibu.termotabip.viewmodel.WoundAnalysisViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale as CS
import coil.compose.rememberAsyncImagePainter

private const val TAG = "ThermalCameraScreen"
private const val HIKMICRO_VENDOR_ID  = 0x2bdf
private const val HIKMICRO_PRODUCT_ID = 0x102
private const val ACTION_USB_PERMISSION = "edu.ibu.termotabip.USB_PERMISSION"

@Composable
fun ThermalCameraScreen(
    viewModel: WoundAnalysisViewModel,
    onAnalysisDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // UvcCamera'nın StateFlow'unu dinle — her yeni frame UI'ı yeniler
    val previewBitmap by UvcCamera.previewFrame.collectAsStateWithLifecycle()

    var usbDevice            by remember { mutableStateOf<UsbDevice?>(null) }
    var usbPermissionGranted by remember { mutableStateOf(false) }
    var statusMessage        by remember { mutableStateOf("Kamera aranıyor...") }
    var isCapturing          by remember { mutableStateOf(false) }

    // ── Ekran kapanınca kamerayı kapat ────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            UvcCamera.disconnect()
        }
    }

    // ── USB izin receiver ─────────────────────────────────────────────────
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                usbPermissionGranted = granted
                statusMessage = if (granted) "USB izni verildi ✓"
                else "USB izni reddedildi"
                // İzin verilince kamerayı hemen bağla
                if (granted) usbDevice?.let { connectCamera(it, context) { msg -> statusMessage = msg } }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // ── Lifecycle resume'da USB durumunu kontrol et ───────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scanUsb(context) { found, hasPerm ->
                    usbDevice = found
                    usbPermissionGranted = hasPerm
                    when {
                        found == null -> statusMessage = "Kamera bulunamadı"
                        hasPerm && !UvcCamera.isConnected() -> {
                            connectCamera(found, context) { msg -> statusMessage = msg }
                        }
                        hasPerm -> statusMessage = "Canlı görüntü aktif"
                        else -> statusMessage = "İzin gerekiyor"
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── İlk yükleme ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        scanUsb(context) { found, hasPerm ->
            usbDevice = found
            usbPermissionGranted = hasPerm
            when {
                found == null -> statusMessage = "Kamera bulunamadı — USB bağlantısını kontrol edin"
                hasPerm       -> connectCamera(found, context) { msg -> statusMessage = msg }
                else          -> statusMessage = "Kamera algılandı — İzin gerekiyor"
            }
        }
    }

    // ── Analiz tamamlanınca sonuç ekranına geç ────────────────────────────
    LaunchedEffect(uiState.analysisResult) {
        if (uiState.analysisResult != null && !uiState.isLoading) {
            isCapturing = false
            onAnalysisDone()
        }
    }

    fun requestUsbPermission(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, pi)
        statusMessage = "İzin bekleniyor..."
    }

    fun captureAndAnalyze() {
        val bitmap = UvcCamera.capture()
        if (bitmap == null) {
            Toast.makeText(context, "Henüz görüntü yok, kamera bağlı mı?", Toast.LENGTH_SHORT).show()
            return
        }
        isCapturing = true
        statusMessage = "Analiz başlıyor..."
        viewModel.analyzeWoundImage(bitmap, context)
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Canlı önizleme alanı ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                // Termal görüntüyü ekrana sığdır
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Termal görüntü",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Bağlı değilken veya henüz frame gelmeden
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (usbPermissionGranted) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Text(
                            "Termal görüntü bekleniyor...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "📷",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            "HIKMICRO Mini3\nbağlantısı bekleniyor",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Durum göstergesi — sağ üst köşe
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when {
                                previewBitmap != null    -> Color(0xFF4CAF50) // yeşil — canlı
                                usbPermissionGranted     -> Color(0xFFFF9800) // turuncu — bağlı, bekliyor
                                usbDevice != null        -> Color(0xFFFF9800)
                                else                     -> Color(0xFF9E9E9E) // gri — yok
                            },
                            CircleShape
                        )
                )
                Text(
                    text = when {
                        previewBitmap != null -> "CANLI"
                        usbPermissionGranted  -> "BAĞLI"
                        usbDevice != null     -> "İZİN GEREKLİ"
                        else                  -> "BAĞLI DEĞİL"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ── Alt kontrol paneli ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Durum mesajı
            Text(
                statusMessage,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )

            when {
                // Kamera yok
                usbDevice == null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            scanUsb(context) { found, hasPerm ->
                                usbDevice = found; usbPermissionGranted = hasPerm
                                statusMessage = if (found != null) "Kamera algılandı" else "Kamera bulunamadı"
                                if (found != null && hasPerm) connectCamera(found, context) { statusMessage = it }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Yenile") }

                    OutlinedButton(
                        onClick = { UvcCamera.disconnect(); onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Geri") }
                }

                // İzin yok
                !usbPermissionGranted -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { requestUsbPermission(usbDevice!!) },
                        modifier = Modifier.weight(1f)
                    ) { Text("USB İzni Ver") }

                    OutlinedButton(
                        onClick = { onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Geri") }
                }

                // Hazır — büyük yakalama butonu
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Büyük kamera butonu (ortada)
                    Button(
                        onClick = { captureAndAnalyze() },
                        enabled = !isCapturing && !uiState.isLoading && previewBitmap != null,
                        modifier = Modifier
                            .size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (previewBitmap != null) Color.White else Color.Gray,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (isCapturing || uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = Color.Black
                            )
                        } else {
                            Text("●", style = MaterialTheme.typography.headlineMedium)
                        }
                    }

                    Text(
                        if (previewBitmap != null) "Analiz Et" else "Görüntü bekleniyor...",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )

                    TextButton(
                        onClick = { UvcCamera.disconnect(); onBack() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.5f))
                    ) { Text("Geri") }
                }
            }
        }
    }
}

// ── Yardımcı: kameraya bağlan ─────────────────────────────────────────────
private fun connectCamera(
    device: UsbDevice,
    context: Context,
    onStatus: (String) -> Unit
) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    onStatus("Kamera bağlanıyor...")
    val ok = UvcCamera.connect(device, usbManager, context)
    onStatus(if (ok) "Canlı görüntü aktif" else "Kamera bağlantısı başarısız")
}

// ── Yardımcı: USB tarama ──────────────────────────────────────────────────
private fun scanUsb(
    context: Context,
    onResult: (device: UsbDevice?, hasPermission: Boolean) -> Unit
) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val found = usbManager.deviceList.values.firstOrNull {
        it.vendorId == HIKMICRO_VENDOR_ID && it.productId == HIKMICRO_PRODUCT_ID
    }
    onResult(found, found != null && usbManager.hasPermission(found))
}