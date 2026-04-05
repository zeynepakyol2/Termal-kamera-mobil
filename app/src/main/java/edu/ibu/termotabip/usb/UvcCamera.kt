package edu.ibu.termotabip.usb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "UvcCamera"
private const val RT_SET = 0x21
private const val RT_GET = 0xA1
private const val SET_CUR = 0x01
private const val GET_CUR = 0x81
private const val GET_MAX = 0x83
private const val GET_DEF = 0x87
private const val VS_PROBE_CONTROL  = 0x01
private const val VS_COMMIT_CONTROL = 0x02
private const val CAM_WIDTH  = 384
private const val CAM_HEIGHT = 288
private const val FULL_FRAME_BYTES = CAM_WIDTH * CAM_HEIGHT * 2
private const val SAVE_FRAMES = 5  // İlk 5 frame'i kaydet

object UvcCamera {

    private val _previewFrame = MutableStateFlow<Bitmap?>(null)
    val previewFrame: StateFlow<Bitmap?> = _previewFrame.asStateFlow()

    private var conn: UsbDeviceConnection? = null
    private var streamIface: UsbInterface? = null
    private var bulkIn: UsbEndpoint? = null
    private var streamJob: Job? = null
    private var isConnected = false
    private var appContext: Context? = null

    fun connect(device: UsbDevice, usbManager: UsbManager, context: Context? = null): Boolean {
        if (isConnected) return true
        appContext = context

        val c = try { usbManager.openDevice(device) }
        catch (e: Exception) { Log.e(TAG, "openDevice: ${e.message}"); return false }
        if (c == null) { Log.e(TAG, "openDevice null"); return false }

        var si: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 14 && iface.interfaceSubclass == 2) { si = iface; break }
        }
        if (si == null) { Log.e(TAG, "VideoStreaming interface yok"); c.close(); return false }
        if (!c.claimInterface(si, true)) { Log.e(TAG, "Claim başarısız"); c.close(); return false }

        val ep = (0 until si.endpointCount)
            .map { si.getEndpoint(it) }
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
        if (ep == null) { Log.e(TAG, "Bulk-IN yok"); c.releaseInterface(si); c.close(); return false }

        conn = c; streamIface = si; bulkIn = ep
        setupStreaming(c, si.id)
        isConnected = true
        Log.i(TAG, "Bağlandı — akış başlıyor")
        startStream()
        return true
    }

    fun disconnect() {
        streamJob?.cancel(); streamJob = null
        try { streamIface?.let { conn?.releaseInterface(it) }; conn?.close() } catch (_: Exception) {}
        conn = null; streamIface = null; bulkIn = null
        isConnected = false; _previewFrame.value = null
        Log.d(TAG, "Bağlantı kapatıldı")
    }

    fun isConnected() = isConnected
    fun capture(): Bitmap? = _previewFrame.value

    // Ham frame'i dosyaya kaydet
    private fun saveFrame(bytes: ByteArray, idx: Int) {
        val ctx = appContext ?: return
        try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val file = File(dir, "thermal_frame_%02d.bin".format(idx))
            file.writeBytes(bytes)
            Log.i(TAG, "KAYIT: ${file.absolutePath} | ${bytes.size} bytes | ilk16=${bytes.take(16).map{it.toInt() and 0xFF}}")
        } catch (e: Exception) { Log.e(TAG, "Kayıt hatası: ${e.message}") }
    }
    private fun decodeY16clean(bytes: ByteArray): Bitmap? {
        if (bytes.size < CAM_WIDTH * CAM_HEIGHT * 2) {
            Log.d(TAG, "Yetersiz veri: ${bytes.size} bytes")
            return null
        }
        // Tam frame boyutunu al, fazlasını at
        return decodeY16(bytes, CAM_WIDTH, CAM_HEIGHT)
    }
    private fun startStream() {
        val ep = bulkIn ?: return
        val c  = conn   ?: return

        streamJob = CoroutineScope(Dispatchers.IO).launch {
            val pktSize    = maxOf(ep.maxPacketSize, 512)
            val buf        = ByteArray(pktSize)
            val payload    = java.io.ByteArrayOutputStream(FULL_FRAME_BYTES + 4096)
            var emptyCount = 0
            var frameCount = 0

            Log.d(TAG, "Stream başladı pktSize=$pktSize")

            while (isActive) {
                val n = c.bulkTransfer(ep, buf, pktSize, 500)
                if (n <= 0) { emptyCount++; if (emptyCount > 30) delay(50); continue }
                emptyCount = 0
                if (n < 2) continue

                // UVC header'ı atla, sadece payload'u ekle
                val hdrLen = (buf[0].toInt() and 0xFF).coerceIn(0, n)
                if (hdrLen < n) payload.write(buf, hdrLen, n - hdrLen)

                // Tam frame doldu mu?
                if (payload.size() >= FULL_FRAME_BYTES) {
                    val bytes = payload.toByteArray()
                    // Tam olarak FULL_FRAME_BYTES al, fazlasını sıradaki frame'e taşı
                    val frameBytes = bytes.copyOf(FULL_FRAME_BYTES)
                    val leftover   = bytes.copyOfRange(FULL_FRAME_BYTES, bytes.size)
                    payload.reset()
                    if (leftover.isNotEmpty()) payload.write(leftover)

                    frameCount++
                    if (frameCount <= SAVE_FRAMES) saveFrame(frameBytes, frameCount)

                    val bmp = decodeY16(frameBytes, CAM_WIDTH, CAM_HEIGHT)
                    if (bmp != null) {
                        if (frameCount <= 3) Log.i(TAG, "Frame #$frameCount OK: ${bmp.width}x${bmp.height}")
                        _previewFrame.value = bmp
                    }
                }
                // prevFid kullanılmıyor artık — satırı sil
            }
        }
    }

    private fun decodeFrame(bytes: ByteArray): Bitmap? {
        // JPEG deneme YOK — kamera Y16 ham veri gönderiyor

        // Y16 tam frame
        if (bytes.size >= CAM_WIDTH * CAM_HEIGHT * 2) {
            return decodeY16(bytes, CAM_WIDTH, CAM_HEIGHT)
        }

        // Boyut tahmin et
        val totalPixels = bytes.size / 2
        if (totalPixels >= 256 * 192) {
            val w = when {
                totalPixels >= 384 * 288 -> 384
                totalPixels >= 320 * 240 -> 320
                else -> 256
            }
            val h = totalPixels / w
            if (h > 0) return decodeY16(bytes, w, h)
        }

        Log.d(TAG, "Decode edilemedi: ${bytes.size} bytes")
        return null
    }

    private fun decodeY16(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        if (bytes.size < width * height * 2) return null

        val pixels16 = ShortArray(width * height) { i ->
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }

        // 0 ve 65535 gürültüyü atla — p5/p95 kullan
        val sorted = pixels16.map { it.toInt() and 0xFFFF }.filter { it in 1000..64000 }.sorted()
        if (sorted.isEmpty()) return null

        val p5  = sorted[(sorted.size * 0.05).toInt()]
        val p95 = sorted[(sorted.size * 0.95).toInt()]
        val range = (p95 - p5).coerceAtLeast(1)

        val argb = IntArray(width * height) { i ->
            val v = pixels16[i].toInt() and 0xFFFF
            val t = ((v - p5).toFloat() / range).coerceIn(0f, 1f)
            thermalColor(t)
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(argb, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun thermalColor(t: Float): Int {
        val r: Int; val g: Int; val b: Int
        when {
            t < 0.25f -> { val f=t/0.25f;        r=0;              g=(255*f).toInt();    b=255 }
            t < 0.5f  -> { val f=(t-0.25f)/0.25f;r=0;              g=255;                b=(255*(1-f)).toInt() }
            t < 0.75f -> { val f=(t-0.5f)/0.25f; r=(255*f).toInt();g=255;                b=0 }
            else      -> { val f=(t-0.75f)/0.25f;r=255;            g=(255*(1-f)).toInt();b=0 }
        }
        return (0xFF shl 24) or (r.coerceIn(0,255) shl 16) or (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)
    }

    private fun setupStreaming(c: UsbDeviceConnection, wIndex: Int) {
        for (probeLen in listOf(34, 26)) {
            for (wi in listOf(wIndex, 0)) {
                if (tryProbeCommit(c, wi, probeLen)) { Log.i(TAG, "Streaming kuruldu probeLen=$probeLen wIndex=$wi"); return }
            }
        }
        Log.w(TAG, "Probe/commit başarısız")
    }

    private fun tryProbeCommit(c: UsbDeviceConnection, wi: Int, len: Int): Boolean {
        val maxBuf = ByteArray(len); val defBuf = ByteArray(len)
        val maxR = c.controlTransfer(RT_GET, GET_MAX, (VS_PROBE_CONTROL shl 8), wi, maxBuf, len, 2000)
        val defR = c.controlTransfer(RT_GET, GET_DEF, (VS_PROBE_CONTROL shl 8), wi, defBuf, len, 2000)
        val probe = when { maxR == len -> maxBuf; defR == len -> defBuf; else -> buildProbe(1,1,len) }
        val setR = c.controlTransfer(RT_SET, SET_CUR, (VS_PROBE_CONTROL shl 8), wi, probe, len, 2000)
        if (setR < 0) return false
        val curBuf = ByteArray(len)
        c.controlTransfer(RT_GET, GET_CUR, (VS_PROBE_CONTROL shl 8), wi, curBuf, len, 2000)
        return c.controlTransfer(RT_SET, SET_CUR, (VS_COMMIT_CONTROL shl 8), wi, curBuf, len, 2000) >= 0
    }

    private fun buildProbe(fmtIdx: Int, frameIdx: Int, len: Int): ByteArray {
        val d = ByteArray(len); d[0]=0x01; d[2]=fmtIdx.toByte(); d[3]=frameIdx.toByte()
        val iv=333333; d[4]=(iv and 0xFF).toByte(); d[5]=((iv shr 8) and 0xFF).toByte()
        d[6]=((iv shr 16) and 0xFF).toByte(); d[7]=((iv shr 24) and 0xFF).toByte(); return d
    }
}