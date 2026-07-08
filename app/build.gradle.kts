import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Load secrets from local.properties (git-ignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.source.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.source.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Last.fm API credentials — sourced from local.properties, never from source code
        buildConfigField("String", "LASTFM_API_KEY",
            "\"${localProps["lastfm.api_key"] ?: ""}\""
        )
        buildConfigField("String", "LASTFM_API_SECRET",
            "\"${localProps["lastfm.api_secret"] ?: ""}\""
        )
    }

    signingConfigs {
        // Release signing — credentials live in local.properties (git-ignored), never in source.
        // Only configured when a keystore path is present, so debug builds work without it.
        val storeFilePath = localProps["signing.store.file"] as String?
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProps["signing.store.password"] as String?
                keyAlias = localProps["signing.key.alias"] as String?
                keyPassword = localProps["signing.key.password"] as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release signing config if it was configured above.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

// Room schema export is disabled to avoid KSP path-with-spaces issue on this machine.
// Re-enable via ksp { arg("room.schemaLocation", ...) } from a path without spaces.

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.startup)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Media3 (media3-common comes in transitively as an API dep of media3-exoplayer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    // MediaRouter (Wi-Fi / Sonos / Cast route discovery)
    implementation(libs.mediarouter)

    // Chromecast & Sonos
    implementation(libs.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    // Ktor (Last.fm)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.neg)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    // Audio tag writing (tag editor)
    implementation(libs.jaudiotagger)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Performance
    implementation(libs.profileinstaller)
    debugImplementation(libs.leakcanary)
}
