plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Jeder Build auf GitHub zählt die Versionsnummer hoch (lokal: 1).
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Wird von der GitHub-Action gesetzt, wenn ein Signaturschlüssel hinterlegt ist.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "app.kalenderwidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.kalenderwidgets"
        minSdk = 31
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS").takeUnless { it.isNullOrBlank() } ?: "kalenderwidgets"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                // Fallback ohne hinterlegten Schlüssel: Zufallsschlüssel pro Build
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
