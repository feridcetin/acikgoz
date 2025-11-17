package com.feridcetin.acikgoz

import android.graphics.*
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Ana yapay zeka analizörü. Kamera karelerini alır ve aktif moda göre işler.
 *
 * @param activity TTS çağrıları ve UI güncellemeleri için AiEyeActivity referansı.
 */
class AiAnalyzer(private val activity: AiEyeActivity) : ImageAnalysis.Analyzer {

    // Aktif modu tutar
    var currentMode: AiEyeActivity.SpecialMode = AiEyeActivity.SpecialMode.NONE

    // Para Birimi Modeli
    private val currencyModelFileName = "tl_banknot_detector.tflite"
    private var objectDetector: ObjectDetector? = null

    // Analiz devam ederken tekrar konuşmayı engellemek için
    private val isAnalyzing = AtomicBoolean(false)

    // Tekrar tekrar aynı etiketi okumayı önlemek için
    private var lastSpokenLabel: String = ""

    // Analiz sıklığını kontrol etmek için
    private var lastAnalysisTime = 0L
    // Analiz sıklığı 1 saniyeden 500ms'ye düşürüldü (daha tepkisel olması için)
    private val frameSkipInterval = 500L

    init {
        setupObjectDetector()
    }

    // ---------------- Model Kurulumu ----------------

    private fun setupObjectDetector() {
        try {
            val options = ObjectDetectorOptions.builder()
                .setMaxResults(1)
                .setScoreThreshold(0.7f)
                .build()

            // Model dosyasını assets'ten yükle
            objectDetector = ObjectDetector.createFromFileAndOptions(activity, currencyModelFileName, options)
            Log.i("AiAnalyzer", "TFLite Object Detector başarıyla yüklendi: $currencyModelFileName")

        } catch (e: Exception) {
            Log.e("AiAnalyzer", "TFLite model yüklenirken hata oluştu: $currencyModelFileName", e)
            activity.speakStatus("Para birimi tanıma başlatılamadı. Model dosyası eksik veya bozuk.")
        }
    }

    // ---------------- Kamera Karesi Analizi ----------------

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mode = currentMode
        val currentTime = System.currentTimeMillis()

        // Sıklık Kontrolü ve Serbest Bırakma
        // NONE modunda ve çok sık gelen karelerde analiz yapma.
        if (mode == AiEyeActivity.SpecialMode.NONE || currentTime - lastAnalysisTime < frameSkipInterval || isAnalyzing.getAndSet(true)) {
            image.close()
            return
        }

        lastAnalysisTime = currentTime

