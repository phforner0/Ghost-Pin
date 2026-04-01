plugins {
    id("com.android.application")
    kotlin("android")
    // kapt replaced by KSP — faster, required for Kotlin 2.x, no Alpha warnings.
    id("com.google.devtools.ksp")
    kotlin("plugin.compose")
    id("com.google.dagger.hilt.android")
}

import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

android {
    namespace = "com.ghostpin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ghostpin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2           // Sprint 4 bump
        versionName = "3.1.0"    // Sprint 4 — Profiles & Routes

        testInstrumentationRunner = "com.ghostpin.app.GhostPinTestRunner"

        // Room schema export directory — enables migration history tracking.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
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
            buildConfigField("boolean", "EXACT_ALARM_SETTINGS_ENABLED", "true")
            buildConfigField("boolean", "SCHEDULING_ENABLED", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "MOCK_PROVIDER_ENABLED", "false")
            buildConfigField("boolean", "SHIZUKU_ENABLED", "false")
            buildConfigField("boolean", "EXACT_ALARM_SETTINGS_ENABLED", "false")
            buildConfigField("boolean", "SCHEDULING_ENABLED", "false")
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

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("schemas")
        }
    }
}

dependencies {
    // Internal modules
    implementation(project(":core"))
    implementation(project(":engine"))

    // Hilt — using KSP
    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-android-compiler:2.55")

    // Room — Sprint 4: profile + route persistence
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")       // suspend + Flow extensions
    ksp("androidx.room:room-compiler:$roomVersion")             // KSP (not kapt)

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
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.2")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore — Sprint 5: onboarding persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.55")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.55")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val sanitizedPathForTests = (System.getenv("PATH") ?: "").replace("\"", "")

tasks.withType<Test>().configureEach {
    // Some Windows PATH entries are quoted (for example Tesseract), which breaks
    // the Gradle test worker command line and causes the JVM to parse `VS Code`
    // as a main class. Sanitize the environment and library path for test forks.
    environment("PATH", sanitizedPathForTests)
    systemProperty("java.library.path", sanitizedPathForTests)
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}
