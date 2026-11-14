plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Ключи Telegram и подпись приходят из переменных окружения (GitHub Secrets).
// Без них сборка проходит, но приложение покажет экран «ключи не заданы».
val tgApiId = System.getenv("TG_API_ID")?.trim()?.toIntOrNull() ?: 0
val tgApiHash = System.getenv("TG_API_HASH")?.trim().orEmpty()
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val keystore = rootProject.file("release.jks")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()

android {
    namespace = "app.tgmus"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tgmus"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.1.$runNumber"

        buildConfigField("int", "TG_API_ID", tgApiId.toString())
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHash\"")

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keystore.exists() && keystorePassword.isNotEmpty()) {
            create("release") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "tgmus"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.github.tdlibx:td:1.8.56")
}
