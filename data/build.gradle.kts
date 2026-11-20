plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
}

android {
    namespace = "com.cooper.homelab.ephyra.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    // The helpers its tests share with :app's: the fake Jellyfin server, the ciphers, the session store and the log that discards.
    testFixtures { enable = true }
    testOptions {
        // The session store tests need a real Context, which Robolectric supplies.
        unitTests.isIncludeAndroidResources = true
    }
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
    // The seam of the shared player: the request a player is handed and the events it sends back. Plain Kotlin, so the sitting
    // that runs between it and the server is tested on the JVM. api: the screens above this module name its types.
    api("com.cooper.homelab:player-api")
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.kit)
    implementation(libs.binge.companion.contracts)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.server)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.homelab.testing)
    testFixturesImplementation(libs.okhttp)
    testFixturesImplementation(libs.okhttp.mockwebserver)
    testFixturesImplementation(libs.binge.companion.kit)
    testFixturesImplementation(libs.homelab.appservices)
    testFixturesImplementation(libs.kotlinx.coroutines.android)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.datastore.preferences)
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // Robolectric fetches its android-all jars itself, outside Gradle's repositories, so point it at the same mirror as
    // settings.gradle.kts: Central answers bursts from shared runners with a 429.
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
    systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
}

// 74, measured: this module's own tests cover 74.96% of its lines. :app's tests exercise more of it through the screens and view
// models, and :app's floor is measured over both modules together, as it was when they were one. Raise it as the client gains
// tests of its own; never lower it.
kover {
    reports {
        verify {
            rule {
                minBound(74)
            }
        }
    }
}
