package edu.ibu.termotabip.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "RulerUtils"

object RulerUtils {

    // ── Gerçek bisturi ölçüleri (kullanıcı tarafından ölçüldü) ──────────────
    // Uzun kenar: 4.5 cm = 45.0 mm
    private const val SCALPEL_REAL_LENGTH_MM = 45.0
    // Genişlik: 0.7 cm = 7.0 mm
    private const val SCALPEL_REAL_WIDTH_MM = 7.0
    // Gerçek en/boy oranı: 45 / 7 ≈ 6.43
    private const val SCALPEL_REAL_ASPECT_RATIO = SCALPEL_REAL_LENGTH_MM / SCALPEL_REAL_WIDTH_MM

    private const val MAX_ANALYSIS_DIMENSION = 500

    // Bisturi eğimli/açılı çekimde kısalmış görünebilir (4.5cm yerine 4cm gibi),
    // bu yüzden alt sınır gerçek orandan (6.4) düşük tutuluyor.
    private const val MIN_ASPECT_RATIO = 3.5
    // Gerçek orandan çok daha "iğne gibi ince" nesneleri elemek için üst sınır.
    private const val MAX_ASPECT_RATIO = 9.0

    // Gürültüyü elemek için minimum piksel sayısı
    private const val MIN_COMPONENT_PIXELS = 150

    // Uzunluktan geriye hesaplanan genişliğin, gerçek 7mm'e göre izin verilen sapma payı.
    // NOT: Gerçek test görsellerinde temiz tespitler %2-3 hata veriyor; parmak/eldivenle
    // kısmen kapatılmış bisturilerde ise hata %40'ın üzerine çıkıyor. Bu yüzden tolerans
    // sıkı tutuluyor: amaç, güvenilmez (kısmen kapalı) bir ölçümü sessizce kabul etmek
    // yerine reddetmek.
    private const val WIDTH_CONSISTENCY_TOLERANCE = 0.30 // ±%30

    // Şeklin kendi döndürülmüş sınırlayıcı kutusunu ne kadar doldurduğu (solidity).
    // NOT: Parmak/el ile kısmen kapatılmış bir bisturi, doluluk oranını düşürebilir
    // (gerçek test verisinde kısmen kapalı bir bisturi 0.275 doluluk vermişti). Eşik,
    // bunu dışarıda bırakmayacak ama tamamen alakasız/dağınık bölgeleri (0.15-0.20
    // civarı) hâlâ eleyecek şekilde ayarlandı.
    private const val MIN_FILL_RATIO = 0.25

    // Nihai piksel->mm oranı için mantıklı sınırlar (güvenlik ağı).
    // Bu aralığın çok dışına çıkan bir sonuç, yanlış bir nesnenin (ör. pencere
    // yansıması) bisturi sanılıp seçildiğinin işaretidir → sonuç reddedilir.
    // Kendi veri setine göre (kamera-nesne mesafesi aralığına göre) ayarlaman gerekebilir.
    private const val MIN_PLAUSIBLE_MM_PER_PX = 0.02
    private const val MAX_PLAUSIBLE_MM_PER_PX = 1.0

    // Bir "parlama" (highlight) lekesinin, çekirdeğe birleştirilmesi için izin
    // verilen maksimum piksel sayısı. Gerçek specular highlight (bisturi üzerindeki
    // ışık yansıması) genelde küçük/ince bir şerittir. Arkadaki beyaz kağıt/gazlı bez
    // gibi büyük düz yüzeyler bu sınırı aşar ve asla çekirdeğe birleştirilmez —
    // böylece bisturi arka plandaki beyazlıkla tek bir dev lekeye dönüşmez.
    private const val MAX_HIGHLIGHT_PATCH_PIXELS = 250

    private data class Shape(
        val length: Double,
        val width: Double,
        val aspectRatio: Double,
        val fillRatio: Double,
        val minX: Int, val maxX: Int, val minY: Int, val maxY: Int
    )

