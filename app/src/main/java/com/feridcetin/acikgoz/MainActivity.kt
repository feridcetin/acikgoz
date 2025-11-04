package com.feridcetin.acikgoz

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Ana Ekran: Mod Seçim Yönlendiricisi
class MainActivity : AppCompatActivity() {

    private val SPEECH_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TalkBack Jest ve Sesli Komut Entegrasyonu
        setupButtons()
        startListeningForVoiceCommand()
    }

    private fun setupButtons() {
        // AI Göz Modu Düğmesi
        findViewById<Button>(R.id.btn_ai_eye).setOnClickListener {
            startActivity(Intent(this, AiEyeActivity::class.java))
        }

        // İnsan Gözü Modu Düğmesi
        findViewById<Button>(R.id.btn_human_eye).setOnClickListener {
            startActivity(Intent(this, HumanEyeActivity::class.java))
        }

        // Navigasyon Modu Düğmesi
        findViewById<Button>(R.id.btn_navigation).setOnClickListener {
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        // Ayarlar Düğmesi
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    // Basit bir Sesli Komut Dinleme Başlatma fonksiyonu
    private fun startListeningForVoiceCommand() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("tr").toString()) // Türkçe Komut Desteği
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
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val command = results?.get(0)?.lowercase(Locale("tr"))

            when {
                command?.contains("göz") == true || command?.contains("ai") == true -> {
                    startActivity(Intent(this, AiEyeActivity::class.java))
                }
                command?.contains("gönüllü") == true || command?.contains("insan") == true -> {
                    startActivity(Intent(this, HumanEyeActivity::class.java))
                }
                command?.contains("yönlendir") == true || command?.contains("navigasyon") == true -> {
                    startActivity(Intent(this, NavigationActivity::class.java))
                }
                // Uygulama sürekli komut dinleme döngüsüne alınır
                else -> startListeningForVoiceCommand()
            }
        }
    }
}