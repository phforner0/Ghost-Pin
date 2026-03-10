plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.compose")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.ghostpin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ghostpin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    flavorDimensions += "distribution"
    productFlavors {
        create("nonplay") {
            dimension = "distribution"
            applicationIdSuffix = ".nonplay"
            buildConfigField("boolean", "MOCK_PROVIDER_ENABLED", "true")
            buildConfigField("boolean", "SHIZUKU_ENABLED", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "MOCK_PROVIDER_ENABLED", "false")
            buildConfigField("boolean", "SHIZUKU_ENABLED", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    // Internal modules
    implementation(project(":core"))
    implementation(project(":engine"))

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-android-compiler:2.54")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

kapt {
    correctErrorTypes = true
}
