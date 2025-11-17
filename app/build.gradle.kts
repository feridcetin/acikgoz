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
        viewBinding = true
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

// 🎯 ÇÖZÜM ADIMI 1: Uzlaşmaya Gitme ve Zorla Sürüm Kullanma
// Tüm yapılandırmalarda TFLite sürümünü, Tesseract'ın talep ettiği sürüme (2.10.0/0.4.3) zorluyoruz.
configurations.all {
    resolutionStrategy {
        // Tesseract'ın getirdiği sürümle (2.10.0) çakışmayı önlemek için uzlaşma.
        force("org.tensorflow:tensorflow-lite:2.10.0")
        force("org.tensorflow:tensorflow-lite-support:0.4.3")
    }
}

dependencies {
    // Kotlin Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation(libs.androidx.activity)

    // --- Çevrimdışı AI (TFLite) Bileşenleri (Uzlaşılmış Sürümler) ---
    // Kendi TFLite sürümlerimizi Tesseract'ın talep ettiği sürüme düşürdük (2.10.0).
    implementation("org.tensorflow:tensorflow-lite:2.10.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.3") // Support sürümüyle uyumlu

    // --- OCR Bileşenleri (Tesseract) ---
    val tesseract4AndroidVersion = "4.0.0"
    implementation("com.github.adaptech-cz:Tesseract4Android:$tesseract4AndroidVersion") {
        // ÇÖZÜM ADIMI 2: isTransitive = false kuralını kaldırdık ve litert-api'yi tekrar hariç tuttuk.
        // resolutionStrategy, org.tensorflow:tensorflow-lite-api'yi 2.10.0'a zorlayarak litert-api'nin
        // kendi TFLite sınıflarını yüklemesini engellemeyi hedefliyor.
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }

    // --- Çevrimdışı Navigasyon (OSMDroid) Bileşenleri ---
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // --- Gönüllü Bağlantısı (WebRTC) Bileşenleri ---
    implementation("io.getstream:stream-webrtc-android-ui:1.3.10")
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // --- CameraX Kütüphaneleri ---
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")

    // Preference Fragment için gerekli
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}