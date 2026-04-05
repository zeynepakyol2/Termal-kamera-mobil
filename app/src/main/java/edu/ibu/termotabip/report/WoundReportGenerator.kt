package edu.ibu.termotabip.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import edu.ibu.termotabip.model.WoundAnalysisResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WoundReportGenerator"

// A4 boyutu (72 DPI)
private const val PAGE_WIDTH  = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN      = 48

data class PatientInfo(
    val name: String = "",
    val age: String = "",
    val ward: String = "",
    val notes: String = ""
)

object WoundReportGenerator {

    /**
     * PDF raporu oluşturur ve dosya URI'sini döndürür.
     * Paylaşım için FileProvider kullanır.
     */
    fun generateReport(
        context: Context,
        result: WoundAnalysisResult,
        thermalBitmap: Bitmap?,
        patient: PatientInfo = PatientInfo()
    ): Uri? {
        return try {
            val pdf  = PdfDocument()
            val page = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            )
            draw(page.canvas, result, thermalBitmap, patient)
            pdf.finishPage(page)

            // Dosyayı kaydet
            val dir  = File(context.cacheDir, "reports").also { it.mkdirs() }
            val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "TermoInJury_Rapor_$date.pdf")

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Log.i(TAG, "PDF oluşturuldu: ${file.absolutePath}")

