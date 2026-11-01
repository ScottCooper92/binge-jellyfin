plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied by :app only when a google-services.json is present; see the note there.
    alias(libs.plugins.google.services) apply false
}
