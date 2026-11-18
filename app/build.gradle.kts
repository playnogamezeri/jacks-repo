plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.toonjack.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toonjack.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.2.4"
    }

    // CI supplies one long-lived keystore from GitHub Actions secrets.
    // Local builds may use the developer's normal Android debug keystore.
    signingConfigs {
        create("persistentDebug") {
            val ciKeystore = System.getenv("TOONJACK_KEYSTORE_PATH")
            storeFile = ciKeystore?.let { file(it) }
                ?: file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = System.getenv("TOONJACK_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("TOONJACK_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("TOONJACK_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("persistentDebug")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
