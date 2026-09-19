plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "net.orionx.messenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.orionx.messenger"
        minSdk = 26
        targetSdk = 36
        versionCode = 6083111
        versionName = "6.9.31"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        // SHA-256 of the admin release-panel password. Set ORIONX_ADMIN_PASSWORD_SHA256
        // in gradle.properties (never in git). Empty by default = panel locked.
        buildConfigField(
            "String",
            "ORIONX_ADMIN_PASSWORD_SHA256",
            "\"${(project.findProperty("ORIONX_ADMIN_PASSWORD_SHA256") as String? ?: "").trim()}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // The unit-test JVM has to fit alongside Gradle on an 8 GB machine.
        unitTests.all { it.maxHeapSize = "512m" }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Consistent signing so a new APK always installs OVER the previous one.
    // Android refuses to update an app if the new APK's signature differs from the
    // installed one ("App not installed" / conflict). Configure a real release keystore
    // in gradle.properties (ORIONX_STORE_FILE / ORIONX_STORE_PASSWORD / ORIONX_KEY_ALIAS /
    // ORIONX_KEY_PASSWORD) to sign every distributed APK identically. If it isn't set,
    // release falls back to the debug key so the build never breaks and stays consistent.
    signingConfigs {
        create("release") {
            val storePath = project.findProperty("ORIONX_STORE_FILE") as String?
            if (storePath != null && rootProject.file(storePath).exists()) {
                storeFile = rootProject.file(storePath)
                storePassword = project.findProperty("ORIONX_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("ORIONX_KEY_ALIAS") as String?
                keyPassword = project.findProperty("ORIONX_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Real 1-to-1 audio/video calls (WebRTC M144).
    implementation("io.github.webrtc-sdk:android:144.7559.09")

    // In-app video-message recording (CameraX). Keep every CameraX artifact on one version.
    val cameraXVersion = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-video:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")


    // OrionX media player and real pre-send video trimming/cropping.
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")

    // QR: decoding only. The scanner screen itself is our own Compose UI from the redesign,
    // so the bundled ML Kit model is used through CameraX ImageAnalysis rather than the
    // ready-made Google scanner activity.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // The «файлы» mini-app browses a folder the user picks through the system picker.
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests for the video-note state machine.
    testImplementation("junit:junit:4.13.2")
}
