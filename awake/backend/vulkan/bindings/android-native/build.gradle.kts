/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Plain Android library that owns the CMake/NDK build and bundled Vulkan
// validation layers. Split out of :awake-vulkan because AGP 9's
// com.android.kotlin.multiplatform.library plugin does not support
// externalNativeBuild; :awake-vulkan's androidMain depends on this module.
import java.security.MessageDigest

plugins {
    id("com.awakekt.awake.plugin.android-library")
    // No com.awakekt.awake.plugin.dokka here: this module has no Kotlin plugin at all (pure
    // com.android.library CMake/NDK build, generated JNI stubs only) -- Dokka's Android
    // integration logs "could not get Android Extension" trying to inspect it anyway, with
    // nothing to document.
    id("com.awakekt.awake.plugin.detekt")
    id("com.vanniktech.maven.publish")
    id("com.awakekt.awake.plugin.central-staging")
    id("com.awakekt.awake.plugin.spotless")
}

// Swappy (#1699): games-frame-pacing's static library and headers, unpacked from the AAR at a
// pinned version and checksum. Resolved through a configuration of its own, so the published
// AAR and its POM gain no dependency: the library is linked into libawake-vulkan.so.
val swappyVersion = "2.1.3"
val swappySha256 = "a9f83e0cf47d694ed2cea0ae995d5cf7411060d9d4b680610430d03d6b20ea27"
// The ABIs this module builds (see abiFilters), each needing its Swappy library.
val nativeAbis = listOf("arm64-v8a", "x86_64")
val swappyDir = layout.buildDirectory.dir("swappy")
val swappyAar: Configuration = configurations.create("swappyAar") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies { swappyAar("androidx.games:games-frame-pacing:$swappyVersion@aar") }

val extractSwappy = tasks.register<Sync>("extractSwappy") {
    group = "awake native"
    description = "Unpack Swappy's static library and headers from games-frame-pacing $swappyVersion"
    val aar = swappyAar
    inputs.property("sha256", swappySha256)
    from({ zipTree(aar.singleFile) }) {
        include("prefab/modules/swappy_static/**")
        eachFile { path = path.removePrefix("prefab/modules/swappy_static/") }
        includeEmptyDirs = false
    }
    into(swappyDir)
    doFirst {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(aar.singleFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == swappySha256) {
            "games-frame-pacing $swappyVersion has SHA-256 $digest, expected $swappySha256: update both together"
        }
    }
    doLast {
        val root = swappyDir.get().asFile
        val expected = nativeAbis.map { "libs/android.$it/libswappy_static.a" } + "include/swappy/swappyVk.h"
        val missing = expected.filterNot { root.resolve(it).isFile }
        check(missing.isEmpty()) { "games-frame-pacing $swappyVersion changed layout; missing $missing" }
    }
}
tasks.matching { it.name.startsWith("configureCMake") }.configureEach { dependsOn(extractSwappy) }

