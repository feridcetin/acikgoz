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
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.preference.PreferenceManager // Gerektiğinde varsayılan tercihleri okumak için
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var tvHeader: TextView
    private lateinit var btnAiEye: ImageButton
    private lateinit var btnNavigation: ImageButton
    private lateinit var btnVoiceCommand: ImageButton
    private lateinit var btnSettings: ImageButton // Ayarlar butonu eklendi

    private val REQUEST_CODE_SPEECH_INPUT = 100
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Elemanlarını Bağlama
        tvHeader = findViewById(R.id.tv_main_header)
        btnAiEye = findViewById(R.id.btn_ai_eye)
        btnNavigation = findViewById(R.id.btn_navigation)
        btnVoiceCommand = findViewById(R.id.btn_voice_command)
        btnSettings = findViewById(R.id.btn_settings) // Ayarlar butonu bağlandı

        // TTS Başlatma ve Dinleyici Kurulumu
        tts = TextToSpeech(this, this)
        setupTtsListener()

        // Menü Butonlarını Ayarlama
        setupMenuButtons()

        // Android Geri Tuşu İşleyicisini Ayarlama
        setupOnBackPressedListener()

        // Uygulama ayarlarını yükle (Örneğin TTS Hızı)
        loadAppSettings()
    }

    // ---------------- AYARLARI YÜKLEME ----------------

    private fun loadAppSettings() {
        // SettingsFragment'ta belirtilen özel shared preferences dosyasını kullanıyoruz
        val prefs = getSharedPreferences("my_custom_settings", MODE_PRIVATE)

        // Konuşma Hızını Yükle (Varsayılan 100/1.0f)
        val ttsSpeedInt = prefs.getInt("tts_speed", 100)
        val ttsSpeedFactor = ttsSpeedInt / 100.0f

        // Ses Tonunu Yükle (Varsayılan 100/1.0f)
        val ttsPitchInt = prefs.getInt("tts_pitch", 100)
        val ttsPitchFactor = ttsPitchInt / 100.0f

        if (::tts.isInitialized) {
            // TTS başlatıldıysa ayarları uygula
            tts.setSpeechRate(ttsSpeedFactor)
            tts.setPitch(ttsPitchFactor)
        }
    }


    // ---------------- TTS BAŞLATMA VE DİNLEYİCİ ----------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("tr"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Türkçe dil paketi eksik veya desteklenmiyor.")
            } else {
                // TTS hazır olduğunda ayarları uygula ve karşılama mesajını oku
                loadAppSettings()
                speakStatus(getString(R.string.main_header_text))
            }
        } else {
            Log.e(TAG, "TTS Başlatma başarısız oldu. Hata kodu: $status")
        }
    }

    private fun setupTtsListener() {
        val listener = object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                // Kapanış komutu bittiğinde uygulamayı güvenle kapat
                if (utteranceId == "APP_CLOSING") {
                    runOnUiThread {
                        safeAppShutdown()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                // Hata durumunda bile kapatma denemesi
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
        runOnUiThread {
            tvHeader.text = message
        }
        if (::tts.isInitialized) {
            // Konuşmadan önce, değişmiş olabilecek TTS ayarlarını tekrar yükle
            loadAppSettings()
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // ---------------- BUTON VE SESLİ KOMUT İŞLEMLERİ ----------------

    private fun setupMenuButtons() {

        // 1. YAPAY ZEKA GÖZÜ BUTONU (Dokunmatik)
        btnAiEye.setOnClickListener {
            speakStatus(getString(R.string.cd_ai_mode_eye))
            startActivity(Intent(this, AiEyeActivity::class.java))
        }

        // 2. NAVİGASYON BUTONU (Dokunmatik)
        btnNavigation.setOnClickListener {
            speakStatus(getString(R.string.cd_navigation_mode))
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        // 3. MİKROFON BUTONU (Dokunmatik -> Sesli Komut)
        btnVoiceCommand.setOnClickListener {
            // Kısa basma: Sesli komut dinlemeyi başlat
            promptSpeechInput()
        }

        // 💡 4. AYARLAR BUTONU (Dokunmatik)
        btnSettings.setOnClickListener {
            speakStatus(getString(R.string.cd_settings))
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun promptSpeechInput() {
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop() // TTS konuşuyorsa kes
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
            val command = results?.get(0)?.lowercase(Locale.getDefault())

            when {
                // Yapay Zeka Göz Modu Komutu
                command?.contains(getString(R.string.command_eye)) == true || command?.contains(getString(R.string.command_ai)) == true -> {
                    speakStatus(getString(R.string.cd_ai_mode_eye))
                    startActivity(Intent(this, AiEyeActivity::class.java))
                }
                // Navigasyon Modu Komutu
                command?.contains(getString(R.string.command_nav)) == true
                        || command?.contains(getString(R.string.command_yonlendir)) == true
                        || command?.contains(getString(R.string.command_yoltarifi)) == true -> {
                    speakStatus(getString(R.string.cd_navigation_mode))
                    startActivity(Intent(this, NavigationActivity::class.java))
                }
                // Ayarlar Komutu
                command?.contains("ayarlar") == true || command?.contains("settings") == true -> {
                    speakStatus(getString(R.string.cd_settings))
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                // Uygulamayı Kapatma Komutu
                command?.contains(getString(R.string.command_close_app_1)) == true || command?.contains(getString(R.string.command_close_app_2)) == true -> {
                    speakAndFinish(R.string.app_closing_message)
                }
                else -> {
                    speakStatus("Anlaşılmayan komut: $command")
                }
            }
        }
    }

    // ---------------- UYGULAMA KAPATMA MANTIĞI ----------------

    private fun speakAndFinish(messageResId: Int) {
        val message = getString(messageResId)
        val params = Bundle()

        // TTS konuşmayı bitirdikten sonra UtteranceProgressListener ile kapatmayı tetikle
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "APP_CLOSING")
        } else {
            safeAppShutdown() // TTS hazır değilse hemen kapat
        }
    }

    private fun safeAppShutdown() {
        // 1. TTS kaynaklarını serbest bırak
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        // 2. Uygulamayı tamamen kapat
        finishAffinity()
    }

    private fun setupOnBackPressedListener() {
        // AndroidX OnBackPressedDispatcher ile geri tuşunu yakalama
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Geri tuşuna basıldığında kapatma mesajını çal ve uygulamayı kapat
                speakAndFinish(R.string.app_closing_message)
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    // ---------------- YAŞAM DÖNGÜSÜ ----------------

    override fun onResume() {
        super.onResume()
        // Ayarlar değişmiş olabileceği için geri dönüldüğünde ayarları yeniden yükle
        loadAppSettings()
    }

    override fun onDestroy() {
        // Genel temizlik
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}