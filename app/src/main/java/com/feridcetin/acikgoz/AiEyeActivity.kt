package com.feridcetin.acikgoz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AiEyeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var previewView: PreviewView
    private lateinit var statusDisplay: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalysis: ImageAnalysis

    // Varsayımsal Analizör Sınıfı Yönetimi için değişkenler
    private var currentSpecialMode: SpecialMode = SpecialMode.NONE
    private lateinit var aiAnalyzer: AiAnalyzer // Tüm yapay zeka mantığını yürüten ana analizör

    private val REQUEST_CODE_SPEECH_INPUT = 100
    private val TAG = "AiEyeActivity"
    private var lastSpokenText: String = "" // Tekrar oku komutu için son okunan metni tutar

    // Özel Mod Enum'u
    enum class SpecialMode {
        NONE, // Normal AI Göz modu
        COLOR_DETECTION,
        CURRENCY_DETECTION,
        OCR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_eye)

        // UI Bağlantıları
        previewView = findViewById(R.id.preview_view)
        statusDisplay = findViewById(R.id.tv_status_display)

        // Executor başlat
        cameraExecutor = Executors.newSingleThreadExecutor()

        // TTS ve Listener başlatma
        tts = TextToSpeech(this, this)
        setupTtsListener()

        // Kamera izinlerini kontrol et ve başlat
        // (İzin kontrol mekanizmalarının dışarıda kurulduğu varsayılmıştır)
        startCamera()

        // Butonları Ayarla
        setupButtons()
    }

    // ---------------- TTS BAŞLATMA VE DİNLEYİCİ ----------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("tr"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Türkçe dil paketi eksik veya desteklenmiyor.")
            } else {
                speakStatus(getString(R.string.ai_ready))
            }
        } else {
            Log.e(TAG, "TTS Başlatma başarısız oldu.")
        }
    }

    private fun setupTtsListener() {
        val listener = object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "APP_CLOSING") {
                    runOnUiThread {
                        safeAppShutdown()
                    }
                }
            }
            override fun onError(utteranceId: String?) {
                if (utteranceId == "APP_CLOSING") {
                    runOnUiThread {
                        safeAppShutdown()
                    }
                }
            }
            override fun onStart(utteranceId: String?) {}
        }
        tts.setOnUtteranceProgressListener(listener)
    }

    fun speakStatus(message: String) {
        lastSpokenText = message // Tekrar okuma için kaydet
        runOnUiThread {
            statusDisplay.text = message
        }
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // ---------------- KAMERA VE AI ANALİZİ ----------------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Analizör kurulumu (İlk başta NONE modunda başlatılır)
            aiAnalyzer = AiAnalyzer()
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, aiAnalyzer)

            // Kamera Seçimi
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Bağlamadan önce tüm use case'leri çöz
                cameraProvider.unbindAll()

                // Use case'leri bağla
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------- BUTON VE KOMUT YÖNETİMİ ----------------

    private fun setupButtons() {
        // Geri Dön Düğmesi
        findViewById<ImageButton>(R.id.btn_back_to_main).setOnClickListener {
            finish() // Ana ekrana geri dön
        }

        // OCR Düğmesi (Tek Kare Metin Yakalama)
        findViewById<ImageButton>(R.id.btn_ocr).setOnClickListener {
            // Sürekli analiz moduna geçmek yerine, anlık bir OCR işlemi tetiklenmeli
            performOcrScan()
        }

        // TTS Komut Düğmesi (R.id.btn_tts_command)
        findViewById<ImageButton>(R.id.btn_tts_command).setOnClickListener {
            // KISA BASMA: Son okunan metni tekrarlar
            speakStatus(lastSpokenText.ifBlank { getString(R.string.ai_ready) })
        }

        // UZUN BASMA: Yeni sesli komut dinlemeyi başlatır
        findViewById<ImageButton>(R.id.btn_tts_command).setOnLongClickListener {
            promptSpeechInput()
            true
        }

        // Özel Tanıma Düğmesi (R.id.btn_special)
        findViewById<ImageButton>(R.id.btn_special).setOnClickListener {
            // Şu anki özel mod COLOR_DETECTION ise CURRENCY_DETECTION'a geç, değilse COLOR_DETECTION'a geç
            val nextMode = if (currentSpecialMode == SpecialMode.COLOR_DETECTION) {
                SpecialMode.CURRENCY_DETECTION
            } else {
                SpecialMode.COLOR_DETECTION
            }
            startSpecialRecognitionMode(nextMode)
        }
    }

    private fun performOcrScan() {
        // NOTE: Gerçek uygulamada burada ImageCapture use case'i kullanılır ve tek kare yakalanır.
        speakStatus(getString(R.string.cd_ai_mode_ocr_start))
        Toast.makeText(this, "OCR Yakalama Tetiklendi. Analiz ediliyor...", Toast.LENGTH_SHORT).show()
    }

    // ---------------- MOD GEÇİŞ MANTIĞI ----------------

    /**
     * Uygulamanın AI analiz modunu değiştirir ve TTS ile durumu bildirir.
     * Bu metot, aiAnalyzer'ın çalışma modunu değiştirerek analiz mantığını yönlendirir.
     */
    private fun startSpecialRecognitionMode(mode: SpecialMode) {
        if (currentSpecialMode == mode) {
            speakStatus("Zaten ${getModeName(mode)} modundasınız.")
            return
        }

        currentSpecialMode = mode
        val message: String

        // Yapay Zeka İşlem Hattını Yönetme
        when (mode) {
            SpecialMode.COLOR_DETECTION -> {
                Log.i(TAG, "Özel Mod: Renk Tanıma Aktif Edildi.")
                aiAnalyzer.currentMode = SpecialMode.COLOR_DETECTION
                message = getString(R.string.msg_mode_color)
            }
            SpecialMode.CURRENCY_DETECTION -> {
                Log.i(TAG, "Özel Mod: Para Tanıma Aktif Edildi.")
                aiAnalyzer.currentMode = SpecialMode.CURRENCY_DETECTION
                message = getString(R.string.msg_mode_currency)
            }
            SpecialMode.OCR -> {
                // OCR sürekli mod olmasa da, tutarlılık için eklenmiştir.
                Log.i(TAG, "Özel Mod: OCR Hazırlanıyor.")
                aiAnalyzer.currentMode = SpecialMode.OCR
                message = getString(R.string.cd_ai_mode_ocr_start)
            }
            SpecialMode.NONE -> {
                Log.i(TAG, "Normal AI Göz Modu Aktif Edildi.")
                aiAnalyzer.currentMode = SpecialMode.NONE
                message = getString(R.string.ai_ready)
            }
        }

        speakStatus(message)
    }

    private fun getModeName(mode: SpecialMode): String {
        return when (mode) {
            SpecialMode.COLOR_DETECTION -> "Renk Tanıma"
            SpecialMode.CURRENCY_DETECTION -> "Para Birimi Tanıma"
            SpecialMode.OCR -> "Metin Okuma"
            SpecialMode.NONE -> "Normal AI Göz"
        }
    }

    // ---------------- SESLİ KOMUT İŞLEMLERİ ----------------

    private fun promptSpeechInput() {
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.cd_start_voice_command))
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (a: Exception) {
            speakStatus("Sesli komut servisi cihazınızda desteklenmiyor.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val fullCommand = results?.get(0)?.lowercase(Locale.getDefault()) ?: return

            when {
                // Kapatma komutu
                fullCommand.contains(getString(R.string.command_close_app_1)) ||
                        fullCommand.contains(getString(R.string.command_close_app_2)) -> {
                    speakAndFinish(R.string.app_closing_message)
                }
                // Tekrar oku komutu
                fullCommand.contains("tekrar") || fullCommand.contains("oku") -> {
                    speakStatus(lastSpokenText.ifBlank { getString(R.string.ai_ready) })
                }
                // OCR komutu
                fullCommand.contains("metin oku") || fullCommand.contains("ocr") -> {
                    performOcrScan()
                }
                // Özel mod komutları (Daha detaylı yapılması gerekir)
                fullCommand.contains("renk") -> {
                    startSpecialRecognitionMode(SpecialMode.COLOR_DETECTION)
                }
                fullCommand.contains("para") -> {
                    startSpecialRecognitionMode(SpecialMode.CURRENCY_DETECTION)
                }
                else -> {
                    speakStatus("Anlaşılmayan komut: $fullCommand")
                }
            }
        }
    }

    // ---------------- YAŞAM DÖNGÜSÜ VE TEMİZLİK ----------------

    private fun speakAndFinish(messageResId: Int) {
        val message = getString(messageResId)
        val params = Bundle()

        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "APP_CLOSING")
        } else {
            safeAppShutdown()
        }
    }

    private fun safeAppShutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        finishAffinity()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        cameraExecutor.shutdown() // Kamera iş parçacığını kapat
        super.onDestroy()
    }
}


