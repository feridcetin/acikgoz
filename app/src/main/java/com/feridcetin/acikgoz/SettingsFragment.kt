// com.feridcetin.acikgoz/SettingsFragment.kt

package com.feridcetin.acikgoz

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Ayar XML dosyasını burada yüklüyoruz.
        // NOT: settings_preferences.xml dosyasını bir sonraki adımda oluşturacağız.
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
    }
}