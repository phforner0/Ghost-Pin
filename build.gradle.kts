plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("android") version "2.1.10" apply false
    kotlin("kapt") version "2.1.10" apply false
    kotlin("plugin.compose") version "2.1.10" apply false
    id("com.android.application") version "8.8.2" apply false
    id("com.google.dagger.hilt.android") version "2.54" apply false
}

allprojects {
    group = "com.ghostpin"
    version = "3.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
    }
}