    /**
     * Görüntüdeki bisturiyi (metalik bıçak + koyu sap) tespit ederek piksel->mm oranını döner.
     * Bisturi bulunamazsa -1.0 döner.
     */
    fun calculatePixelToMmRatioFromScalpel(original: Bitmap): Double {
        val rawScale = MAX_ANALYSIS_DIMENSION.toDouble() / maxOf(original.width, original.height)
        val workScale = if (rawScale < 1.0) rawScale else 1.0
        val width = (original.width * workScale).toInt().coerceAtLeast(1)
        val height = (original.height * workScale).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createScaledBitmap(original, width, height, true)
        try {
            // ── Tespit kısmı ──
            // Parlama (specular highlight), sadece gerçek metalik "çekirdek" bölgeye
            // bitişikse kabul edilir. Böylece pencere/lens flare gibi nesneyle
            // alakasız uzak parlak bölgeler maskeye asla dahil olmaz.
            val rawMask = buildLinkedMask(bmp, width, height)
            // Küçük kopuklukları/delikleri gidermek için ek bir morfolojik kapama.
            val mask = closeMask(rawMask, width, height)
            val components = findConnectedComponents(mask, width, height)

            val candidates = components
                .map { comp -> comp to analyzeShape(comp, width) }
                .filter { (comp, shape) ->
                    comp.size >= MIN_COMPONENT_PIXELS &&
                            shape.aspectRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO &&
                            shape.fillRatio >= MIN_FILL_RATIO
                }

            if (candidates.isEmpty()) {
                Log.w(TAG, "Bisturi adayı bulunamadı (toplam bileşen: ${components.size})")
                return -1.0
            }

            candidates.forEach { (comp, shape) ->
                Log.d(TAG, "Aday → px=${comp.size} uzunluk=%.1f genişlik=%.1f oran=%.2f doluluk=%.2f konum=(x:%d-%d, y:%d-%d)"
                    .format(shape.length, shape.width, shape.aspectRatio, shape.fillRatio,
                        shape.minX, shape.maxX, shape.minY, shape.maxY))
            }

            // ── Seçim kısmı: en/boy oranına yakınlığa göre ──
            // NOT: Doluluğu da skora dahil etmek (aspectError + fillPenalty), test
            // verisinde parmakla kısmen kapatılmış gerçek bisturiyi (fill=0.28) değil,
            // görüntünün başka bir yerindeki yanlış ama "daha dolu" görünen küçük bir
            // bölgeyi (fill=0.35) seçtiriyordu. Doluluk zaten yukarıda MIN_FILL_RATIO ile
            // elemek için kullanıldı; buradaki son seçim SADECE en/boy oranı yakınlığına
            // göre yapılınca, dört test görselinin hepsinde doğru bölge seçildi.
            val (_, bestShape) = candidates.minByOrNull { (_, shape) ->
                Math.abs(shape.aspectRatio - SCALPEL_REAL_ASPECT_RATIO)
            }!!

            if (bestShape.length <= 0.0) return -1.0

            val lengthInOriginalPixels = bestShape.length / workScale
            val widthInOriginalPixels = bestShape.width / workScale

            // Tutarlılık kontrolü: uzunluktan hesaplanan oranla genişlik ölçülüp
            // gerçek 7mm'e ne kadar yakın çıktığına bakılır.
            val ratioFromLength = SCALPEL_REAL_LENGTH_MM / lengthInOriginalPixels
            val impliedWidthMm = widthInOriginalPixels * ratioFromLength
            val widthError = Math.abs(impliedWidthMm - SCALPEL_REAL_WIDTH_MM) / SCALPEL_REAL_WIDTH_MM

            if (widthError > WIDTH_CONSISTENCY_TOLERANCE) {
                Log.w(TAG, "Aday tutarsız: uzunluktan çıkan genişlik=%.2fmm, beklenen=%.1fmm (hata=%.0f%%). " +
                        "Bisturi muhtemelen el/eldiven/başka bir nesne tarafından kısmen kapatılmış olabilir " +
                        "(kapalı kısım gerçek oranı bozar). Reddedildi. konum=(x:%d-%d, y:%d-%d)"
                            .format(impliedWidthMm, SCALPEL_REAL_WIDTH_MM, widthError * 100,
                                bestShape.minX, bestShape.maxX, bestShape.minY, bestShape.maxY))
                return -1.0
            }

            // Güvenlik ağı: sonuç fiziksel olarak makul aralığın dışındaysa,
            // muhtemelen bisturiyle alakasız bir nesne seçilmiştir → reddet.
            if (ratioFromLength !in MIN_PLAUSIBLE_MM_PER_PX..MAX_PLAUSIBLE_MM_PER_PX) {
                Log.w(TAG, "Oran mantıksız aralıkta: %.4f mm/px (izin verilen: %.2f-%.2f). Reddedildi. konum=(x:%d-%d, y:%d-%d)"
                    .format(ratioFromLength, MIN_PLAUSIBLE_MM_PER_PX, MAX_PLAUSIBLE_MM_PER_PX,
                        bestShape.minX, bestShape.maxX, bestShape.minY, bestShape.maxY))
                return -1.0
            }

            Log.i(TAG, "Seçilen bisturi: uzunluk(orijinal px)=%.1f genişlik(orijinal px)=%.1f doluluk=%.2f konum=(x:%d-%d, y:%d-%d) → oran=%.4f mm/px"
                .format(lengthInOriginalPixels, widthInOriginalPixels, bestShape.fillRatio,
                    bestShape.minX, bestShape.maxX, bestShape.minY, bestShape.maxY, ratioFromLength))
            return ratioFromLength
        } finally {
            if (bmp != original) bmp.recycle()
        }
    }