        try {
            when (mode) {
                AiEyeActivity.SpecialMode.CURRENCY_DETECTION -> {
                    // Para Birimi Analizi (TFLite Object Detector kullanır)
                    performCurrencyDetection(image)
                }
                AiEyeActivity.SpecialMode.COLOR_DETECTION -> {
                    // Renk Analizi (Basit ortalama renk algoritması kullanır)
                    performColorDetection(image)
                }
                AiEyeActivity.SpecialMode.OCR -> {
                    // Sürekli OCR (Placeholder mesajı)
                    performOcrPlaceholder()
                }
                AiEyeActivity.SpecialMode.NONE -> {
                    // NONE modunda zaten yukarıda erken çıkış yapılıyor
                }
            }
        } catch (e: Exception) {
            Log.e("AiAnalyzer", "Genel Analiz Hatası", e)
        } finally {
            image.close() // Kareyi kapat
            isAnalyzing.set(false) // Analizi serbest bırak
        }
    }

    // ---------------- Renk Tanıma Mantığı ----------------

    @OptIn(ExperimentalGetImage::class)
    private fun performColorDetection(image: ImageProxy) {
        val bitmap = image.toBitmap() ?: return

        // 1. Görüntüyü döndürme işlemini ImageProcessor ile uygula
        var tensorImage = TensorImage.fromBitmap(bitmap)
        val rotation = image.imageInfo.rotationDegrees / 90
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(rotation))
            .build()
        tensorImage = imageProcessor.process(tensorImage)

        val rotatedBitmap = tensorImage.bitmap
        val colorName = getAverageColorName(rotatedBitmap)

        val spokenText = "$colorName algılandı."

        // Sadece yeni veya farklı bir renk algılandığında TTS'i tetikle
        if (colorName != lastSpokenLabel) {
            activity.speakStatus(spokenText)
            lastSpokenLabel = colorName
        }
    }

    /**
     * Bitmap'in merkez bölgesindeki ortalama rengi hesaplar ve Türkçe adını döndürür.
     * Renk haritası daha geniş bir yelpazeyi kapsayacak şekilde güncellenmiştir.
     */
    private fun getAverageColorName(bitmap: Bitmap): String {
        val size = 50 // Merkezde 50x50 piksellik bir alan al
        val startX = (bitmap.width - size) / 2
        val startY = (bitmap.height - size) / 2

        // Güvenli bölge kontrolü
        if (startX < 0 || startY < 0 || startX + size > bitmap.width || startY + size > bitmap.height) {
            return "Renk (Alan çok küçük)"
        }

        var rSum: Long = 0
        var gSum: Long = 0
        var bSum: Long = 0
        var pixelCount = 0

        for (x in startX until startX + size) {
            for (y in startY until startY + size) {
                val color = bitmap.getPixel(x, y)
                rSum += Color.red(color)
                gSum += Color.green(color)
                bSum += Color.blue(color)
                pixelCount++
            }
        }

        if (pixelCount == 0) return "Bilinmiyor"

        val avgR = (rSum / pixelCount).toInt()
        val avgG = (gSum / pixelCount).toInt()
        val avgB = (bSum / pixelCount).toInt()

        // Genişletilmiş renk haritası (Daha fazla yaygın rengi içerir)
        val colorMap = mapOf(
            "Beyaz" to Color.rgb(255, 255, 255),
            "Siyah" to Color.rgb(0, 0, 0),
            "Kırmızı" to Color.rgb(255, 0, 0),
            "Yeşil" to Color.rgb(0, 255, 0),
            "Mavi" to Color.rgb(0, 0, 255),
            "Sarı" to Color.rgb(255, 255, 0),
            "Turuncu" to Color.rgb(255, 165, 0),
            "Mor" to Color.rgb(128, 0, 128),
            "Gri" to Color.rgb(128, 128, 128),
            "Pembe" to Color.rgb(255, 192, 203),
            "Açık Mavi (Cyan)" to Color.rgb(0, 255, 255),
            "Bordo (Maroon)" to Color.rgb(128, 0, 0),
            "Koyu Yeşil (Dark Green)" to Color.rgb(0, 128, 0),
            "Lacivert (Navy)" to Color.rgb(0, 0, 128),
            "Zeytin Yeşili (Olive)" to Color.rgb(128, 128, 0),
            "Mor (Purple)" to Color.rgb(128, 0, 128), // Tekrar Mor (Daha standart)
            "Deniz Mavisi (Teal)" to Color.rgb(0, 128, 128),
            "Açık Yeşil (Lime)" to Color.rgb(50, 205, 50),
            "Kahverengi" to Color.rgb(165, 42, 42),
            "Altın" to Color.rgb(255, 215, 0),
            "Gümüş" to Color.rgb(192, 192, 192),
            "Bej" to Color.rgb(245, 245, 220),
            "Açık Gri" to Color.rgb(211, 211, 211),
            "Koyu Gri" to Color.rgb(105, 105, 105)
        )

        var minDistance = Double.MAX_VALUE
        var closestColor = "Bilinmeyen Renk"

        for ((name, colorValue) in colorMap) {
            val r = Color.red(colorValue)
            val g = Color.green(colorValue)
            val b = Color.blue(colorValue)

            // Renkler arasındaki mesafe (Euclidean distance)
            val distance = sqrt(
                (avgR - r) * (avgR - r) +
                        (avgG - g) * (avgG - g) +
                        (avgB - b) * (avgB - b).toDouble()
            )

            if (distance < minDistance) {
                minDistance = distance
                closestColor = name
            }
        }

        return closestColor
    }

    // ---------------- Para Birimi Tanıma Mantığı ----------------

    @OptIn(ExperimentalGetImage::class)
    private fun performCurrencyDetection(image: ImageProxy) {
        // Modelin yüklenip yüklenmediğini kontrol et
        val detector = objectDetector ?: run {
            Log.e("AiAnalyzer", "Object Detector nesnesi null. Model yüklenememiş.")
            // Model yüklenmediyse daha fazla işlem yapma
            return
        }

        // 1. Görüntüyü dönüştürme (ImageProxy -> Bitmap -> TensorImage)
        val bitmap = image.toBitmap() ?: return

        // TensorImage'ı Bitmap'ten oluştur
        var tensorImage = TensorImage.fromBitmap(bitmap)

        // 2. Görüntüyü döndürme işlemini ImageProcessor ile uygula (KameraX ile uyum için)
        val rotation = image.imageInfo.rotationDegrees / 90
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(rotation))
            .build()

        tensorImage = imageProcessor.process(tensorImage)

        // 3. Modeli çalıştırma
        val results = detector.detect(tensorImage)

        // 4. Sonuçları işleme
        if (!results.isNullOrEmpty()) {
            val topResult = results[0]
            val label = topResult.categories[0].label
            val confidence = topResult.categories[0].score * 100

            val spokenText = "$label algılandı. Güven: ${confidence.toInt()} yüzde."

            // Sadece yeni veya farklı bir şey algılandığında TTS'i tetikle
            if (label != lastSpokenLabel) {
                activity.speakStatus(spokenText)
                lastSpokenLabel = label
                Log.d("AiAnalyzer", "Para birimi algılandı: $label")
            }
        } else {
            // Hiçbir şey algılanmazsa etiketi sıfırla
            if (lastSpokenLabel.isNotEmpty()) {
                Log.d("AiAnalyzer", "Para birimi algılanmadı. (Eşik altında veya görünürde yok)")
            }
            lastSpokenLabel = ""
        }
    }

    // ---------------- OCR Placeholder Mantığı ----------------

    private fun performOcrPlaceholder() {
        // Bu mod anlık yakalama (performOcrScan) yerine sürekli analiz için tasarlanmıştır.
        // NOT: Gerçek OCR (Tesseract) burada implemente edilmelidir.
        val message = "Sürekli Metin Okuma aktif. Lütfen kamerayı metne odaklayın."
        if (lastSpokenLabel != "OCR_ACTIVE") {
            activity.speakStatus(message)
            lastSpokenLabel = "OCR_ACTIVE"
        }
    }
}

// ---------------- ImageProxy'den Bitmap'e Dönüşüm (Extension Function) ----------------

/**
 * ImageProxy nesnesini Bitmap'e dönüştüren yardımcı (Extension) fonksiyon.
 * TFLite'a beslemek için gereklidir.
 */
@OptIn(ExperimentalGetImage::class)
fun ImageProxy.toBitmap(): Bitmap? {
    val image = this.image ?: return null

    // YUV verisini al
    val planes = image.planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // YUV verisini NV21 formatına dönüştürme
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    // YUV verisinden JPEG oluşturma
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    // Sıkıştırma sırasında Rect kullanmak gerekebilir, ancak tam boyutlu kullanmak daha güvenli
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()

    // JPEG verisinden Bitmap oluştur
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}