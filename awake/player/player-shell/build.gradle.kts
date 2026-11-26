/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "awake-player.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:player"))
        }
        named("desktopMain") {
            dependencies {
                implementation(project(":awake:backend:vulkan"))
                implementation(project(":awake:engine:window"))
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(project(":awake:backend:webgpu"))
                implementation(project(":awake:core:audio"))
                implementation(libs.kotlinx.browser)
            }
            resources.srcDir(project(":awake:backend:webgpu").file("src/wasmJsMain/resources"))
            // basis/basis_transcoder.{js,wasm} beside index.html for cooked KTX2/Basis textures.
            resources.srcDir(project(":awake:asset:texture-ktx-loader").file("src/wasmJsMain/resources"))
        }
    }
}

tasks.named("wasmJsProcessResources") {
    dependsOn(":awake:asset:texture-ktx-loader:verifyBasisTranscoder")
}

// Several Core modules share jar filenames (e.g. runtime-desktop.jar). Prefix each jar by its
// repository path or Maven group so scripts/package-macos-player.sh can stage all jars in one lib/.
fun stagedJarName(jar: File, componentId: org.gradle.api.artifacts.component.ComponentIdentifier?): String {
    val module = jar.parentFile?.takeIf { it.name == "libs" }?.parentFile?.takeIf { it.name == "build" }?.parentFile
    if (module != null && module.startsWith(rootDir)) {
        return module.relativeTo(rootDir).invariantSeparatorsPath.replace('/', '-') + "-" + jar.name
    }
    return if (componentId is ModuleComponentIdentifier) "${componentId.group}-${jar.name}" else jar.name
}

tasks.register<Sync>("desktopPackageInput") {
    val desktopJar = tasks.named("desktopJar")
    dependsOn(desktopJar)
    val runtimeArtifacts = configurations.named("desktopRuntimeClasspath")
        .flatMap { it.incoming.artifacts.resolvedArtifacts }
    from(desktopJar)
    from(runtimeArtifacts.map { artifacts -> artifacts.map { it.file } }) {
        eachFile {
            val sourceFile = file
            val id = runtimeArtifacts.get().firstOrNull { it.file == sourceFile }?.id?.componentIdentifier
            name = stagedJarName(sourceFile, id)
        }
    }
    into(layout.buildDirectory.dir("desktop-package/lib"))
}
