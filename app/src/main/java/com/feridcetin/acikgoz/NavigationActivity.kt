package com.feridcetin.acikgoz

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class NavigationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var tvStatus: TextView
    private lateinit var btnVoiceCommand: ImageButton

    private val REQUEST_CODE_SPEECH_INPUT = 100
    private val TAG = "NavigationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation) // activity_navigation.xml'in var olduğu varsayılır

        // UI Elemanları
        tvStatus = findViewById(R.id.tv_nav_status)
        btnVoiceCommand = findViewById(R.id.btn_voice_nav_command)

        // TTS ve Listener başlatma
        tts = TextToSpeech(this, this)
        setupTtsListener()

        // Buton Dinleyicisi
        btnVoiceCommand.setOnClickListener {
            promptSpeechInput()
        }

        // Geri Butonunu Ayarlama (Eğer XML'de varsa)
        findViewById<ImageButton>(R.id.btn_nav_back)?.setOnClickListener {
            finish()
        }
    }

    // ---------------- TTS BAŞLATMA VE DİNLEYİCİ ----------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("tr"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Türkçe dil paketi eksik veya desteklenmiyor.")
            } else {
                speakStatus(getString(R.string.nav_ready))
            }
        } else {
            Log.e(TAG, "TTS Başlatma başarısız oldu.")
        }
    }

    private fun setupTtsListener() {
        // Konuşma bittiğinde harici aksiyonları tetiklemek için dinleyici
        val listener = object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                // Burada konuşma bittikten sonra sesli komutu tekrar başlatma gibi aksiyonlar eklenebilir.
                Log.i(TAG, "TTS Sona Erdi: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS Hata: $utteranceId")
            }

            override fun onStart(utteranceId: String?) {
                // Konuşma başladığında yapılacaklar
            }
        }
        tts.setOnUtteranceProgressListener(listener)
    }

    fun speakStatus(message: String) {
        runOnUiThread {
            tvStatus.text = message
        }
        if (::tts.isInitialized) {
            // Konuşma kimliği olmadan konuş
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // ---------------- SESLİ KOMUT İŞLEMLERİ ----------------

    private fun promptSpeechInput() {
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop() // TTS konuşuyorsa kes
        }

        // Standart Android konuşma tanıma intenti
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR") // Türkçe dilini zorla
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.say_your_destination))
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

            // 💡 DİNAMİK HEDEF AYRIŞTIRMA MANTIĞI
            when {
                fullCommand.contains("yol tarifi")
                        || fullCommand.contains("git")
                        || fullCommand.contains("navigasyon")
                        || fullCommand.contains("başlat")
                        || fullCommand.contains("götür")
                        || fullCommand.contains("yolu")
                        || fullCommand.contains("rota") -> {

                    val keywords = listOf("yol tarifi", "git", "navigasyon", "başlat", "götür", "yolu","rota")
                    var destination = fullCommand

                    // Anahtar kelimeleri komutun başından/sonundan çıkar
                    for (keyword in keywords) {
                        if (destination.contains(keyword)) {
                            // Anahtar kelimeyi ve etrafındaki boşlukları temizle
                            destination = destination.replace(keyword, "").trim()
                        }
                    }

                    // Ek temizlik (Örn: "lütfen", "hemen")
                    destination = destination.trimStart(*charArrayOf('l', 'ü', 't', 'f', 'e', 'n', ' ')).trim()

                    if (destination.isNotBlank() && destination.length > 3) {
                        speakStatus("$destination hedefine rota oluşturuluyor.")
                        findDirections(destination) // Dinamik hedefi harita uygulamasına gönder
                    } else {
                        speakStatus("Hedefi net olarak belirtmediniz. Lütfen tekrar deneyin.")
                    }
                }

                // Ek Komutlar (Eğer varsa)
                fullCommand.contains("iptal") -> {
                    speakStatus("İşlem iptal edildi.")
                }

                else -> {
                    speakStatus("Anlaşılmayan komut: $fullCommand")
                }
            }
        }
    }

    // ---------------- HARİTA VE NAVİGASYON ----------------

    /**
     * Belirtilen hedefe harita uygulaması üzerinden navigasyon başlatır.
     * @param destination Dinamik olarak ayrıştırılmış hedef adresi.
     */
    private fun findDirections(destination: String) {
        try {
            // Google Haritalar'ı açmak için URI kullan
            val gmmIntentUri = Uri.parse("google.navigation:q=$destination")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps") // Sadece Google Haritalar'ı hedefle

            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                // Google Haritalar yüklü değilse, basit bir tarayıcı tabanlı arama yap
                val webIntentUri = Uri.parse("geo:0,0?q=$destination")
                val webMapIntent = Intent(Intent.ACTION_VIEW, webIntentUri)
                startActivity(webMapIntent)
                //speakStatus("Google Haritalar uygulaması bulunamadı. Harita web üzerinde açıldı.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Navigasyon hatası: ${e.message}")
            speakStatus("Navigasyon başlatılamadı.")
        }
    }

    // ---------------- YAŞAM DÖNGÜSÜ YÖNETİMİ ----------------

    override fun onDestroy() {
        // TTS kaynaklarını serbest bırak
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}