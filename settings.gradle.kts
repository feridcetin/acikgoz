pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven { url = uri("https://jitpack.io") }
        // ✨ KRİTİK EKLEME: Google'ın kendi Maven deposunu (WebRTC'yi kesinlikle içeren) manuel ekleyelim.
        // Bu, bazen standart google() çağrısının yetersiz kaldığı durumlarda çözüm olur.
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        // ✨ KRİTİK EKLEME: Chromium WebRTC deposu
        maven { url = uri("https://storage.googleapis.com/chromium-webrtc-archive/android") }

        // ✨ KRİTİK EKLEME: Stream'in kendi Maven deposu
        maven { url = uri("https://getstream.io/maven/releases") }
    }
}

rootProject.name = "acikgoz"
include(":app")
