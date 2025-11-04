package com.feridcetin.acikgoz

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // XML layout'u yerine, PreferenceFragment'ı yerleştiriyoruz.
        setContentView(R.layout.activity_settings)

        // Geri düğmesini göstermek için bir ActionBar ayarlıyoruz
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Fragment'ı Activity'ye ekle
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    // Geri düğmesine basıldığında aktiviteyi sonlandır
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}