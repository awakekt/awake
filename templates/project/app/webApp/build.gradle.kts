import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
        // The browser fetches the project next to index.html, so the web app serves the shared one.
        wasmJsMain {
            resources.srcDir(rootProject.file("app/shared/src/commonMain/resources"))
        }
    }
}
