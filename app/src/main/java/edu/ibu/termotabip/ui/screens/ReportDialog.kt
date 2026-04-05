package edu.ibu.termotabip.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.ibu.termotabip.model.WoundAnalysisResult
import edu.ibu.termotabip.report.PatientInfo
import edu.ibu.termotabip.report.WoundReportGenerator

/**
 * Hasta bilgisi girişi + PDF oluşturma diyaloğu.
 * ResultScreen'den çağrılır.
 */
@Composable
fun ReportDialog(
    result: WoundAnalysisResult,
    thermalBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name  by remember { mutableStateOf("") }
    var age   by remember { mutableStateOf("") }
    var ward  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Rapor Oluştur",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Hasta bilgileri opsiyoneldir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad (opsiyonel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Yaş") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ward,
                        onValueChange = { ward = it },
                        label = { Text("Servis") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notlar (opsiyonel)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    errorMsg = null
                    val patient = PatientInfo(
                        name  = name.trim(),
                        age   = age.trim(),
                        ward  = ward.trim(),
                        notes = notes.trim()
                    )
                    val uri = WoundReportGenerator.generateReport(
                        context, result, thermalBitmap, patient
                    )
                    if (uri != null) {
                        WoundReportGenerator.shareReport(context, uri)
                        onDismiss()
                    } else {
                        errorMsg = "PDF oluşturulamadı. Tekrar deneyin."
                    }
                    isGenerating = false
                },
                enabled = !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isGenerating) "Oluşturuluyor..." else "PDF Oluştur ve Paylaş")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