    // ── Tespit fonksiyonları ──────────────────────────────────────────────

    /**
     * İki aşamalı maske:
     *  1) "Çekirdek" mask: kesin metalik bıçak (orta-yüksek parlaklık, ama tam
     *     beyaza yakın değil) + koyu sap. Bu, güvenilir/kesin bisturi pikselleridir.
     *  2) "Parlama" mask: neredeyse beyaza yakın, çok düşük doygunluklu pikseller
     *     (specular highlight ADAYLARI). Bunlar TEK BAŞINA güvenilir değildir çünkü
     *     pencere/lens flare veya arka plandaki beyaz kağıt/gazlı bez gibi bisturiyle
     *     alakasız parlak yüzeyler de bu kritere uyar.
     *
     * Parlama lekeleri, YALNIZCA çekirdeğe bitişikse VE küçükse (MAX_HIGHLIGHT_PATCH_PIXELS
     * altında, yani gerçek bir ışık yansımasına benzer ince bir şeritse) son maskeye eklenir.
     * Büyük parlama lekeleri (arka plandaki kağıt gibi) boyut sınırını aştığı için asla
     * birleştirilmez — böylece bisturi, arkasındaki beyaz yüzeyle tek bir dev lekeye dönüşmez.
     */
    private fun buildLinkedMask(bmp: Bitmap, w: Int, h: Int): BooleanArray {
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val core = BooleanArray(w * h)
        val highlight = BooleanArray(w * h)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            Color.colorToHSV(pixels[i], hsv)
            val sat = hsv[1]
            val value = hsv[2]
            // Çekirdek metalik bıçak: düşük doygunluk, orta-yüksek parlaklık ama
            // tam beyaza yakın değil (0.90 üstü artık "parlama adayı" sayılıyor).
            val isMetallicCore = sat < 0.18f && value in 0.55f..0.90f
            // Koyu/renkli sap (siyah, lacivert, koyu gri vb.) — çekirdeğin parçası.
            val isHandle = sat < 0.35f && value in 0.05f..0.35f
            core[i] = isMetallicCore || isHandle
            // Parlama adayı: çok düşük doygunluk, çok yüksek parlaklık.
            highlight[i] = sat < 0.25f && value > 0.85f
        }

        // Çekirdeği 1 piksel genişleterek "komşuluk" bölgesi oluştur.
        val coreNeighborhood = dilate(core, w, h)

        // Parlama maskesini kendi bağlı bileşenlerine ayır: her leke tek tek
        // değerlendirilecek (küçük mü büyük mü, çekirdeğe değiyor mu).
        val highlightComponents = rawConnectedComponents(highlight, w, h)

