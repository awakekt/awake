/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    alias(libs.plugins.android.application)
    id("com.awakekt.awake.plugin.spotless")
}

android {
    namespace = "com.awakekt.awake.player"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "com.awakekt.awake.player"
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Shrunk and obfuscated by R8; Core libraries contribute their consumer keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            // libawake-vulkan ships for arm64-v8a and x86_64; filtering ABIs shrinks the prebuilt
            // android-player.apk and excludes 4 KB-aligned third-party .so binaries on Android 15+.
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

// Keep Khronos Vulkan validation layers in debug APKs, strip them (~80 MB) from release builds.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes.add("**/libVkLayer_khronos_validation.so")
    }
}

dependencies {
    implementation(project(":awake:player"))
    implementation(project(":awake:backend:vulkan"))
    implementation(project(":awake:engine:platform"))
    implementation(project(":awake:engine:window"))
}
