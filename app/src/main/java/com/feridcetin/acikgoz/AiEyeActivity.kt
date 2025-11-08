package com.feridcetin.acikgoz

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView // TextView eklendi
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class AiEyeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var previewView: PreviewView
    private lateinit var ttsButton: ImageButton
    private lateinit var statusDisplay: TextView // Durum metni için eklendi

    // OCR ve TFLite için yürütme havuzu
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val TESSERACT_LANG = "tur" // Türkçe dil paketi

    private val CAMERA_PERMISSION_REQUEST_CODE = 101

    // Geçici değişkenler
    private var isObjectDetectionEnabled = true
    private var tessApi: TessBaseAPI? = null

    private var imageCapture: ImageCapture? = null

    // Durum takibi için
    private var lastTtsTime = 0L
    private val TTS_MIN_INTERVAL = 2000 // 2 saniye aralık

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_eye)

        tts = TextToSpeech(this, this)
        previewView = findViewById(R.id.preview_view)
        ttsButton = findViewById(R.id.btn_tts_command)
        statusDisplay = findViewById(R.id.tv_status_display) // XML'den çekildi

        copyTessdataFiles() // Dil dosyasını dahili depolamaya kopyala

        // OCR motorunu başlat (Şimdi dosya yerinde olmalı)
        initializeTesseract()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        setupButtons()
        setupOnBackPressedListener()

    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Kamera izni gereklidir.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 1. 💡 preview nesnesinin tanımı burada olmalı
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. ImageCapture nesnesinin tanımı
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            // 3. ImageAnalysis nesnesinin tanımı
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(720, 1280))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer(::handleAnalysisResult))
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                // 4. Bağlama (Burada preview'ı kullanıyoruz)
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer, imageCapture
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
                Log.i("OCR", "Tesseract başarıyla başlatıldı: $dataPath")
            }
        } catch (e: Exception) {
            Log.e("OCR", "Tesseract başlatılamadı: " + e.message)
            Toast.makeText(this, "OCR motoru başlatılamadı.", Toast.LENGTH_LONG).show()
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
                if (mostConfidentLabel.isNotBlank()) {
                    speakStatus("Algılanan nesne: $mostConfidentLabel")
                }
            }
            AnalysisResult.Type.OCR -> {
                val detectedText = result.data.firstOrNull()?.text ?: ""
                if (detectedText.length > 5) {
                    speakStatus("Okunan metin: $detectedText")
                }
            }
            AnalysisResult.Type.CURRENCY -> {
                val currencyValue = result.data.firstOrNull()?.text ?: ""
                if (currencyValue.isNotBlank()) {
                    speakStatus("Algılanan para: $currencyValue")
                }
            }
            AnalysisResult.Type.NONE -> {
                // Hareketsizlik durumunda boş konuşma
            }
        }

        lastTtsTime = currentTime // Son konuşma zamanını güncelle
    }

    fun speakStatus(message: String) {
        // 💡 Görsel Durum Metnini Güncelle
        runOnUiThread {
            statusDisplay.text = message
        }

        // TextToSpeech kullanımı
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun setupButtons() {

        // Geri Dön Düğmesi
        findViewById<ImageButton>(R.id.btn_back_to_main).setOnClickListener {
            // Activity'yi kapatarak MainActivity'ye geri dön
            //finish()
            speakAndFinish(R.string.app_closing_message)
        }

        // OCR Düğmesi
        findViewById<ImageButton>(R.id.btn_ocr).setOnClickListener {
            performOcrScan()
        }

        // Özel Tanıma (Para/Renk) Düğmesi
        findViewById<ImageButton>(R.id.btn_special).setOnClickListener {
            Toast.makeText(this, "Özel Tanıma Moduna Geçiliyor...", Toast.LENGTH_SHORT).show()
        }

        // TTS Komut Düğmesi (Tekrar oku/Mikrofon)
        ttsButton.setOnClickListener {
            // Son okunan metni veya mevcut durumu tekrar okutabiliriz.
            speakStatus("Tekrar okuma komutu verildi.")
        }
    }

    private fun performOcrScan() {
        val currentImageCapture = imageCapture ?: run {
            speakStatus("Kamera servisi hazır değil.")
            return
        }

        // Kullanıcıya işlemi başlattığını sesli olarak bildir
        speakStatus(getString(R.string.cd_ai_mode_ocr_start))

        // Görüntüyü kaydetmek için geçici bir dosya oluştur
        val photoFile = File(externalMediaDirs.firstOrNull(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 1. Görüntüyü Yakala (Asenkron)
        currentImageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this), // Ana iş parçacığında dinle
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("OCR_Capture", "Görüntü yakalama hatası: ${exc.message}", exc)
                    speakStatus("Görüntü yakalanırken bir hata oluştu.")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 2. Yakalanan görüntüyü arka planda OCR için işle
                    output.savedUri?.path?.let { filePath ->
                        cameraExecutor.execute {
                            processOcrImage(filePath)
                        }
                    }
                }
            })
    }

    private fun processOcrImage(imagePath: String) {
        var ocrResult = "Metin algılanamadı."

        try {
            val bitmap = BitmapFactory.decodeFile(imagePath) // Yakalanan dosyayı Bitmap'e dönüştür

        // 💡 GÜNCELLEME: Bitmap'i Tesseract'a göndermeden önce ön işlemeden geçir
            val preprocessedBitmap = preprocessBitmap(bitmap)

            tessApi?.let { api ->
                api.setImage(bitmap) // Tesseract'a görüntüyü gönder
                ocrResult = api.getUTF8Text() // Metni al

                // Eğer metin başarıyla alındıysa, sonucu ana iş parçacığında seslendir
                if (ocrResult.isNotBlank() && ocrResult.length > 5) {
                    // Sonucu, TTS ile okuması için ana iş parçacığına gönder
                    runOnUiThread {
                        speakStatus("Okunan metin: $ocrResult")
                    }
                } else {
                    runOnUiThread {
                        speakStatus("Karede net bir metin algılanamadı.")
                    }
                }
                preprocessedBitmap.recycle()
            }

            // Belleği serbest bırak
            bitmap.recycle()
            // Geçici dosyayı sil
            File(imagePath).delete()

        } catch (e: Exception) {
            Log.e("OCR_Process", "OCR işlemi sırasında hata: ${e.message}", e)
            runOnUiThread {
                speakStatus("OCR motoru bir sorunla karşılaştı.")
            }
        }
    }

    private fun copyTessdataFiles() {
        try {
            val assetManager = assets
            val files = assetManager.list("tessdata") // assets/tessdata altındaki dosyaları listele

            if (files.isNullOrEmpty()) {
                Log.e("OCR_Copy", "Assets/tessdata klasörü boş veya bulunamadı.")
                return
            }

            val dataPath = getExternalFilesDir(null)?.absolutePath // Tesseract'ın beklediği ana dizin
            val tessdataDir = File(dataPath, "tessdata")

            if (!tessdataDir.exists()) {
                tessdataDir.mkdirs() // Eğer yoksa tessdata klasörünü oluştur
            }

            // Tüm dosyaları kopyala
            for (filename in files) {
                val destFile = File(tessdataDir, filename)
                if (!destFile.exists()) {
                    assetManager.open("tessdata/$filename").use { inputStream ->
                        FileOutputStream(destFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.i("OCR_Copy", "$filename başarıyla kopyalandı.")
                }
            }
        } catch (e: Exception) {
            Log.e("OCR_Copy", "Tessdata kopyalama hatası: " + e.message)
        }
    }

    /**
     * OCR için Bitmap'i iyileştirir: Gri tonlama ve basit ikili hale getirme (binarization).
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Eşik değeri (threshold) belirleme
        val threshold = 128 // 0-255 aralığında. Bu değer, siyah mı beyaz mı olacağına karar verir.

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)

                // 1. Gri Tonlamaya Dönüştürme (Luminosity metodu)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt() // Luminosity

                // 2. İkili Hale Getirme (Binarization)
                val outputColor = if (gray < threshold) Color.BLACK else Color.WHITE

                resultBitmap.setPixel(x, y, outputColor)
            }
        }
        return resultBitmap
    }

    // Ekran kapanmadan önce sesli uyarı yapmak için yeni bir metod
    private fun speakAndFinish(messageResId: Int) {
        val message = getString(messageResId)

        // 1. Sesi çal
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // 2. TTS'nin bitmesini beklemek yerine, kısa bir gecikme ile aktiviteyi kapat
        // Not: Bu, tts'nin konuşmayı bitirmesi için kaba bir tahmindir.
        // Daha kesin çözüm için UtteranceProgressListener kullanmak gerekir,
        // ancak basitlik için gecikme kullanıyoruz.
        previewView.postDelayed({
            super.finish() // Aktiviteyi güvenle kapat
        }, 1500) // 1.5 saniye bekle
    }

    private fun setupOnBackPressedListener() {
        // Geri tuşu/hareketi algılandığında çalışacak anonim sınıf
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Geri tuşuna basıldığında eski onBackPressed mantığını çağır.
                // Bu, TTS'yi çalar ve gecikmeli olarak aktiviteyi kapatır.
                speakAndFinish(R.string.app_closing_message)
            }
        }

        // Geri çağrıyı aktivitenin yaşam döngüsüne bağla
        onBackPressedDispatcher.addCallback(this, callback)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

        // TTS kaynaklarını serbest bırak
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
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

// ----------------------------------------------------------------------------------
// Görüntü Analiz Sınıfı (Yapay Zeka Mantığı Buradadır)
// ----------------------------------------------------------------------------------

class ImageAnalyzer(private val listener: (AnalysisResult) -> Unit) : ImageAnalysis.Analyzer {

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {

            // !!! BU KISIM SİLİNMELİ veya YORUMA ALINMALI !!!
            /*
            val result = AnalysisResult(
                AnalysisResult.Type.OBJECT_DETECTION,
                listOf(AnalysisResult.Data("cep telefonu", 0.95f))
            )
            listener(result) // Bu, AiEyeActivity'deki speakStatus'u tetikler.
            */

            // Gerçek görüntü işleme mantığı buraya eklendiğinde,
            // sadece anlamlı bir sonuç varsa 'listener' çağrılmalıdır.

            imageProxy.close()
        }
    }
}

// Analiz sonucu için basit bir veri sınıfı
data class AnalysisResult(val type: Type, val data: List<Data>) {
    enum class Type { OBJECT_DETECTION, OCR, CURRENCY, NONE }
    data class Data(val label: String, val confidence: Float) {
        val text: String
            get() = label // OCR sonuçları için label yerine text kullanılır
    }
}