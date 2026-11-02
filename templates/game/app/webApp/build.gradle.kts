import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Puts the Basis Universal transcoder (basis/) beside the page, checked against Core's pinned digests.
    // Keep it on the same version as Core (`awake` in the catalog).
    alias(libs.plugins.awakeBasisTranscoder)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
        wasmJsMain.dependencies {
            implementation(libs.awake.backend.webgpu)
        }
    }
}