android {
    namespace = "com.awakekt.awake.vulkan.jni"
    ndkVersion = "26.1.10909125"

    defaultConfig {
        externalNativeBuild {
            cmake {
                // 64-bit only: the generated JNI marshalling code assumes pointer-sized
                // Vulkan handles, which 32-bit ABIs (uint64_t handles) don't satisfy
                abiFilters += nativeAbis
                cppFlags += listOf("-DVK_USE_PLATFORM_ANDROID_KHR", "-lvulkan")
                arguments += listOf(
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DANDROID_STL=c++_static",
                    "-DAWAKE_SWAPPY_DIR=${swappyDir.get().asFile.absolutePath}",
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("../src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes {
        debug {
            isJniDebuggable = true
        }
    }
}

// Generates JNI marshalling C++ from external fun declarations in :awake-vulkan's Kotlin
// source, using a vendored copy of jni-binding-generator (tools/jni-binding-generator —
// see that directory's README for why it's vendored rather than referenced by path).
//
// --kotlin-source points at the whole module (needed so the generator's struct/enum
// pre-pass can see types declared anywhere, e.g. models/info/VkBufferCreateInfo.kt), but
// --package-filter scopes actual *generation* to com.awakekt.awake.vulkan.gen —
// the new Phase 1d package. The legacy com.awakekt.awake.vulkan.Vulkan (58
// functions, awake-vulkan-generator-backed) is deliberately left alone: some of its
// existing signatures (e.g. Array<VkLayerProperties> as a *return type*, as opposed to a
// struct field) use shapes jni-binding-generator doesn't support at the function level yet.
// New Vulkan API surface should be added to the .gen package; see
// docs/decisions/D10-codegen-derisk-findings.md for the full history.
//
// IMPORTANT — neither task below runs automatically before the native build.
// Annotated functions delegate from generated JNI wrappers to named implementations in
// ordinary *_native.cpp files. Unannotated generated entry points remain TODO stubs that
// require the legacy hand-edit workflow until they are migrated.
//
// This also means `--check` (byte-for-byte diff against a fresh generation) CANNOT be
// wired in as an automatic build gate: once a hand-edit exists, `--check` fails forever,
// even when the Kotlin signature hasn't changed — it has no way to tell "signature drift"
// apart from "TODO body intentionally filled in". `checkJniBindings` below is kept as a
// manually-run diagnostic (most useful right after changing a .gen signature, before
// re-applying hand edits, to see the true diff) — it is NOT a build dependency. The actual
// safety net for signature drift is the C++ compiler itself: if a .gen struct's shape
// changes incompatibly, the hand-written body referencing its old fields fails to compile,
// pointing at the exact mismatch.
val kotlinSourceForJni = layout.projectDirectory.dir("../src")
// NOTE: this must resolve to awake-backend-vulkan/src/main/cpp/generated (NOT
// android-native/src/main/cpp/generated) because externalNativeBuild.cmake.path above
// points CMake's project root at "../src/main/cpp/CMakeLists.txt" — i.e.
// awake-backend-vulkan/src, a sibling of this module, not a subdirectory of it.
val jniOutputDir = layout.projectDirectory.dir("../src/main/cpp/generated")
val jniScriptsDir = rootProject.layout.projectDirectory.dir("tools/jni-binding-generator/scripts")
val jniScript = jniScriptsDir.file("jni-binding-generator.py")
val jniPackageFilter = "com.awakekt.awake.vulkan.gen"

tasks.register<Exec>("generateJniBindings") {
    group = "awake native"
    description = "Generate JNI marshalling C++ from Kotlin external functions (jni-binding-generator). " +
        "Run manually after changing a .gen package signature; annotated native implementations stay in *_native.cpp."

    inputs.dir(jniScriptsDir)
    inputs.dir(kotlinSourceForJni)
    outputs.dir(jniOutputDir)

    commandLine(
        "python3",
        jniScript.asFile.absolutePath,
        "--kotlin-source", kotlinSourceForJni.asFile.absolutePath,
        "--output", jniOutputDir.asFile.absolutePath,
        "--package-filter", jniPackageFilter,
    )
}

tasks.register<Exec>("checkJniBindings") {
    group = "awake native"
    description = "Fail if committed generated JNI bindings are stale relative to the Kotlin source " +
        "(run generateJniBindings, then verify any unannotated native bodies)"

    inputs.dir(jniScriptsDir)
    inputs.dir(kotlinSourceForJni)

    commandLine(
        "python3",
        jniScript.asFile.absolutePath,
        "--kotlin-source", kotlinSourceForJni.asFile.absolutePath,
        "--output", jniOutputDir.asFile.absolutePath,
        "--package-filter", jniPackageFilter,
        "--check",
    )
}


// Published because the bindings' Android flavor api-depends on this module: without its own
// coordinates the bindings' Android POM would reference an artifact that exists nowhere and
// every consumer's resolution would fail. Kept as a separate AAR (not folded into the
// bindings artifact) for the same reason the module exists at all -- AGP 9's KMP plugin has
// no externalNativeBuild, so the CMake/NDK build needs a plain library to live in.
mavenPublishing {
    publishToMavenCentral()
    val hasSigningKey = hasProperty("signing.keyId") ||
        hasProperty("signing.secretKey") ||
        hasProperty("signingInMemoryKey") ||
        hasProperty("signingKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null ||
        System.getenv("SIGNING_KEY") != null

    if (hasSigningKey) {
        signAllPublications()
    }
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = com.vanniktech.maven.publish.SourcesJar.Empty(),
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
        ),
    )
    coordinates("com.awakekt.awake", "vulkan-kmp-android-native", version.toString())
    pom {
        name.set("Vulkan KMP Android Native")
        description.set("NDK-built JNI library and validation layers backing vulkan-kmp on Android")
        url.set("https://docs.awakekt.com")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/awakekt/awake.git")
            developerConnection.set("scm:git:ssh://github.com:awakekt/awake.git")
            url.set("https://github.com/awakekt/awake")
        }
        developers {
            developer {
                name.set("Ron June Valdoz")
                email.set("ronjune.valdoz@gmail.com")
            }
        }
    }
}
