import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
}

if (gradle.startParameter.taskNames.any { it.contains("google", true) }) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// CI passes the release tag's version (-PappVersion=1.2.0); local builds use the default
val baseVersion = (findProperty("appVersion") as String?) ?: "1.0.0"

fun computeGitCommitHash(): String {
    val envHash = System.getenv("COMMIT_HASH")
    if (!envHash.isNullOrBlank()) {
        return envHash.take(7)
    }
    val gitHash = try {
        providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim().take(7)
    } catch (e: Exception) {
        try {
            providers.exec {
                commandLine("git", "rev-parse", "--verify", "--short=7", "HEAD")
            }.standardOutput.asText.get().trim()
        } catch (e2: Exception) {
            ""
        }
    }
    if (gitHash.isNotEmpty()) {
        return gitHash
    }
    val fallbackHash = System.getenv("GITHUB_SHA")
    if (!fallbackHash.isNullOrBlank()) {
        return fallbackHash.take(7)
    }
    return ""
}

val gitCommitHash = computeGitCommitHash()

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingProperty(key: String): String? =
    localProperties.getProperty(key) ?: System.getenv(key)

android {
    namespace = "ani.arkhime.com"
    compileSdk = 37

    signingConfigs {
        // Release signing is read from local.properties or environment variables, never hardcoded
        val releaseStoreFile = signingProperty("RELEASE_STORE_FILE")
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingProperty("RELEASE_STORE_PASSWORD")
                keyAlias = signingProperty("RELEASE_KEY_ALIAS")
                keyPassword = signingProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "ani.arkhime.com"
        minSdk = 26
        targetSdk = 36

        versionName = if (gitCommitHash.isNotEmpty()) "$baseVersion+$gitCommitHash" else baseVersion
        versionCode = baseVersion.split(".")
            //noinspection WrongGradleMethod
            .map { it.toInt() * 100 }
            .joinToString("")
            .toInt()

        signingConfig = signingConfigs.getByName("debug")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("fdroid") {
            dimension = "store"
            versionNameSuffix = "-fdroid"
        }
        create("google") {
            dimension = "store"
            isDefault = true
        }
    }

    buildTypes {
        create("alpha") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-alpha01"
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher_alpha"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_alpha_round"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            isDefault = true
        }

        getByName("debug") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta01"
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher_beta"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_beta_round"
            isDebuggable = false
        }

        getByName("release") {
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_round"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("**/libavcodec.so")
            pickFirsts.add("**/libavdevice.so")
            pickFirsts.add("**/libavfilter.so")
            pickFirsts.add("**/libavformat.so")
            pickFirsts.add("**/libavutil.so")
            pickFirsts.add("**/libswresample.so")
            pickFirsts.add("**/libswscale.so")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-XXLanguage:+ContextParameters",
            "-Xmulti-platform"
        )
    }
}

configurations.all {
    exclude(group = "org.json", module = "json")
}

dependencies {

    // Firebase
    add("googleImplementation", platform(libs.firebase.bom))
    add("googleImplementation", libs.bundles.firebase)

    // AndroidX
    implementation(libs.bundles.androidx)
    implementation(libs.androidx.webkit)

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    // Core libs
    implementation(libs.bundles.misc)
    implementation(libs.metro.runtime)
    implementation(libs.androidx.profileInstaller)

    // Glide
    implementation(libs.bundles.glide)
    ksp(libs.glide.ksp)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // UI
    implementation(libs.material)
    implementation(libs.materialKolor)
    implementation(files("libs/AnimatedBottomBar-7fcb9af.aar"))
    implementation(libs.flexbox)
    implementation(libs.kenburns)
    implementation(libs.subsampling)
    implementation(libs.dialogs)
    implementation(libs.charts)

    implementation(libs.bundles.markwon)
    implementation(libs.bundles.groupie)
    implementation(libs.bundles.rx)
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // LeakCanary & Plumber (Active in Debug, Alpha, and Release builds for memory leak diagnosis)
    implementation(libs.leakcanary.android)
    implementation(libs.leakcanary.plumber)
}
