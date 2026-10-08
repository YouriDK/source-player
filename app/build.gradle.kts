import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Load secrets from local.properties (git-ignored)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Release signing stays optional at configuration time so a clone without
// local.properties can still sync and build debug. Hoisted to script scope so the
// signingConfigs block below and the execution-time guard further down test the
// exact same condition and cannot drift apart.
val releaseStoreFilePath: String? = localProps["signing.store.file"] as String?

// ---- Version ----------------------------------------------------------------
// MAJOR.MINOR.PATCH lives in gradle.properties and nothing else declares it.
// versionCode is derived so it always moves forward with the name; Play rejects
// a reused or lowered code, and hand-maintaining both is how that happens.
val appVersionName: String = providers.gradleProperty("source.version").get().trim()

val versionParts = appVersionName.split(".")
require(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "source.version must be MAJOR.MINOR.PATCH (got '$appVersionName')"
}
val (vMajor, vMinor, vPatch) = versionParts.map(String::toInt)
// The scheme packs minor and patch into two decimal digits each, so 2.6.100 and
// 2.7.0 would collide silently. Fail the build instead of shipping a duplicate.
require(vMinor < 100 && vPatch < 100) {
    "source.version minor/patch must each stay under 100 (got '$appVersionName')"
}
val appVersionCode: Int = vMajor * 10000 + vMinor * 100 + vPatch

// Short commit SHA, exposed through BuildConfig only — versionName stays clean
// semver because that is what Play displays. Read through providers.exec so the
// configuration cache tracks it instead of baking in a stale value, and degrade
// to "unknown" outside a git checkout (source archives, shallow CI exports).
val gitSha: String =
    if (rootProject.file(".git").exists()) {
        runCatching {
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    } else {
        "unknown"
    }

android {
    namespace = "com.source.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.source.player"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Last.fm API credentials — sourced from local.properties, never from source code
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")

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
        if (releaseStoreFilePath != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
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
            // Nullable on purpose: see the release-signing guard below the android
            // block, which turns a missing config into an execution-time failure
            // instead of a configuration-time one.
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

// A null release signingConfig would otherwise package an unsigned artifact and
// report success; Play only rejects it at upload. Guard it, but as a task action:
//
//  - not error() in the buildTypes block — that runs at configuration time and
//    would break `tasks`, IDE sync and debug builds on a machine with no keystore,
//    which is exactly the setup the nullability exists to support;
//  - not gradle.taskGraph.whenReady — the configuration cache is on
//    (gradle.properties), and a cache hit skips configuration entirely, so the
//    callback would never fire and the check would silently disappear.
//
// A doFirst is serialized with the task, so it runs on cache hits and misses
// alike. `configured` is read at configuration time into a plain Boolean local:
// the action captures that, never the script object or Project, which is what
// keeps it configuration-cache serializable.
//
// The packaging tasks are guarded alongside the lifecycle ones: bundleRelease and
// assembleRelease are the last nodes in the graph, so on their own they would only
// fail once an unsigned artifact already sat in build/outputs. package*Release run
// immediately before that write. Scoping to these four rather than preReleaseBuild
// keeps compileReleaseKotlin, lintRelease and testReleaseUnitTest working on a
// machine with no keystore.
val releaseSigningGuarded = setOf(
    "packageRelease", "packageReleaseBundle", "assembleRelease", "bundleRelease",
)
tasks.matching { it.name in releaseSigningGuarded }
    .configureEach {
        val configured = releaseStoreFilePath != null
        doFirst {
            check(configured) {
                "signingConfig 'release' introuvable — vérifie les clés signing.* dans local.properties"
            }
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

// Room schema export via the Room Gradle plugin — unlike the raw KSP arg, it
// handles the space in this project's path. Exported JSON under app/schemas/ is
// version-controlled and is the baseline for writing/validating migrations.
room { schemaDirectory("$projectDir/schemas") }

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
    implementation(libs.androidx.security.crypto) // Freebox app_token at rest

    // Coroutines
    implementation(libs.coroutines.android)

    // Performance
    implementation(libs.profileinstaller)
    debugImplementation(libs.leakcanary)
}
