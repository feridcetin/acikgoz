package com.feridcetin.acikgoz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider // CameraX Referans hatası burada çözülmeli
import androidx.camera.view.PreviewView // XML'de kullanılır
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//import com.rmtheis.tess_two.TessBaseAPI // Tesseract OCR Referans hatası burada çözülmeli
import java.util.Locale
import java.util.concurrent.Executors
//import com.gregstoll.tesseract.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI

class AiEyeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var cameraProviderFuture: ProcessCameraProvider
    private lateinit var previewView: PreviewView
    private lateinit var ttsButton: ImageButton

    // OCR ve TFLite için yürütme havuzu
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val TESSERACT_LANG = "tur" // Türkçe dil paketi

    private val CAMERA_PERMISSION_REQUEST_CODE = 101

    // Geçici değişkenler
    private var isObjectDetectionEnabled = true
    private var tessApi: TessBaseAPI? = null

    // Durum takibi için
    private var lastTtsTime = 0L
    private val TTS_MIN_INTERVAL = 2000 // 2 saniye aralık

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_eye)

        tts = TextToSpeech(this, this)
        previewView = findViewById(R.id.preview_view)
        ttsButton = findViewById(R.id.btn_tts_command)

        // OCR motorunu başlat
        initializeTesseract()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        setupButtons()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Kamera Önizleme (Preview)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Görüntü Analizi (ImageAnalysis)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(720, 1280)) // Yüksek çözünürlük
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // En yeni kareyi kullan
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer(::handleAnalysisResult))
                }

            // Arka Kamera Seçimi
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Önceki kullanımları çöz
                cameraProvider.unbindAll()

                // Yeni kullanım durumlarını bağla
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e("AiEyeActivity", "Kullanım durumlarını bağlama başarısız oldu.", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun initializeTesseract() {
        tessApi = TessBaseAPI()

        // Tesseract veri yolunu ayarlayın. Veri dosyaları (tessdata),
        // uygulamanın assets klasöründen cihazın dahili depolamasına kopyalanmalıdır.
        try {
            // Örnek: getExternalFilesDir(null)?.absolutePath + "/tessdata"
            val dataPath = getExternalFilesDir(null)?.absolutePath
            if (dataPath != null) {
                tessApi?.init(dataPath, TESSERACT_LANG)
            }
        } catch (e: Exception) {
            Log.e("OCR", "Tesseract başlatılamadı: " + e.message)
        }
    }

    private fun handleAnalysisResult(result: AnalysisResult) {
        val currentTime = System.currentTimeMillis()
        // TTS spamini önlemek için aralık kontrolü
        if (currentTime - lastTtsTime < TTS_MIN_INTERVAL) {
            return
        }

        // Gerçek Analiz Sonuçlarının İşlenmesi
        when (result.type) {
            AnalysisResult.Type.OBJECT_DETECTION -> {
                val mostConfidentLabel = result.data.firstOrNull()?.label ?: ""
                speakStatus("Algılanan nesne: $mostConfidentLabel")
            }
            AnalysisResult.Type.OCR -> {
                val detectedText = result.data.firstOrNull()?.text ?: ""
                if (detectedText.length > 5) {
                    speakStatus("Okunan metin: $detectedText")
                }
            }
            AnalysisResult.Type.CURRENCY -> {
                val currencyValue = result.data.firstOrNull()?.text ?: ""
                speakStatus("Algılanan para: $currencyValue")
            }
            AnalysisResult.Type.NONE -> {
                // Hareketsizlik durumunda boş konuşma
            }
        }

        lastTtsTime = currentTime // Son konuşma zamanını güncelle
    }

    private fun speakStatus(message: String) {
        // Aynı metod Navigasyon ve HumanEye aktivitelerinde olduğu gibi
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupButtons() {
        // OCR Düğmesi
        findViewById<ImageButton>(R.id.btn_ocr).setOnClickListener {
            // Kameradan anlık fotoğraf çek ve OCR motoruna gönder
            performOcrScan()
        }

        // Özel Tanıma (Para/Renk) Düğmesi
        findViewById<ImageButton>(R.id.btn_special).setOnClickListener {
            // Basit bir geçiş veya menü açılabilir
            Toast.makeText(this, "Para Tanıma Moduna Geçiliyor...", Toast.LENGTH_SHORT).show()
            // Mod değişikliği burada yönetilir (örneğin, farklı bir TFLite modeli yüklenir).
        }
    }

    private fun performOcrScan() {
        // 1. Kameradan yüksek çözünürlüklü tek bir kare yakala (ImageCapture kullanılarak).
        // 2. Görüntüyü Bitmap'e dönüştür.
        // 3. ocrApi.setImage(bitmap) çağır.
        // 4. ocrApi.getUTF8Text() ile metni al.
        // 5. Metni TTS ile seslendir.
        tts.speak(getString(R.string.cd_ai_mode_ocr), TextToSpeech.QUEUE_FLUSH, null, null) // OCR işlemi başlatılıyor mesajı
        // ... OCR Mantığı Buraya Gelecek ...
        // tts.speak(ocrResult, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
        tessApi?.recycle() // Tesseract motorunu kapat
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("tr"))
            speakStatus(getString(R.string.ai_ready))
        } else {
            Log.e("TTS", "TTS Başlatılamadı.")
        }
    }
}

class ImageAnalyzer(private val listener: (AnalysisResult) -> Unit) : ImageAnalysis.Analyzer {

    // TFLite ve Tesseract (OCR) motorları burada başlatılmalıdır.
    // TFLite için: Load Model, Create Interpreter
    // Tesseract için: TessBaseAPI nesnesini kullanma

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Burası her kamera karesinde çalışır.
            // 1. Görüntüyü TFLite'a hazır hale getir
            // 2. TFLite ile nesne tanıma yap
            // 3. (Eğer nesne tanıma sonucu OCR'a uygunsa) Tesseract ile metin oku
            // 4. Sonucu listener'a gönder (handleAnalysisResult)

            // Varsayım: Basitleştirilmiş rastgele sonuç döndürelim
            val result = AnalysisResult(
                AnalysisResult.Type.OBJECT_DETECTION,
                listOf(AnalysisResult.Data("cep telefonu", 0.95f))
            )
            listener(result)

            imageProxy.close()
        }
    }
}

// Analiz sonucu için basit bir veri sınıfı
data class AnalysisResult(val type: Type, val data: List<Data>) {
    enum class Type { OBJECT_DETECTION, OCR, CURRENCY, NONE }
    data class Data(val label: String, val confidence: Float) {
        val text: String
            get() = label // OCR sonuçları için label yerine text kullanılıyor
    }
}