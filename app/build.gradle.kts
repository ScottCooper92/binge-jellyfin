import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    alias(libs.plugins.kover)
    alias(libs.plugins.screenshot)
    id("binge.gates.tv-material-separation")
    id("binge.gates.translation-staleness")
}

android {
    namespace = "com.cooper.binge.companion.jellyfin"
    compileSdk = 37

    // What makes AGP create the screenshotTest source set. The matching flag is in gradle.properties;
    // both have to be present or the frames compile nowhere and the task does not exist.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.cooper.binge.companion.jellyfin"
        minSdk = 26
        targetSdk = 36
        // A release workflow overrides both from the tag it builds; a local build keeps the placeholders.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    buildFeatures {
        // BuildConfig.VERSION_NAME goes in the Authorization header Jellyfin reads the client from.
        buildConfig = true
        compose = true
    }

    // Generates the locale config from the values-<lang> directories, so Android 13+ offers this app
    // in the per-app language picker.
    androidResources { generateLocaleConfig = true }

    // A locale is complete or it does not exist: the alternative is Android's per-string fallback,
    // half a screen in each language. Adding an English string means adding its Spanish in the same
    // change. UnusedResources is pinned too: a removed call site can otherwise leave its string behind.
    lint {
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

    // The release keystore is never in the repository: the values come from keystore.properties at
    // the root (gitignored) or from the environment. Without them the release build has no signing
    // config, so it still builds everywhere, as an unsigned APK.
    val keystoreProperties =
        Properties().apply {
            val file = rootDir.resolve("keystore.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }

    fun signingValue(key: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(key)
    val releaseStoreFile = signingValue("RELEASE_STORE_FILE")

    signingConfigs {
        // Committed rather than left to AGP's implicit debug key, which each machine generates for
        // itself: a debug build from one machine would then not install over one from another.
        // Standard AOSP debug credentials; there is nothing secret in a debug key.
        getByName("debug") {
            storeFile = rootDir.resolve("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootDir.resolve(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A package of its own, so a debug build installs beside a release one instead of over it.
            applicationIdSuffix = ".debug"
            // Opt-in (`-PminifyDebug`) minified debug build: the one the device lane runs, so a keep
            // rule that goes stale fails there rather than on a user. BuildConfig.DEBUG stays true, which
            // is what lets the smoke test bind as a caller. Off by default: it would slow the dev loop.
            if (project.hasProperty("minifyDebug")) {
                optimization { enable = true }
            }
        }
        release {
            // One switch for code shrinking, resource shrinking and the default Android keep rules;
            // this app's own rules are the `keepRules` source set. Must stay identical to the
            // minifyDebug block above, or the lane stops proving what release ships.
            optimization { enable = true }
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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

    testOptions {
        // Robolectric hosts Compose on the JVM, against screens built from stringResource, so the
        // merged resources have to be on the unit-test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

// A reworded English string is the one translation drift lint cannot see, so the hash of every
// translated source string is committed at the root and `check` fails when one moves. Re-read the
// translation it names, then re-stamp with `updateTranslationHashes`. The gate is binge-ci's.
translationStaleness {
    includes.set(listOf("app/src/*/res/values/strings.xml", "app/src/*/res/values-*/strings.xml"))
}

// Material 3 and tv-material each ship a MaterialTheme under the same simple name, so the wrong
// import compiles and renders subtly wrong. binge-ci's gate keeps `ui/tv` on tv-material and
// everything else off it. JellyfinTheme.kt is the one crossing.
tvMaterialSeparation {
    sources.from(fileTree("src") { include("**/*.kt") })
    allowlist.add("app/src/main/kotlin/com/cooper/binge/companion/jellyfin/theme/JellyfinTheme.kt")
    themeTypeNames.addAll("JellyfinDarkColorScheme", "JellyfinLightColorScheme")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.contracts)
    implementation(libs.binge.designsystem)
    // The television surface in ui/tv. tv-material and the phone's Material 3 are never mixed in one file.
    implementation(libs.binge.designsystem.tv)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // The Service is driven over an in-process channel: the whole contract, no device.
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // The frames render against the same Compose the app ships, and the validation API is what
    // @PreviewTest resolves to.
    screenshotTestImplementation(platform(libs.compose.bom))
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}

// detekt is binge-ci's gate: production `src/main` only, with type resolution against the debug
// compilation, against the root detekt.yml. There is no baseline: the gate is zero findings.

// The exception on a failed test, into the console and so into CI's own log. Gradle's default
// prints the exception's class and not its message.
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // The screenshot render and validate tasks are Test tasks too, and they discover no JUnit tests.
    if (name.contains("ScreenshotTest")) {
        failOnNoDiscoveredTests = false
        maxParallelForks = 1
    } else {
        // Robolectric fetches its android-all jars itself, outside Gradle's repositories, so point it at
        // the same mirror as settings.gradle.kts: Central answers bursts from shared runners with a 429.
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
        systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
    }
}

// The screenshot plugin does not wire itself into `check`, so without this `./gradlew build` would
// carry the frames and never compare them.
tasks.named("check") {
    dependsOn("validateDebugScreenshotTest")
}

// The gate measures logic. What is excluded is an Android entry point with nothing a JVM test can
// reach, or a composable: how a screen looks is the screenshot gate's question, not line coverage's.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*ComposableSingletons*",
                    "*PreviewData*",
                    "com.cooper.binge.companion.jellyfin.MainActivity*",
                    // The exported Service: services() and hostPolicy() hand the SDK objects from the graph.
                    // What they choose is tested through JellyfinLibraryService and selectHostPolicy.
                    "com.cooper.binge.companion.jellyfin.service.JellyfinCompanionService",
                    // The Application and the hand-built graph: wiring, every member a constructed object.
                    "com.cooper.binge.companion.jellyfin.JellyfinApp",
                    "com.cooper.binge.companion.jellyfin.AppGraph*",
                    // Generated.
                    "com.cooper.binge.companion.jellyfin.BuildConfig",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        // 0 while the code it measures is landing, a PR at a time. The PR that completes the app
        // sets it to what the suite measures then; never lower it.
        verify {
            rule {
                minBound(0)
            }
        }
    }
}
