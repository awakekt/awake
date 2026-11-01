/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import java.security.MessageDigest

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

// libktx's natives for every desktop Awake runs on; LWJGL loads the one for the host.
val lwjglNatives = listOf("natives-linux", "natives-macos-arm64", "natives-windows")

kotlin {
    android { namespace = "com.awakekt.awake.asset.ktx.loader" }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:asset:texture-ktx"))
            api(project(":awake:engine:render:contract"))
            api(project(":awake:core:io"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        named("desktopMain") {
            dependencies {
                // The BOM lifts every LWJGL module an app has, wgpu4k's lwjgl-glfw included, to the
                // version lwjgl-ktx needs: LWJGL modules of different versions don't load together.
                implementation(project.dependencies.platform(libs.lwjgl.bom))
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.ktx)
                val version = libs.versions.lwjgl.get()
                lwjglNatives.forEach { natives ->
                    runtimeOnly("org.lwjgl:lwjgl:$version:$natives")
                    runtimeOnly("org.lwjgl:lwjgl-ktx:$version:$natives")
                }
            }
        }
        named("wasmJsMain") {
            // Awaits the Basis Universal transcoder's asynchronous start.
            dependencies { implementation(libs.kotlinx.coroutines.core) }
        }
        named("desktopTest") {
            dependencies { implementation(libs.kotlinx.coroutines.test) }
        }
        named("wasmJsTest") {
            dependencies { implementation(libs.kotlinx.coroutines.test) }
        }
    }
}

// The vendored Basis Universal transcoder in src/wasmJsMain/resources/basis/ must be upstream's
// prebuilt release byte for byte: webgl/transcoder/build/ at tag v2_50
// (9bebe16726b3a61c8c213eeee3b7cffb462ef34e), as NOTICE.md and source-provenance.json record. It is
// checked before the wasm resources are packed and with `check`, so a changed file fails the build.
// To update it, copy the new release's two files unchanged and update the tag, the commit and these
// digests here, in the provenance registry and in NOTICE.md.
val basisTranscoderRelease = mapOf(
    "basis_transcoder.js" to "720dd9bd09c7cada6d87f1b7b70cec713df04da88cd641ac3212559353834dc8",
    "basis_transcoder.wasm" to "a0f65d4a30ecb3269d01ead7d0a3477d2b0208146d083625a90623f473f6c139",
)
val basisTranscoderDirectory = layout.projectDirectory.dir("src/wasmJsMain/resources/basis")
val verifyBasisTranscoder = tasks.register("verifyBasisTranscoder") {
    group = "verification"
    description = "Checks the vendored Basis Universal transcoder against its release's SHA-256"
    val files = basisTranscoderRelease.keys.associateWith { basisTranscoderDirectory.file(it).asFile }
    val expected = basisTranscoderRelease
    inputs.files(files.values)
    doLast {
        files.forEach { (name, file) ->
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(digest == expected.getValue(name)) {
                "$name is not the pinned Basis Universal v2_50 file: its SHA-256 is $digest, not ${expected.getValue(name)}"
            }
        }
    }
}
tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(verifyBasisTranscoder) }
tasks.named("check") { dependsOn(verifyBasisTranscoder) }

mavenPublishing {
    pom {
        name.set("Awake KTX2 Texture Loader")
        description.set("Loads KTX2 textures to a GPU block format or RGBA8, transcoding Basis Universal on desktop with libktx")
    }
}
