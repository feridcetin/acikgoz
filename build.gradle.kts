// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Android Uygulama Eklentisi (uygulama modülü için)
    alias(libs.plugins.android.application) apply false

    // Kotlin Android Eklentisi (tüm Kotlin modülleri için)
    alias(libs.plugins.kotlin.android) apply false

    // Kotlin Kapt Eklentisi (Annotation Processing - Room/Dagger/Hilt gibi kütüphaneler için gerekebilir)
    // alias(libs.plugins.kotlin.kapt) apply false
}

// ----------------------------------------------------
// NOTE: Modern Gradle projelerinde (Kotlin DSL ve settings.gradle.kts varken),
// genel depo (repositories) ayarları genellikle settings.gradle.kts dosyasında yapılır.
// Ancak bazı eski veya özel yapılandırmalar için bu blok gerekebilir:
// ----------------------------------------------------

/*
allprojects {
    repositories {
        google()
        mavenCentral()

        // Eğer özel bir kütüphane (örneğin eski bir WebRTC sürümü) farklı bir depodan geliyorsa
        // maven { url = uri("https://jitpack.io") }
    }
}
*/

// Görev ve bağımlılık temizleme gibi proje çapında ayarlar buraya eklenebilir.