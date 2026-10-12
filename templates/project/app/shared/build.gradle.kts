import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "com.awakekt.awake.template.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        // Core 0.3 renamed the project runtime API. Keep the published 0.2 starter usable while
        // the engine's consumer gate builds this same template against the newer Core artifacts.
        val coreVersion = libs.versions.awake.asProvider().get().substringBefore('-').split('.').map(String::toInt)
        val legacyProjectRuntime = coreVersion[0] == 0 && coreVersion[1] < 3
        commonMain {
            kotlin.srcDir(if (legacyProjectRuntime) "src/core2Main/kotlin" else "src/core3Main/kotlin")
        }
        // The Android library packages only its own Java resources, so it takes the project from commonMain's.
        androidMain {
            resources.srcDir("src/commonMain/resources")
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.awake.engine.bootstrap)
            implementation(libs.awake.asset.shaders)
            implementation(libs.awake.asset.shader.pack)
            implementation(libs.awake.core.host)
            implementation(libs.awake.scene.authoring)
            // api: loadGame and createGame hand LoadedProject to every app module.
            api(libs.awake.project.runtime)
            implementation(libs.awake.backend.jolt)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        iosMain.dependencies {
            implementation(libs.awake.backend.vulkan)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
