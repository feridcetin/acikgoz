plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.feridcetin.acikgoz"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.feridcetin.acikgoz"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true // İsteğe bağlı
        // android.enableAapt2 = true (Eski sürümlerde gerekliyken artık varsayılan)
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}

dependencies {
    // Kotlin Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // *** Çevrimdışı AI (TFLite) Bileşenleri ***
    // Not: Kotlin DSL'de, tırnak işaretlerini (") parantezlerle () çevrelemelisiniz.
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // *** Çevrimdışı Navigasyon (OSMDroid) Bileşenleri ***
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // GraphHopper (rota hesaplama) genellikle harici JAR/AAR olarak veya JitPack ile eklenir
    // implementation("com.github.graphhopper:graphhopper:6.0")

    // *** OCR Bileşenleri (Tesseract) ***
    ///implementation("com.rmtheis:tess-two:9.1.0")
    ///implementation("com.github.rmtheis:tess-two:9.1.0")
    val tesseract4AndroidVersion = "4.0.0" // Güncel stabil versiyonu kullanın
    implementation("com.github.adaptech-cz:Tesseract4Android:$tesseract4AndroidVersion")


    // *** Gönüllü Bağlantısı (WebRTC) Bileşenleri ***

    ///implementation("io.github.webrtc-sdk:android:125.6422.06.1")
    implementation("io.getstream:stream-webrtc-android-ui:1.3.10")
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    implementation(libs.androidx.activity)

    // Test dependencies (Genellikle otomatik eklenir)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ✨ CameraX Kütüphaneleri (ProcessCameraProvider için KRİTİK)
    val cameraXVersion = "1.3.1" // Veya kullandığınız güncel stabil versiyon

    // ProcessCameraProvider burada bulunur
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")

    // Temel CameraX işlevleri
    implementation("androidx.camera:camera-core:$cameraXVersion")

    // Önizleme ve Görünüm yönetimi (PreviewView için gereklidir)
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // camera2 API'si üzerinden donanıma erişim
    implementation("androidx.camera:camera-camera2:$cameraXVersion")

// 💡 EKSİK OLAN KÜTÜPHANE: Preference Fragment ve ilgili sınıfları kullanmak için gereklidir
    implementation("androidx.preference:preference-ktx:1.2.1") // En son stabil sürümü kullanın
}