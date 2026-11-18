plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
}

android {
    namespace = "com.cooper.homelab.ephyra.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.kit)
    implementation(libs.binge.companion.contracts)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.server)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)
}
