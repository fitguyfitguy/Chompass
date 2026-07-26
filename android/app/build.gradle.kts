import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

// Release signing config is read from android/keystore.properties (gitignored).
// When the file is absent (fresh checkout, CI without secrets), assembleRelease
// still works but emits an unsigned APK. Generate one with:
//   keytool -genkey -v -keystore fudai-release.jks -keyalg RSA -keysize 2048 \
//           -validity 10000 -alias fudai
// then create keystore.properties with storeFile / storePassword / keyAlias / keyPassword.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

// Optional dev convenience: a Gemini API key read from the gitignored
// android/secrets.properties (add a line `GEMINI_API_KEY=AIza...`). It is baked
// into the DEBUG BuildConfig only and seeded into the encrypted KeyStore on
// first launch so you don't have to re-enter it in Settings after every
// reinstall. Release builds always get an empty string — never ship a key.
// secrets.properties is preferred; local.properties works as a fallback, but
// the Android tooling rewrites that file and can drop the line, so prefer the
// dedicated file.
val secretsProps = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { fallback ->
        val lp = Properties().apply { load(fallback) }
        lp.getProperty("GEMINI_API_KEY")?.let { if (getProperty("GEMINI_API_KEY") == null) setProperty("GEMINI_API_KEY", it) }
    }
}
fun bcString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val geminiDebugApiKey: String = (secretsProps.getProperty("GEMINI_API_KEY") ?: "").trim()
// Optional: -PreleaseAbi=arm64-v8a for a single-ABI release APK (local smoke test /
// F-Droid). Uses ndk.abiFilters with ABI splits disabled so the artifact is the
// plain app-*-release(-unsigned).apk name F-Droid discovers without `output:`.
val releaseAbi: String? = providers.gradleProperty("releaseAbi").orNull

android {
    namespace = "app.chompass"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.chompass"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "3.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (releaseAbi != null) {
            ndk {
                abiFilters += releaseAbi
            }
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "NONE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config if keystore.properties exists. Without
            // it, gradle emits app-release-unsigned.apk and you sign manually with
            // apksigner before uploading to a store or release host.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Never bake an API key into a shippable build.
            buildConfigField("String", "GEMINI_API_KEY", bcString(""))
        }
        debug {
            // Suffix the package + version so the debug build installs side-by-side
            // with a release build on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Seeded into the encrypted KeyStore on first launch (see ChompassApp).
            buildConfigField("String", "GEMINI_API_KEY", bcString(geminiDebugApiKey))
        }
        create("debug2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug2"
            versionNameSuffix = "-debug2"
        }
    }
    // Grounded USDA SQLite lives under src/debug/assets (not main) so release/F-Droid
    // APKs stay lean while the feature is gated. debug2 reuses the same assets.
    // Heavy grounded Kotlin (orchestrator, USDA index, tool loop, sheets) lives in
    // src/grounded and is compiled for debug/test only; release gets thin stubs.
    sourceSets {
        getByName("debug") {
            kotlin.srcDir("src/grounded/java")
        }
        getByName("debug2") {
            assets.srcDir("src/debug/assets")
            kotlin.srcDir("src/grounded/java")
        }
        getByName("release") {
            kotlin.srcDir("src/groundedStubs/java")
        }
        getByName("test") {
            kotlin.srcDir("src/grounded/java")
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

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    // IzzyOnDroid (and F-Droid infra) strongly prefer keeping APKs small.
    // Compress native *.so libs to reduce the on-disk size of "fat" builds.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Avoid Google-encrypted "dependency info" blobs in the APK.
    // These are often flagged during Izzy/F-Droid scanning and add size overhead.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // Full releases: per-ABI APKs + universal. Single-ABI (-PreleaseAbi=…): no
    // splits — plain APK name + abiFilters only (see releaseAbi above).
    splits {
        abi {
            if (releaseAbi != null) {
                isEnable = false
            } else {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86_64")
                isUniversalApk = true
            }
        }
    }
}

// Cross-app parity fixtures live at repo-root testdata/parity/ (shared with the PWA).
tasks.withType<Test>().configureEach {
    val parityDir = rootProject.projectDir.resolve("../testdata/parity").normalize()
    systemProperty("chompass.parity.dir", parityDir.absolutePath)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.cpp.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // On-device LLM (LiteRT-LM) — see docs/ON_DEVICE_LLM.md. Promoted from
    // debugImplementation now that OnDeviceLlmClient backs the production
    // ON_DEVICE dispatch path (behind the onDeviceFeatureVisible flag,
    // default off). Whether litertlm-android + a runtime fetch of a
    // non-buildable binary blob from Hugging Face clear F-Droid's guidelines
    // is still an open question (production plan Phase 3). The Google Play
    // distribution flavor is disabled for now — see docs/DISTRIBUTION.md.
    implementation(libs.litertlm.android)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
