plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version and update URL come from the CI build (gradle -PversionCode=... -PupdateUrl=...)
val vCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val vName = (findProperty("versionName") as String?) ?: "0.1.0"
val updateUrl = (findProperty("updateUrl") as String?) ?: ""
val configUrl = (findProperty("configUrl") as String?) ?: ""
val keystorePath = System.getenv("ATR_KEYSTORE") ?: "atr-release.jks"

android {
    namespace = "com.atr.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atr.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        buildConfigField("String", "CONFIG_URL", "\"$configUrl\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("ATR_KEYSTORE_PASS")
            keyAlias = "atr"
            keyPassword = System.getenv("ATR_KEY_PASS")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Same key every build, so the TV accepts updates over the top
            signingConfig = if (file(keystorePath).exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
