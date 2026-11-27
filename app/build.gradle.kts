import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sportassistant"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.sportassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.12.0"

        // HTTPS URL of a version.json (see README, "Updates"). Empty disables the update check.
        // Set in gradle.properties or with -PupdateUrl=...
        val updateUrl = (project.findProperty("updateUrl") as String?).orEmpty()
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    // Material Symbols (rounded) as vector icons: one consistent, theme-aware style for the whole UI.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    val camerax = "1.7.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Bundled (not Play-services) model: works offline, no first-run download.
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub on the JVM; the real implementation is needed for parser tests.
    testImplementation("org.json:json:20250517")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
}
