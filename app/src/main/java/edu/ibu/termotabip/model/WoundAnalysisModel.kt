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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set

private const val TAG = "WoundAnalysisModel"

class WoundAnalysisModel(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var modelFile = "tflitemodel.tflite"
    
    // Model boyutları - Otomatik tespit edilecek
    private var modelInputWidth = 224
    private var modelInputHeight = 224
    private var modelInputChannels = 1  // Gri tonlamalı (1 kanal)
    private var modelOutputClasses = 3  // Varsayılan sınıf sayısı
    
    // Sınıf adları
    private val classNames = arrayOf("Düşük Risk", "Orta Risk", "Yüksek Risk")
    
    init {
        loadModel()
        detectModelParameters()
    }
    
    private fun loadModel() {
        try {
            val options = Interpreter.Options()
            options.setNumThreads(4)
            
            val modelBuffer = loadModelFile(context, modelFile)
            interpreter = Interpreter(modelBuffer, options)
            
            Log.d(TAG, "Model başarıyla yüklendi: $modelFile")
        } catch (e: Exception) {
            Log.e(TAG, "Model yüklenirken hata oluştu: ${e.message}")
        }
    }
    
    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun detectModelParameters() {
        try {
            interpreter?.let { interp ->
                // Giriş boyutlarını tespit et
                val inputTensor = interp.getInputTensor(0)
                val inputShape = inputTensor.shape()
                
                if (inputShape.size >= 3) {
                    modelInputHeight = inputShape[1]
                    modelInputWidth = inputShape[2]
                    modelInputChannels = if (inputShape.size > 3) inputShape[3] else 1
                }
                
                // Çıktı boyutlarını tespit et
                val outputTensor = interp.getOutputTensor(0)
                val outputShape = outputTensor.shape()
                
                // Çıktı şekli [1, sınıf_sayısı] veya [sınıf_sayısı] olabilir
                modelOutputClasses = if (outputShape.size > 1) outputShape[1] else outputShape[0]
                
                Log.d(TAG, "Model parametreleri tespit edildi: " +
                        "Giriş: ${inputShape.contentToString()}, " +
                        "Çıktı: ${outputShape.contentToString()}, " +
                        "Sınıf sayısı: $modelOutputClasses")
                
                // Veri tiplerini kontrol et
                Log.d(TAG, "Giriş veri tipi: ${inputTensor.dataType()}, " +
                        "Çıktı veri tipi: ${outputTensor.dataType()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model parametreleri tespit edilirken hata: ${e.message}")
        }
    }
    
    fun analyzeWound(bitmap: Bitmap): WoundAnalysisResult {
        if (interpreter == null) {
            return WoundAnalysisResult(
                woundLevel = -1,
                confidence = 0f,
                isSuccess = false,
                errorMessage = "Model henüz yüklenmedi"
            )
        }
        
        try {
            // Görüntüyü model boyutuna yeniden boyutlandır
            val processedBitmap = preprocessImage(bitmap)
            
            // ByteBuffer'a dönüştür
            val inputBuffer = convertBitmapToByteBuffer(processedBitmap)
            
            // Çıktı veri tipini kontrol et
            val isUint8Output = interpreter?.getOutputTensor(0)?.dataType() == DataType.UINT8
            
            // Çıktı için uygun buffer oluştur
            val outputBuffer: Any = if (isUint8Output) {
                ByteBuffer.allocateDirect(modelOutputClasses).order(ByteOrder.nativeOrder())
            } else {
                Array(1) { FloatArray(modelOutputClasses) }
            }
            
            // Modeli çalıştır
            Log.d(TAG, "Model çalıştırılıyor... Buffer pozisyonu: ${inputBuffer.position()}, Kapasite: ${inputBuffer.capacity()}")
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Sonuçları işle
            var maxIndex = 0
            var maxConfidence = 0f
            
            if (isUint8Output) {
                val byteBuffer = outputBuffer as ByteBuffer
                byteBuffer.rewind()
                
                Log.d(TAG, "Model çıktıları (UINT8):")
                for (i in 0 until modelOutputClasses) {
                    val value = byteBuffer.get().toInt() and 0xFF
                    val confidence = value / 255.0f
                    
                    // Sadece ilk 10 ve en yüksek değerleri logla
                    if (i < 10 || confidence > 0.1f) {
                        Log.d(TAG, "Sınıf $i: $value (${confidence * 100.0f}%)")
                    }
                    
                    if (confidence > maxConfidence) {
                        maxConfidence = confidence
                        maxIndex = i
                    }
                }
            } else {
                val results = (outputBuffer as Array<FloatArray>)[0]
                
                Log.d(TAG, "Model çıktıları (FLOAT32):")
                for (i in results.indices) {
                    val confidence = results[i]
                    
                    // Sadece ilk 10 ve en yüksek değerleri logla
                    if (i < 10 || confidence > 0.1f) {
                        Log.d(TAG, "Sınıf $i: $confidence (${confidence * 100.0f}%)")
                    }
                    
                    if (confidence > maxConfidence) {
                        maxConfidence = confidence
                        maxIndex = i
                    }
                }
            }
            
            // Eğer çok fazla sınıf varsa ve bizim modelimiz 3 sınıflı ise
            // Sınıf indeksini 3'e göre modülo alarak düzeltebiliriz
            val actualClassCount = 3 // Gerçek sınıf sayınız
            val mappedIndex = if (modelOutputClasses > actualClassCount) {
                maxIndex % actualClassCount
            } else {
                maxIndex
            }
            
            Log.d(TAG, "Analiz sonucu: ${classNames.getOrElse(mappedIndex) { "Bilinmeyen (Seviye $maxIndex)" }}, Güven: $maxConfidence")
            
            return WoundAnalysisResult(
                woundLevel = mappedIndex,
                confidence = maxConfidence,
                isSuccess = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Analiz sırasında hata oluştu: ${e.message}")
            e.printStackTrace()
            
            return WoundAnalysisResult(
                woundLevel = -1,
                confidence = 0f,
                isSuccess = false,
                errorMessage = "Analiz sırasında hata: ${e.message}"
            )
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Görüntüyü model boyutuna yeniden boyutlandır
        val resizedBitmap = bitmap.scale(modelInputWidth, modelInputHeight)
        
        // Görüntüyü gri tonlamaya dönüştür (eğer model gri tonlamalı ise)
        return if (modelInputChannels == 1) {
            convertToGrayscale(resizedBitmap)
        } else {
            resizedBitmap
        }
    }
    
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = createBitmap(width, height)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap[x, y]
                
                // Gri tonlama formülü: Y = 0.299R + 0.587G + 0.114B
                val grayValue = (Color.red(pixel) * 0.299f + 
                               Color.green(pixel) * 0.587f + 
                               Color.blue(pixel) * 0.114f).toInt()
                
                // Alfa kanalını koru
                val alpha = Color.alpha(pixel)
                val grayPixel = Color.argb(alpha, grayValue, grayValue, grayValue)
                grayBitmap[x, y] = grayPixel
            }
        }
        
        return grayBitmap
    }
    
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Modelin beklediği veri tipini kontrol et
        val isUint8 = interpreter?.getInputTensor(0)?.dataType() == DataType.UINT8
        
        // Doğru boyutta ByteBuffer oluştur
        val bytesPerChannel = if (isUint8) 1 else 4
        val inputBuffer = ByteBuffer.allocateDirect(modelInputWidth * modelInputHeight * modelInputChannels * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(modelInputWidth * modelInputHeight)
        bitmap.getPixels(pixels, 0, modelInputWidth, 0, 0, modelInputWidth, modelInputHeight)
        
        inputBuffer.rewind()
        for (pixel in pixels) {
            if (isUint8) {
                // UINT8 formatı için (0-255 aralığında)
                if (modelInputChannels == 1) {
                    // Gri tonlamalı (1 kanal)
                    val grayValue = (Color.red(pixel) * 0.299f + 
                                    Color.green(pixel) * 0.587f + 
                                    Color.blue(pixel) * 0.114f).toInt()
                    inputBuffer.put(grayValue.toByte())
                } else {
                    // RGB (3 kanal)
                    inputBuffer.put(Color.red(pixel).toByte())
                    inputBuffer.put(Color.green(pixel).toByte())
                    inputBuffer.put(Color.blue(pixel).toByte())
                }
            } else {
                // FLOAT32 formatı için (0-1 aralığında)
                if (modelInputChannels == 1) {
                    // Gri tonlamalı (1 kanal)
                    val grayValue = (Color.red(pixel) * 0.299f + 
                                    Color.green(pixel) * 0.587f + 
                                    Color.blue(pixel) * 0.114f) / 255.0f
                    inputBuffer.putFloat(grayValue)
                } else {
                    // RGB (3 kanal)
                    inputBuffer.putFloat(Color.red(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.green(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.blue(pixel) / 255.0f)
                }
            }
        }
        
        inputBuffer.rewind()
        Log.d(TAG, "ByteBuffer oluşturuldu: Pozisyon=${inputBuffer.position()}, Limit=${inputBuffer.limit()}, " +
                "Kapasite=${inputBuffer.capacity()}, Veri tipi=${if (isUint8) "UINT8" else "FLOAT32"}")
        return inputBuffer
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
    
    // Model dosyasının varlığını kontrol eden yardımcı metod
    fun isModelAvailable(context: Context): Boolean {
        return try {
            val assetManager = context.assets
            val files = assetManager.list("")
            val exists = files?.contains(modelFile) == true
            Log.d(TAG, "Model dosyası kontrol ediliyor: $modelFile, Mevcut: $exists")
            if (exists) {
                Log.d(TAG, "Assets içindeki dosyalar: ${files?.joinToString()}")
            } else {
                Log.e(TAG, "Model dosyası bulunamadı! Assets içindeki dosyalar: ${files?.joinToString()}")
            }
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Model dosyası kontrol edilirken hata: ${e.message}")
            false
        }
    }
} 