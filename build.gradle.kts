plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("android") version "2.1.10" apply false
    // Fix: kapt is deprecated in Kotlin 2.x — migrated to KSP (Kotlin Symbol Processing).
    // KSP is faster (incremental), officially maintained, and required for Kotlin 2.x going forward.
    id("com.google.devtools.ksp") version "2.1.10-1.0.31" apply false
    kotlin("plugin.compose") version "2.1.10" apply false
    id("com.android.application") version "8.8.2" apply false
    id("com.google.dagger.hilt.android") version "2.55" apply false
}

allprojects {
    group = "com.ghostpin"
    version = "3.1.0"

    repositories {
        google()
        mavenCentral()
    }
}
