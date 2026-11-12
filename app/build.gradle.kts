plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yotakon.pes5editor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yotakon.pes5editor"
        minSdk = 21
        targetSdk = 34
        // En GitHub Actions cada compilación sube el número, así el APK nuevo se instala encima del anterior
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "0.2.$run"
    }

    // Misma firma en todas las compilaciones (si la firma cambia, Android no deja actualizar)
    signingConfigs {
        create("app") {
            storeFile = rootProject.file("firma/pes5editor.jks")
            storePassword = "pes5editor"
            keyAlias = "pes5editor"
            keyPassword = "pes5editor"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
}