            // FileProvider ile URI
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDF oluşturma hatası: ${e.message}")
            null
        }
    }

    /** PDF'i paylaşma Intent'i */
    fun shareReport(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type  = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TermoInJury Analiz Raporu")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Raporu Paylaş"))
    }

    // ── Çizim ────────────────────────────────────────────────────────────
    private fun draw(
        canvas: Canvas,
        result: WoundAnalysisResult,
        bitmap: Bitmap?,
        patient: PatientInfo
    ) {
        var y = MARGIN.toFloat()

        // ── Başlık bandı ─────────────────────────────────────────────────
        val headerPaint = Paint().apply {
            color   = Color.parseColor("#1565C0")
            style   = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 70f, headerPaint)

        val titlePaint = Paint().apply {
            color     = Color.WHITE
            textSize  = 22f
            typeface  = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("TermoInJury — Termal Yara Analiz Raporu", MARGIN.toFloat(), 44f, titlePaint)

        y = 80f

        // ── Tarih / Saat ─────────────────────────────────────────────────
        val dateStr = SimpleDateFormat("dd MMMM yyyy  HH:mm", Locale("tr")).format(Date())
        canvas.drawText(
            "Rapor Tarihi: $dateStr",
            MARGIN.toFloat(), y + 14f,
            smallGray()
        )
        y += 30f

        // ── Hasta bilgileri ───────────────────────────────────────────────
        if (patient.name.isNotBlank() || patient.age.isNotBlank() || patient.ward.isNotBlank()) {
            y = drawSection(canvas, "Hasta Bilgileri", y)
            if (patient.name.isNotBlank()) y = drawField(canvas, "Ad Soyad", patient.name, y)
            if (patient.age.isNotBlank())  y = drawField(canvas, "Yaş", patient.age, y)
            if (patient.ward.isNotBlank()) y = drawField(canvas, "Servis/Birim", patient.ward, y)
            if (patient.notes.isNotBlank()) y = drawField(canvas, "Notlar", patient.notes, y)
            y += 8f
        }

        // ── Termal görüntü ────────────────────────────────────────────────
        bitmap?.let {
            y = drawSection(canvas, "Termal Görüntü", y)
            val imgW = 220
            val imgH = (imgW * it.height.toFloat() / it.width).toInt()
            val scaled = Bitmap.createScaledBitmap(it, imgW, imgH, true)
            canvas.drawBitmap(scaled, MARGIN.toFloat(), y, null)

            // Görüntünün yanına analiz özeti
            val textX = MARGIN + imgW + 20f
            val summaryPaint = body()
            canvas.drawText("Çözünürlük: ${it.width}×${it.height}", textX, y + 20f, summaryPaint)
            canvas.drawText("Format: Termal (Y16 → RGB)", textX, y + 38f, summaryPaint)
            y += imgH + 16f
        }

        // ── Analiz sonucu ─────────────────────────────────────────────────
        y = drawSection(canvas, "Analiz Sonucu", y)

        // Varlık tespiti
        result.presenceResult?.let { p ->
            val label = if (p.hasWound) "✓  YARA TESPİT EDİLDİ" else "✓  YARA TESPİT EDİLMEDİ"
            val color = if (p.hasWound) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
            val paint = Paint().apply {
                this.color   = color
                textSize     = 16f
                typeface     = Typeface.DEFAULT_BOLD
                isAntiAlias  = true
            }
            canvas.drawText(label, MARGIN.toFloat(), y + 16f, paint)
            canvas.drawText(
                "Güven: %${(p.confidence * 100).toInt()}",
                MARGIN.toFloat(), y + 32f, smallGray()
            )
            y += 44f
        }

        // Evre
        result.stageResult?.let { s ->
            y = drawResultCard(
                canvas, y,
                title    = "Yara Evresi: ${s.stageName}",
                subtitle = s.stageDescription,
                conf     = s.confidence,
                color    = Color.parseColor("#E53935")
            )
        }

        // Risk
        result.riskResult?.let { r ->
            y = drawResultCard(
                canvas, y,
                title    = r.riskName,
                subtitle = "Risk değerlendirmesi",
                conf     = r.confidence,
                color    = Color.parseColor("#F57C00")
            )
        }

        // ── Öneriler ──────────────────────────────────────────────────────
        y = drawSection(canvas, "Klinik Öneriler", y)

        val suggestions = result.stageResult?.let { stageRecommendations(it.stage) }
            ?: result.riskResult?.let { riskRecommendations(it.riskLevel) }
            ?: emptyList()

        suggestions.forEachIndexed { idx, suggestion ->
            if (y > PAGE_HEIGHT - 60) return@forEachIndexed  // Sayfadan taşmasın
            val bulletPaint = Paint().apply {
                color       = Color.parseColor("#1565C0")
                textSize    = 11f
                isAntiAlias = true
            }
            canvas.drawText("${idx + 1}.", MARGIN.toFloat(), y + 12f, bulletPaint)
            drawWrappedText(canvas, suggestion, MARGIN + 18f, y + 12f, PAGE_WIDTH - MARGIN * 2 - 18f)
            y += 18f
        }

        // ── Footer ────────────────────────────────────────────────────────
        val footerPaint = Paint().apply {
            color       = Color.LTGRAY
            textSize    = 9f
            isAntiAlias = true
        }
        canvas.drawLine(
            MARGIN.toFloat(), PAGE_HEIGHT - 30f,
            (PAGE_WIDTH - MARGIN).toFloat(), PAGE_HEIGHT - 30f,
            Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        )
        canvas.drawText(
            "Bu rapor TermoInJury uygulaması tarafından otomatik oluşturulmuştur. " +
            "Klinik karar için hekime danışınız.",
            MARGIN.toFloat(), PAGE_HEIGHT - 14f, footerPaint
        )
    }

    // ── Yardımcı çizim fonksiyonları ──────────────────────────────────────

    private fun drawSection(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint().apply {
            color       = Color.parseColor("#1565C0")
            textSize    = 13f
            typeface    = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(title.uppercase(), MARGIN.toFloat(), y + 14f, paint)
        canvas.drawLine(
            MARGIN.toFloat(), y + 18f,
            (PAGE_WIDTH - MARGIN).toFloat(), y + 18f,
            Paint().apply { color = Color.parseColor("#1565C0"); strokeWidth = 0.8f }
        )
        return y + 26f
    }

    private fun drawField(canvas: Canvas, label: String, value: String, y: Float): Float {
        val labelPaint = Paint().apply {
            color       = Color.GRAY
            textSize    = 11f
            isAntiAlias = true
        }
        val valuePaint = body()
        canvas.drawText("$label:", MARGIN.toFloat(), y + 12f, labelPaint)
        canvas.drawText(value, MARGIN + 100f, y + 12f, valuePaint)
        return y + 18f
    }

    private fun drawResultCard(
        canvas: Canvas, y: Float,
        title: String, subtitle: String,
        conf: Float, color: Int
    ): Float {
        val cardPaint = Paint().apply {
            this.color = color
            alpha      = 20
            style       = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            MARGIN.toFloat(), y,
            (PAGE_WIDTH - MARGIN).toFloat(), y + 48f,
            8f, 8f, cardPaint
        )
        val borderPaint = Paint().apply {
            this.color  = color
            alpha       = 180
            style        = Paint.Style.STROKE
            strokeWidth  = 1f
        }
        canvas.drawRoundRect(
            MARGIN.toFloat(), y,
            (PAGE_WIDTH - MARGIN).toFloat(), y + 48f,
            8f, 8f, borderPaint
        )
        val titlePaint = Paint().apply {
            this.color  = color
            textSize    = 14f
            typeface    = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(title, (MARGIN + 10).toFloat(), y + 18f, titlePaint)
        canvas.drawText(subtitle, (MARGIN + 10).toFloat(), y + 32f, smallGray())
        canvas.drawText(
            "Güven: %${(conf * 100).toInt()}",
            (PAGE_WIDTH - MARGIN - 70).toFloat(), y + 20f,
            Paint().apply { this.color = color; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        )
        return y + 56f
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float) {
        val paint = body()
        val words = text.split(" ")
        var line  = ""
        var lineY = y
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                canvas.drawText(line, x, lineY, paint)
                line  = word
                lineY += 14f
            } else {
                line = test
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, lineY, paint)
    }

    private fun body() = Paint().apply {
        color       = Color.parseColor("#212121")
        textSize    = 11f
        isAntiAlias = true
    }

    private fun smallGray() = Paint().apply {
        color       = Color.GRAY
        textSize    = 10f
        isAntiAlias = true
    }

    // ── Önerileri getir ───────────────────────────────────────────────────

    private fun stageRecommendations(stage: Int) = when (stage) {
        0 -> listOf(
            "Bası uygulanan bölgeyi 2 saatte bir değerlendirin",
            "Yeniden konumlandırma sıklığını artırın",
            "Köpük veya silikon yara örtüsü ile koruyun",
            "Nemlendiricili bariyer krem uygulayın",
            "Protein ve vitamin takviyesi ekleyin"
        )
        1 -> listOf(
            "Yarayı nemli ortamda tutun (hidrokoloid veya köpük örtü)",
            "Bası azaltılmalı — hava veya sıvı dolu yüzey kullanın",
            "Enfeksiyon belirtilerini izleyin",
            "Yumuşak temizleme — SF veya yaraya uygun solüsyon",
            "Nütrisyon değerlendirmesi yapın"
        )
        2 -> listOf(
            "Yara bakım ekibiyle konsültasyon yapın",
            "Debridman gerekebilir (hekim kararıyla)",
            "Derin yarayı boşluk bırakmadan doldurun",
            "Enfeksiyon kontrolü — topikal veya sistemik antibiyotik",
            "Aktif yüzey değiştirme — sürekli düşük basınçlı destek"
        )
        3 -> listOf(
            "ACİL yara bakım uzmanı veya plastik cerrah konsültasyonu",
            "Cerrahi debridman/onarım değerlendirilmeli",
            "Negatif basınçlı yara tedavisi (VAC) düşünün",
            "Enfeksiyon riski yüksek — yoğun takip",
            "Nütrisyon desteği zorunlu"
        )
        else -> emptyList()
    }

    private fun riskRecommendations(level: Int) = when (level) {
        0 -> listOf(
            "Cildi temiz ve hidrate tutun",
            "İnkontinans sonrası cildi temizleyin",
            "Alkali sabun/temizleyicilerden kaçının",
            "Düzenli pozisyon değişimi — 30° lateral tercih edin",
            "Destek yüzey kullanın",
            "Yumuşak silikon ya da çok-katlı köpük yara örtüsünü düşünün"
        )
        1 -> listOf(
            "Cildi temiz ve hidrate tutun",
            "Düzenli pozisyon değişimi yapın",
            "Aktif destek yüzey kullanın",
            "Nütrisyon: yüksek kalori, yüksek protein, arjinin, çinko",
            "Günlük 30-35 kcal/kg enerji ve 1.25-1.5 g/kg/gün protein"
        )
        2 -> listOf(
            "Cildi temiz ve hidrate tutun",
            "Aktif destek yüzey kullanın",
            "Düzenli pozisyon değişimi (30° lateral)",
            "Basmakla solan eritem: şeffaf disk yöntemi kullanın",
            "Nütrisyon beslenme uzmanıyla değerlendirilmeli",
            "Günlük 30-35 kcal/kg enerji ve 1.25-1.5 g/kg/gün protein"
        )
        else -> emptyList()
    }
}
