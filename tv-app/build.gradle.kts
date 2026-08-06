// AGP 9's built-in Kotlin support compiles this module's Kotlin sources directly;
// the standalone `org.jetbrains.kotlin.android` plugin is intentionally not applied.
// See: https://developer.android.com/build/migrate-to-built-in-kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "no.esotericgames.quotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "no.esotericgames.quotes"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Placeholder for the future quote server. Not wired to any network call yet;
        // this is the single, clearly-identifiable location to change it later.
        // The Android emulator reaches the host machine via 10.0.2.2, not localhost —
        // see README.md for details.
        buildConfigField("String", "SERVER_BASE_URL", "\"http://10.0.2.2:8080\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Compose for TV ships its own Material3-based theme and components (focus-aware,
    // remote-friendly), used here instead of the phone androidx.compose.material3 artifact.
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    testImplementation(libs.kotlin.test.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
