plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun oauthCredential(name: String, env: String): String {
    System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }?.let { return it }
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        local.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || !trimmed.contains("=")) return@forEach
            val idx = trimmed.indexOf('=')
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key == name && value.isNotBlank()) return value
        }
    }
    return ""
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.conreo.couchytv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.conreo.couchytv"
        minSdk = 21
        targetSdk = 34
        versionCode = 7
        versionName = "1.0.6"

        // Telegram client (api_id + api_hash from https://my.telegram.org) for
        // optional Saved-Messages backup. Empty = Cloud options toast "not configured".
        // Set via local.properties, gradle.properties, or env (never commit secrets).
        buildConfigField(
            "int",
            "TELEGRAM_API_ID",
            (oauthCredential("telegram.api.id", "TELEGRAM_API_ID").toIntOrNull() ?: 0).toString(),
        )
        buildConfigField(
            "String",
            "TELEGRAM_API_HASH",
            quotedBuildConfig(oauthCredential("telegram.api.hash", "TELEGRAM_API_HASH")),
        )
    }

    // Release signing key, supplied by CI via environment variables (from GitHub
    // secrets). Absent locally and on F-Droid, so this config stays inert there.
    val ciKeystore = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing priority:
            //  • CI (KEYSTORE_FILE env set)  → your real release key, from secrets.
            //  • -PlocalSign                 → the auto-generated debug key (local test).
            //  • otherwise (incl. F-Droid)   → UNSIGNED; F-Droid signs with its own key.
            signingConfig = when {
                ciKeystore != null -> signingConfigs.getByName("ci")
                project.hasProperty("localSign") -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    // Reproducibility for F-Droid: don't embed the Google-signed dependency
    // metadata block in the artifact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // API 21 TVs: extract .so so TDLib's JNI loads reliably.
            useLegacyPackaging = true
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/tdlibJni"))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    // iOS-style continuous (squircle) corners — perceptually smoother than the
    // circular-arc RoundedCornerShape.
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Applies the bundled baseline profile (src/main/baseline-prof.txt) so the
    // startup + scroll paths are AOT-compiled on first run instead of JIT'd —
    // the big cold-start "screen shows but not smooth yet" win.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Video wallpaper playback — plays Apple .mov aerials that MediaPlayer can't
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // OkHttp data source: lets us stream aerials through a trust-all TLS client
    // so Apple's sylvan.apple.com (cert chain many TV trust stores reject) works.
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR encoding for Telegram device-link on the TV (no camera on the box).
    implementation("com.google.zxing:core:3.5.3")
    // Reach ActivityManager.forceStopPackage / PackageManager.deleteApplicationCacheFiles
    // (hidden on API 28+). Apache-2.0; F-Droid-safe. Permissions still required.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}

// TDLib natives only (the published AAR needs Kotlin 2.3 + compileSdk 36).
// We vendor a matching JNI shim in io.xbot.tdlib and unpack the .so files here.
val tdlibAar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    tdlibAar("io.github.xephosbot:tdlib-kmp-android:1.8.62")
}

val extractTdlibJni by tasks.registering {
    val dest = layout.buildDirectory.dir("generated/tdlibJni")
    inputs.files(tdlibAar)
    outputs.dir(dest)
    doLast {
        copy {
            from(zipTree(tdlibAar.singleFile))
            include("jni/**/*.so")
            eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
            }
            includeEmptyDirs = false
            into(dest.get().asFile)
        }
    }
}

tasks.named("preBuild").configure { dependsOn(extractTdlibJni) }
