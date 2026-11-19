plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
}

// The client's screens (epic #759): Home first, then Libraries, Search and the title pages, one sub-package each. This module
// depends on :data and the design system and never on :app; :app depends on it and its shell hosts the entries. Models, view
// models and UI state here use no Android types, so they can move to common code.
android {
    namespace = "com.cooper.homelab.ephyra.client"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    lint {
        // As :app's: a locale is complete or it does not exist, and an unused string is an error.
        error +=
            setOf(
                "MissingTranslation",
                "ExtraTranslation",
                "MissingQuantity",
                "UnusedQuantity",
                "StringFormatMatches",
                "StringFormatCount",
                "UnusedResources",
            )
    }
    testOptions {
        // The screens' tests need real resources, which Robolectric supplies.
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
    implementation(project(":data"))
    implementation(libs.binge.designsystem)
    implementation(libs.homelab.appservices)
    implementation(libs.homelab.uiandroid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.homelab.testing)
    testImplementation(testFixtures(project(":data")))
    testImplementation(testFixtures(libs.binge.designsystem))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
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

// No floor yet: the module has no code to measure. The first screen's PR sets one from its measured coverage, and it is never
// lowered. :app's floor is measured over :app, :data and this module together.
