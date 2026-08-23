plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Declared but never applied here: app/build.gradle.kts applies it only when
    // google-services.json exists, so the project builds without any Firebase setup.
    alias(libs.plugins.google.services) apply false
}