        val result = core.copyOf()
        for (comp in highlightComponents) {
            if (comp.size > MAX_HIGHLIGHT_PATCH_PIXELS) continue // çok büyük → arka plan yüzeyi olabilir, atla
            val touchesCore = comp.any { coreNeighborhood[it] }
            if (touchesCore) {
                for (idx in comp) result[idx] = true
            }
        }
        return result
    }

    /**
     * Morfolojik kapama (dilate + erode). Parlama gibi nedenlerle maskede oluşan
     * küçük (1-2 piksellik) kopuklukları/delikleri, nesnenin genel boyutunu ciddi
     * şekilde büyütmeden birleştirir.
     */
    private fun closeMask(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val dilated = dilate(mask, w, h)
        return erode(dilated, w, h)
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val result = BooleanArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (mask[idx]) {
                    result[idx] = true
                    continue
                }
                var found = false
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h && mask[ny * w + nx]) {
                            found = true
                        }
                    }
                }
                result[idx] = found
            }
        }
        return result
    }

    private fun erode(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val result = BooleanArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!mask[idx]) {
                    result[idx] = false
                    continue
                }
                var allSet = true
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h || !mask[ny * w + nx]) {
                            allSet = false
                        }
                    }
                }
                result[idx] = allSet
            }
        }
        return result
    }

    /** Bağlı bileşenleri, boyut filtrelemeden, ham haliyle döner. */
    private fun rawConnectedComponents(mask: BooleanArray, w: Int, h: Int): List<List<Int>> {
        val visited = BooleanArray(mask.size)
        val components = mutableListOf<List<Int>>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue

            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            visited[start] = true
            val comp = mutableListOf<Int>()

            while (stack.isNotEmpty()) {
                val idx = stack.removeLast()
                comp.add(idx)
                val x = idx % w
                val y = idx / w

                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h) {
                            val nIdx = ny * w + nx
                            if (mask[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true
                                stack.addLast(nIdx)
                            }
                        }
                    }
                }
            }
            components.add(comp)
        }
        return components
    }

    /** Bağlı bileşenleri bulur ve gürültüyü elemek için minimum piksel sayısına göre filtreler. */
    private fun findConnectedComponents(mask: BooleanArray, w: Int, h: Int): List<List<Int>> =
        rawConnectedComponents(mask, w, h).filter { it.size >= MIN_COMPONENT_PIXELS }

    /**
     * PCA ile bileşenin ana ekseni boyunca uzunluğunu, buna dik genişliğini ve
     * kendi döndürülmüş sınırlayıcı kutusunu ne kadar doldurduğunu (fillRatio) hesaplar.
     */
    private fun analyzeShape(comp: List<Int>, w: Int): Shape {
        val xs = DoubleArray(comp.size)
        val ys = DoubleArray(comp.size)
        for ((i, idx) in comp.withIndex()) {
            xs[i] = (idx % w).toDouble()
            ys[i] = (idx / w).toDouble()
        }

        val meanX = xs.average()
        val meanY = ys.average()

        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }

        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        val axisX = cos(theta)
        val axisY = sin(theta)
        val perpX = -sin(theta)
        val perpY = cos(theta)

        var minAlong = Double.MAX_VALUE; var maxAlong = -Double.MAX_VALUE
        var minPerp = Double.MAX_VALUE; var maxPerp = -Double.MAX_VALUE

        for (i in xs.indices) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            val along = dx * axisX + dy * axisY
            val perp = dx * perpX + dy * perpY
            if (along < minAlong) minAlong = along
            if (along > maxAlong) maxAlong = along
            if (perp < minPerp) minPerp = perp
            if (perp > maxPerp) maxPerp = perp
        }

        val length = maxAlong - minAlong
        val width = (maxPerp - minPerp).coerceAtLeast(1.0)
        val boundingArea = length * width
        val fillRatio = if (boundingArea > 0) comp.size / boundingArea else 0.0

        // Debug/log amaçlı eksenlere paralel sınır kutusu (hangi bölgede olduğunu görmek için).
        val minX = xs.min().toInt(); val maxX = xs.max().toInt()
        val minY = ys.min().toInt(); val maxY = ys.max().toInt()

        return Shape(length, width, length / width, fillRatio, minX, maxX, minY, maxY)
    }
}