// ---------------------------------------------------------------------------------
// 💡 VARSAYIMSAL AI ANALİZÖR SINIFI 💡
// Bu sınıf, kamera karesini alır ve belirlenen moda göre analiz yapar.
// Gerçek yapay zeka entegrasyonu (TensorFlow, ML Kit) burada gerçekleşir.

class AiAnalyzer : ImageAnalysis.Analyzer {

    var currentMode: AiEyeActivity.SpecialMode = AiEyeActivity.SpecialMode.NONE

    override fun analyze(image: ImageProxy) {
        // Görüntü işleme işlemi burada yapılır.

        when (currentMode) {
            AiEyeActivity.SpecialMode.COLOR_DETECTION -> {
                // Renk tanıma algoritması
                Log.d("AiAnalyzer", "Renk Analizi Yapılıyor...")
            }
            AiEyeActivity.SpecialMode.CURRENCY_DETECTION -> {
                // Para birimi tanıma algoritması
                Log.d("AiAnalyzer", "Para Birimi Analizi Yapılıyor...")
            }
            AiEyeActivity.SpecialMode.OCR -> {
                // Sürekli OCR Analizi yapılıyor
                Log.d("AiAnalyzer", "Sürekli OCR Analizi Yapılıyor...")
            }
            AiEyeActivity.SpecialMode.NONE -> {
                // Normal Nesne Tanıma/Çevre Analizi
                Log.d("AiAnalyzer", "Normal Çevre Analizi Yapılıyor...")
            }
        }

        image.close() // Analiz tamamlandıktan sonra kareyi kapat
    }
}