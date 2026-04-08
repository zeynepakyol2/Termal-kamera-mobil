package edu.ibu.termotabip.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object RulerUtils {


    private const val RULER_REAL_WIDTH_MM = 25.0

    private const val SCALPEL_REAL_WIDTH_MM = 8.0

    fun calculatePixelToMmRatioFromNumbers(bitmap: Bitmap): Double {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_RGB2HSV)

        // 1. İkisini de bağımsız olarak dene
        val rulerRatio = detectBlueRuler(hsvMat)
        val scalpelRatio = detectScalpel(hsvMat)

        // 2. Akıllı Seçim Mantığı
        return when {
            // Eğer her ikisi de bulunduysa, muhtemelen bistüri daha nettir (termal görüntülerde)
            // Veya hangisi -1 değilse onu döndür
            rulerRatio > 0.0 && scalpelRatio > 0.0 -> {
                println("BİLGİ: İkisi de bulundu, Bistüriye öncelik veriliyor.")
                scalpelRatio // Bistüri genellikle daha kararlı sonuç verir
            }
            rulerRatio > 0.0 -> {
                println("BİLGİ: Sadece Mezura bulundu.")
                rulerRatio
            }
            scalpelRatio > 0.0 -> {
                println("BİLGİ: Sadece Bistüri bulundu.")
                scalpelRatio
            }
            else -> {
                println("HATA: Hiçbir referans bulunamadı.")
                -1.0
            }
        }
    }

    private fun detectBlueRuler(hsvMat: Mat): Double {
        val lowerBlue = Scalar(100.0, 70.0, 50.0) // 90, 50, 20 idi
        val upperBlue = Scalar(140.0, 255.0, 255.0)

        val mask = Mat()
        Core.inRange(hsvMat, lowerBlue, upperBlue, mask)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        if (contours.isEmpty()) return -1.0

        var bestRulerContour: MatOfPoint? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < 2000.0) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotatedRect = Imgproc.minAreaRect(contour2f)

            val shortSide = Math.min(rotatedRect.size.width, rotatedRect.size.height)
            val longSide = Math.max(rotatedRect.size.width, rotatedRect.size.height)

            if (shortSide == 0.0) continue

            val aspectRatio = longSide / shortSide
            val isLongEnough = aspectRatio >= 2.5 && aspectRatio <= 20.0

            val perfectRectArea = shortSide * longSide
            val rectangularity = if (perfectRectArea > 0) area / perfectRectArea else 0.0

            if (isLongEnough && rectangularity >= 0.75 && shortSide > 15.0) {
                if (area > maxArea) {
                    maxArea = area
                    bestRulerContour = contour
                }
            }
        }

        if (bestRulerContour == null) return -1.0

        val contour2f = MatOfPoint2f(*bestRulerContour.toArray())
        val rotatedRect = Imgproc.minAreaRect(contour2f)
        val pixelWidth = Math.min(rotatedRect.size.width, rotatedRect.size.height)

        return if (pixelWidth > 0) RULER_REAL_WIDTH_MM / pixelWidth else -1.0
    }

    private fun detectScalpel(hsvMat: Mat): Double {
        // Siyah nesneyi bul
        val lowerBlack = Scalar(0.0, 0.0, 0.0)
        val upperBlack = Scalar(180.0, 255.0, 80.0)

        val mask = Mat()
        Core.inRange(hsvMat, lowerBlack, upperBlack, mask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.erode(mask, mask, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        if (contours.isEmpty()) return -1.0

        var bestScalpelContour: MatOfPoint? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            // Filtreyi küçültüyoruz ki neşteri kaçırmasın
            if (area < 100.0) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotatedRect = Imgproc.minAreaRect(contour2f)

            val shortSide = Math.min(rotatedRect.size.width, rotatedRect.size.height)
            val longSide = Math.max(rotatedRect.size.width, rotatedRect.size.height)

            if (shortSide == 0.0) continue

            val aspectRatio = longSide / shortSide

            // Oranı esnetiyoruz (Bazı açılarda neşter daha kısa görünebilir)
            if (aspectRatio in 3.0..12.0) {
                if (area > maxArea) {
                    maxArea = area
                    bestScalpelContour = contour
                }
            }
        }

        if (bestScalpelContour == null) return -1.0

        val contour2f = MatOfPoint2f(*bestScalpelContour.toArray())
        val rotatedRect = Imgproc.minAreaRect(contour2f)


        val pixelWidth = Math.min(rotatedRect.size.width, rotatedRect.size.height)

        return if (pixelWidth > 0.1) SCALPEL_REAL_WIDTH_MM / pixelWidth else -1.0
    }
}