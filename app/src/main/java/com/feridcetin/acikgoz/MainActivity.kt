package com.feridcetin.acikgoz

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {


    private lateinit var tts: TextToSpeech
    private val SPEECH_REQUEST_CODE = 100
    private var isPausedByApp = false // Uygulama içi geçişlerde dinlemeyi tekrar başlatmamak için

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        // 💡 YENİ EKLEME: TTS dinleyicisini ayarla
        setupTtsListener()

        setupButtons()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // TTS başarıyla başlatıldı, Türkçe dilini ayarla
            val result = tts.setLanguage(Locale("tr"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Türkçe dil paketi eksik veya desteklenmiyor.")
                // İsteğe bağlı: Kullanıcıyı bilgilendir
            } else {
                Log.i("TTS", "TTS motoru başarıyla başlatıldı.")
                // İsteğe bağlı: Uygulama hazır olduğunda başlangıç mesajı çalınabilir
            }
        } else {
            Log.e("TTS", "TTS Başlatma başarısız oldu. Hata kodu: $status")
        }
    }

    // 💡 İyileştirme: Activity ekrana her geldiğinde (veya geri dönüldüğünde) dinlemeyi başlat
    override fun onResume() {
        super.onResume()
        // Başka bir Activity'den geri dönüldüyse veya uygulama yeni açılıyorsa dinlemeyi başlat
        if (!isPausedByApp) {
            startListeningForVoiceCommand()
        }
        isPausedByApp = false // Bir sonraki onPause için sıfırla
    }

    override fun onPause() {
        super.onPause()
        // Dinleme Intent'i kapatılamaz, ancak uygulamanın arka plana gittiğini işaretleriz.
    }

    private fun setupButtons() {
        // ... (Buton click listener'larınız aynı kalır)

        findViewById<Button>(R.id.btn_ai_eye).setOnClickListener {
            isPausedByApp = true // Uygulama içi geçişlerde dinlemeyi tekrar başlatma
            startActivity(Intent(this, AiEyeActivity::class.java))
        }

        findViewById<Button>(R.id.btn_human_eye).setOnClickListener {
            isPausedByApp = true
            startActivity(Intent(this, HumanEyeActivity::class.java))
        }

        findViewById<Button>(R.id.btn_navigation).setOnClickListener {
            isPausedByApp = true
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            isPausedByApp = true
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    // 🎤 Türkçe Sesli Komut Dinleme
    private fun startListeningForVoiceCommand() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // 💡 Düzeltme: Türkçe dil kodunu "tr-TR" olarak açıkça belirtiyoruz.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")

            // Kullanıcının bulunduğu bölgeyi (Türkiye) zorla (güvenilir ses tanıma için)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("tr", "TR"))

            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.say_a_command))
        }
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Ses tanıma desteklenmiyor.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {

            if (resultCode == RESULT_OK) {
                // Komut başarıyla tanındı
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val command = results?.get(0)?.lowercase(Locale("tr"))

                when {
                    command?.contains("göz") == true
                            || command?.contains("ai") == true
                            || command?.contains("ai modu") == true
                            || command?.contains("yapay zeka göz")== true   -> {
                        isPausedByApp = true
                        startActivity(Intent(this, AiEyeActivity::class.java))
                    }
                    command?.contains("gönüllü") == true || command?.contains("insan") == true -> {
                        isPausedByApp = true
                        startActivity(Intent(this, HumanEyeActivity::class.java))
                    }
                    command?.contains("yönlendir") == true
                            || command?.contains("yol tarifi") == true
                            || command?.contains("navigasyon") == true -> {
                        isPausedByApp = true
                        startActivity(Intent(this, NavigationActivity::class.java))
                    }
                    // 💡 YENİ EKLENEN KAPATMA KOMUTU
                    command?.contains(getString(R.string.command_close_app_1)) == true ||
                            command?.contains(getString(R.string.command_close_app_2)) == true -> {
                        speakAndFinish(R.string.app_closing_message)
                    }

                    else -> {
                        // Geçersiz komut, kullanıcıyı bilgilendir
                        Toast.makeText(this, "Komut anlaşılamadı. Lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
                        // Tekrar dinlemeye başlaması için onResume'u bekleriz.
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                // Kullanıcı geri tuşuna bastı veya zaman aşımı oldu (Döngüyü durdurmak için bu önemli!)
                Toast.makeText(this, "Sesli komut iptal edildi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

   /* private fun speakAndFinish(messageResId: Int) {
        val message = getString(messageResId)

        // 1. Sesi çal
        if (::tts.isInitialized) {
            // QUEUE_FLUSH ile önceki konuşmaları kes
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // 2. Mesajın çalınması için yeterli bir süre bekle ve ardından uygulamayı kapat
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 3. 🚨 ÖNEMLİ EKLEME: TTS motorunu durdur ve serbest bırak
            if (::tts.isInitialized) {
                tts.stop() // Konuşmayı durdur
                tts.shutdown() // Kaynakları serbest bırak
            }

            finishAffinity() // Tüm aktiviteleri kapatarak uygulamayı tamamen sonlandır
        }, 1500) // 1.5 saniye bekle (mesajın uzunluğuna göre ayarlanabilir)
    }
    */

    private fun speakAndFinish(messageResId: Int) {
        val message = getString(messageResId)

        // TTS dinleyicisini kullanmak için Bundle oluştur
        val params = Bundle()

        // TTS'i oynatırken kimlik (Utterance ID) ata
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "APP_CLOSING") // 💡 ID Eklendi
        } else {
            // TTS henüz hazır değilse, hemen kapat
            safeAppShutdown()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // TTS'i sadece henüz kapatılmamışsa kapat
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        // ... (diğer kaynakları serbest bırakma)
    }

    // Yeni metot: TTS olaylarını dinlemek için
    private fun setupTtsListener() {
        val listener = object : UtteranceProgressListener() {
            // Konuşma başarılı bir şekilde bittiğinde çağrılır
            override fun onDone(utteranceId: String?) {
                // Sadece bizim belirlediğimiz ID ile gelen konuşma biterse kapat
                if (utteranceId == "APP_CLOSING") {
                    // Konuşma bitti, şimdi ana iş parçacığında uygulamayı kapat
                    runOnUiThread {
                        safeAppShutdown()
                    }
                }
            }

            // Konuşma sırasında hata oluşursa çağrılır
            override fun onError(utteranceId: String?) {
                // Hata olsa bile uygulamayı kapat
                if (utteranceId == "APP_CLOSING") {
                    runOnUiThread {
                        safeAppShutdown()
                    }
                }
            }

            // Konuşma başladığında çağrılır (gereksiz, override etme zorunluluğu nedeniyle var)
            override fun onStart(utteranceId: String?) {
                Log.i("TTS_Listener", "Kapanış mesajı çalmaya başladı.")
            }
        }

        // Dinleyiciyi TTS motoruna kaydet
        tts.setOnUtteranceProgressListener(listener)
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
}