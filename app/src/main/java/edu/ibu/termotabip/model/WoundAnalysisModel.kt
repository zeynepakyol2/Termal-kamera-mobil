package edu.ibu.termotabip.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import androidx.core.graphics.scale

private const val TAG = "WoundAnalysisModel"

// ─────────────────────────────────────────────
// Tek bir TFLite modelini sarmalayan yardımcı sınıf
// ─────────────────────────────────────────────
class TFLiteModel(
    private val context: Context,
    private val modelFileName: String
) {
    private var interpreter: Interpreter? = null
    var inputWidth = 224
    var inputHeight = 224
    var inputChannels = 1
    var outputClasses = 2

    init {
        loadModel()
        detectParameters()
    }

    private fun loadModel() {
        try {
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(loadModelFile(), options)
            Log.d(TAG, "$modelFileName başarıyla yüklendi")
        } catch (e: Exception) {
            Log.e(TAG, "$modelFileName yüklenemedi: ${e.message}")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(modelFileName)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun detectParameters() {
        val interp = interpreter ?: return
        try {
            val inputShape = interp.getInputTensor(0).shape()
            val outputShape = interp.getOutputTensor(0).shape()

            if (inputShape.size >= 3) {
                inputHeight = inputShape[1]
                inputWidth  = inputShape[2]
                inputChannels = if (inputShape.size > 3) inputShape[3] else 1
            }
            outputClasses = if (outputShape.size > 1) outputShape[1] else outputShape[0]

            Log.d(TAG, "$modelFileName — giriş:${inputShape.contentToString()}, " +
                    "çıktı:${outputShape.contentToString()}, " +
                    "tip:${interp.getInputTensor(0).dataType()}")
        } catch (e: Exception) {
            Log.e(TAG, "$modelFileName parametre tespitinde hata: ${e.message}")
        }
    }

    /** Bitmap al, FloatArray döndür (softmax çıktı varsayılır) */
    fun run(bitmap: Bitmap): FloatArray {
        if (interpreter == null) {
            Log.e(TAG, "$modelFileName: interpreter null, sıfır döndürülüyor")
            return FloatArray(outputClasses)
        }
        val resized = bitmap.scale(inputWidth, inputHeight)
        val buffer  = toByteBuffer(resized)
        val isUint8Output = interpreter?.getOutputTensor(0)?.dataType() == DataType.UINT8

        return if (isUint8Output) {
            val out = ByteBuffer.allocateDirect(outputClasses).order(ByteOrder.nativeOrder())
            interpreter?.run(buffer, out)
            out.rewind()
            FloatArray(outputClasses) { (out.get().toInt() and 0xFF) / 255.0f }
        } else {
            val out = Array(1) { FloatArray(outputClasses) }
            interpreter?.run(buffer, out)
            out[0]
        }
    }

    private fun toByteBuffer(bitmap: Bitmap): ByteBuffer {
        val isUint8 = interpreter?.getInputTensor(0)?.dataType() == DataType.UINT8
        val bytes   = if (isUint8) 1 else 4
        val buf = ByteBuffer
            .allocateDirect(inputWidth * inputHeight * inputChannels * bytes)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (px in pixels) {
            val r = Color.red(px);  val g = Color.green(px);  val b = Color.blue(px)
            val gray = r * 0.299f + g * 0.587f + b * 0.114f
            if (isUint8) {
                if (inputChannels == 1) buf.put(gray.toInt().toByte())
                else { buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte()) }
            } else {
                if (inputChannels == 1) buf.putFloat(gray / 255f)
                else { buf.putFloat(r / 255f); buf.putFloat(g / 255f); buf.putFloat(b / 255f) }
            }
        }
        buf.rewind()
        return buf
    }

    fun isAvailable(): Boolean = try {
        context.assets.list("")?.contains(modelFileName) == true
    } catch (e: Exception) { false }

    fun close() { interpreter?.close(); interpreter = null }
}

// ─────────────────────────────────────────────
// 3 modeli yöneten ana sınıf
// Pipeline: var_yok → (VAR) evre_tespit | (YOK) risk_tespit
// ─────────────────────────────────────────────
class WoundAnalysisModel(private val context: Context) {

    companion object {
        const val MODEL_VAR_YOK = "wound_detection.tflite"
        const val MODEL_EVRE    = "wound_stage.tflite"
        const val MODEL_RISK    = "wound_risk.tflite"

    }

    private val varYokModel = TFLiteModel(context, MODEL_VAR_YOK)
    private val evreModel   = TFLiteModel(context, MODEL_EVRE)
    private val riskModel   = TFLiteModel(context, MODEL_RISK)


    /**
     * Ana analiz metodu.
     * 1) var_yok_tespit çalıştır
     * 2) Yara varsa → evre_tespit; yoksa → risk_tespit
     */
    fun analyze(bitmap: Bitmap): WoundAnalysisResult {
        return try {
            // ── Adım 1: Var / Yok ─────────────────────────
            val varYokOut = varYokModel.run(bitmap)
            logOutputs("var_yok", varYokOut)

            // Varsayım: index 0 = YOK, index 1 = VAR
            // Modelinizin eğitimindeki sınıf sırasına göre ayarlayın!
            val hasWound = if (varYokOut.size >= 2) {
                varYokOut[0] > varYokOut[1]  // index 0 = Yara_Var, index 1 = Yara_Yok
            } else {
                varYokOut[0] > 0.5f
            }
            val presenceConf = if (varYokOut.size >= 2) {
                if (hasWound) varYokOut[0] else varYokOut[1]
            } else varYokOut[0]

            val presenceResult = WoundPresenceResult(hasWound, presenceConf)
            Log.i(TAG, "Var/Yok → ${if (hasWound) "VAR" else "YOK"} (%.3f)".format(presenceConf))

            if (hasWound) {
                // ── Adım 2a: Evre tespiti ─────────────────
                val evreOut = evreModel.run(bitmap)
                logOutputs("evre", evreOut)
                val evreIdx  = evreOut.indices.maxByOrNull { evreOut[it] } ?: 0
                val evreConf = evreOut[evreIdx]
                Log.i(TAG, "Evre → ${evreIdx + 1} (%.3f)".format(evreConf))

                WoundAnalysisResult(
                    presenceResult = presenceResult,
                    stageResult    = WoundStageResult(evreIdx, evreConf),
                    isSuccess      = true
                )
            } else {
                // ── Adım 2b: Risk tespiti ─────────────────
                val riskOut = riskModel.run(bitmap)
                logOutputs("risk", riskOut)
                val riskIdx  = riskOut.indices.maxByOrNull { riskOut[it] } ?: 0
                val riskConf = riskOut[riskIdx]
                Log.i(TAG, "Risk → $riskIdx (%.3f)".format(riskConf))

                WoundAnalysisResult(
                    presenceResult = presenceResult,
                    riskResult     = WoundRiskResult(riskIdx, riskConf),
                    isSuccess      = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analiz hatası: ${e.message}")
            WoundAnalysisResult(isSuccess = false, errorMessage = "Analiz hatası: ${e.message}")
        }
    }

    private fun logOutputs(name: String, arr: FloatArray) {
        arr.forEachIndexed { i, v -> Log.d(TAG, "  $name[$i] = %.4f".format(v)) }
    }

    /** Her üç model dosyasının assets'te var olup olmadığını kontrol eder */
    fun checkAvailability(): Triple<Boolean, Boolean, Boolean> {
        val v = varYokModel.isAvailable()
        val e = evreModel.isAvailable()
        val r = riskModel.isAvailable()
        Log.i(TAG, "Model durumları → var_yok:$v | evre:$e | risk:$r")
        return Triple(v, e, r)
    }

    fun allModelsAvailable(): Boolean {
        val (v, e, r) = checkAvailability()
        return v && e && r
    }

    fun close() {
        varYokModel.close()
        evreModel.close()
        riskModel.close()
    }
}
