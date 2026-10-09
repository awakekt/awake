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
    namespace = "com.awakekt.awake.showcase.android"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "com.awakekt.awake.showcase.android"
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Shrunk as a game's release is, with no keep rules of its own: what Core's native code
            // reaches by name comes from the consumer rules Core's artifacts ship. The nightly device
            // tests launch it, so a lookup those rules miss crashes there and not in a game.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // The debug key, so CI can install the release it built.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":samples:engine-showcase"))
    implementation(project(":awake:backend:vulkan"))
    implementation(project(":awake:engine:platform"))
    implementation(project(":awake:engine:window"))
    // Debug only: LeakCanary aborts non-debuggable builds, so a library must never ship it.
    debugImplementation(libs.leakcanary.android)
}
