// Root build file. Individual modules declare and configure their own plugins;
// this file only registers plugin versions once via the version catalog so
// every module resolves the same version without redeclaring it.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
}
