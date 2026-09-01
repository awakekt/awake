/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Awake
 * Awake.awake-backend-jolt
 *
 * Copyright (c) ronjunevaldoz 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.native-build-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.physics.jolt"
        // Instrumented, not host, and the only way this backend can be tested at all: jolt-jni's
        // Android artifact ships device ABIs, so `System.loadLibrary("joltjni")` has nothing to
        // load in a JVM host test. `sourceSetTreeName = "test"` is what puts androidDeviceTest in
        // the test tree, so it inherits commonTest rather than needing a copy of it.
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    // Jolt Physics integration slice 2 (see docs/reference/decision-log.md): real iOS
    // backend via JoltC (SecondHalfGames/JoltC, a plain-C wrapper around Jolt Physics),
    // vendored as a git submodule at ios-native/JoltC -- mirrors awake-backend-vulkan's
    // MoltenVK cinterop precedent (a vendored C/C++ library built from source, not a
    // committed prebuilt binary). JoltC itself depends on JoltPhysics as its own nested
    // submodule (ios-native/JoltC/JoltPhysics) and its own CMakeLists.txt builds both
    // `joltc` (the C wrapper) and `Jolt` (the physics engine itself, via
    // `add_subdirectory(JoltPhysics/Build)`) as separate static libraries in one configure.
    // wasmJs (JoltPhysics.js) is still deferred -- that target keeps its explicit
    // TODO()-throwing stub `JoltPhysicsWorld` just so the module compiles there, no JS
    // interop of any kind lives here yet.
    //
    // One-time setup, from awake/backend/jolt/ios-native/JoltC:
    //   git submodule update --init --recursive
    // Then build both target slices (device + simulator) via the Gradle tasks below:
    //   ./gradlew :awake:backend:jolt:buildJoltCIosArm64 :awake:backend:jolt:buildJoltCIosSimulatorArm64
    val joltCDir = file("ios-native/JoltC")
    val joltCBuildDir = mapOf(
        "iosArm64" to layout.buildDirectory.dir("joltc-native/iosArm64").get().asFile,
        "iosSimulatorArm64" to layout.buildDirectory.dir("joltc-native/iosSimulatorArm64")
            .get().asFile,
    )
    // CMAKE_OSX_SYSROOT per target -- device builds against the iphoneos SDK, the
    // Apple-Silicon simulator against iphonesimulator; CMake's iOS cross-compile support
    // (CMAKE_SYSTEM_NAME=iOS) picks the matching clang -isysroot/-arch flags from these two
    // settings without needing a separate hand-written toolchain file.
    val joltCSysroot = mapOf(
        "iosArm64" to "iphoneos",
        "iosSimulatorArm64" to "iphonesimulator",
    )
    // Simulator only. Asserts are what make Jolt check its callers at all -- wrong body type,
    // a body read without its lock, a layer out of range -- so with them off those are silent
    // corruption rather than a stop. The simulator is where tests run, so it is where the checks
    // are worth paying for.
    //
    // What they do NOT give you is the message. Jolt ships `DummyAssertFailed`, which returns true
    // to breakpoint and prints nothing, and `DummyTrace`, which asserts. So a violation surfaces as
    // `signal 5: Trace/BPT trap` and a stack trace, not as text -- read the crash report under
    // ~/Library/Logs/DiagnosticReports and the frame below `JPH::DummyTrace` is the site. Seeing
    // `DummyTrace` at the top is itself the tell: Jolt formatted a diagnostic and had nowhere to
    // put it. Installing a real handler means setting the C++ globals `JPH::Trace` and
    // `JPH::AssertFailed`, which cinterop cannot reach and JoltC does not expose.
    //
    // Left OFF for the device, which is shipped code: an assert there stops a player's game over
    // something the simulator build should have caught, and the checks are not free.
    val joltCAsserts = mapOf(
        "iosArm64" to "OFF",
        "iosSimulatorArm64" to "ON",
    )

    listOf(
        "iosArm64",
        "iosSimulatorArm64"
    ).forEach { targetName ->
        val nativeBuildDir = joltCBuildDir.getValue(targetName)
        val sysroot = joltCSysroot.getValue(targetName)
        val useAsserts = joltCAsserts.getValue(targetName)
        val capitalizedTargetName = targetName.replaceFirstChar { it.uppercase() }
        val target =
            kotlin.targets.getByName(targetName) as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

        registerCMakeLibrary(
            name = "JoltC$capitalizedTargetName",
            sourceDir = joltCDir,
            buildDir = nativeBuildDir,
            buildType = "Release",
            defines = listOf(
                "CMAKE_SYSTEM_NAME=iOS",
                "CMAKE_OSX_SYSROOT=$sysroot",
                "CMAKE_OSX_ARCHITECTURES=arm64",
                "CMAKE_OSX_DEPLOYMENT_TARGET=13.0",
                // Passed at the top level, which satisfies both the JoltC and the nested
                // JoltPhysics `option(USE_ASSERTS)` -- one cache entry, one setting, so the
                // wrapper and the engine cannot disagree about it.
                "USE_ASSERTS=$useAsserts",
            ),
            // JoltPhysics is the slow half of a rebuild, so parallelise it. ccache is applied
            // by the shared helper.
            buildArgs = listOf(
                "--target", "joltc",
                "--", "-j", Runtime.getRuntime().availableProcessors().toString(),
            ),
            description = "Build the JoltC + JoltPhysics static libraries for iOS " +
                "($targetName) -- manual/on-demand, like buildDesktopNative: CMake " +
                "configure+build is too slow to run on every Kotlin edit, and these libraries " +
                "only change when the vendored JoltC/JoltPhysics C++ sources do.",
        )

        // Gradle's binaries.X { linkerOpts(...) } only affects binaries THIS module builds
        // directly, not a downstream consumer's own final link step -- same lesson already
        // documented in awake-backend-vulkan/build.gradle.kts for MoltenVK. So the real
        // linker flags live in a generated per-target .def file (paths differ: each target's
        // static libs land under a different build dir), not in
        // target.binaries.framework { linkerOpts(...) }. Generated eagerly at configuration
        // time (like MoltenVK's own per-target .def) since the linker flags are just build
        // directory paths -- known before the native libraries are actually built, same as
        // MoltenVK's own generated .def doesn't need the xcframework to exist yet either.
        registerGeneratedDefCinterop(
            target = target,
            interopName = "JoltC",
            baseDefFile = project.file("src/nativeInterop/cinterop/JoltC.def"),
            headerDirs = listOf(joltCDir),
            linkerOpts = listOf(
                "-L${nativeBuildDir.path}", "-ljoltc",
                "-L${nativeBuildDir.resolve("JoltPhysics/Build").path}", "-lJolt",
                "-lc++",
            ),
        )
    }

    jvm("desktop")

    @Suppress("OPT_IN_USAGE")
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:physics:api"))
            // QuatEuler.kt's pure quaternion-to-Euler conversion takes/returns Vec3.
            implementation(project(":awake:core:math"))
        }
        named("androidDeviceTest") {
            // Shared with desktopTest rather than duplicated: desktop and Android are the same
            // jolt-jni binding, and these assert what that binding can do. The module's main
            // sources are duplicated between the two for want of a shared JVM source set; there is
            // no reason for its tests to be.
            kotlin.srcDir("src/joltJniTest/kotlin")
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
        named("desktopTest") {
            kotlin.srcDir("src/joltJniTest/kotlin")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // createJoltPhysicsWorld is suspend for wasmJs's Emscripten bootstrap, so every test
            // that builds a real world needs runTest.
            implementation(libs.kotlinx.coroutines.test)
        }
        // jolt-jni is a JVM-only native binding (desktop+Android) -- duplicated verbatim
        // between desktopMain/androidMain rather than a shared intermediate source set
        // (this repo's default hierarchy template doesn't group desktop+Android under one
        // JVM-shared source set the way it auto-groups js+wasmJs under `webMain`).
        named("desktopMain") {
            dependencies {
                // JVM library (Java classes) -- see libs.versions.toml's comment for why
                // MacOSX_ARM64's artifact ID is used (this dev machine's platform); the
                // classes themselves are identical across all 8 desktop platform artifact
                // IDs jolt-jni publishes.
                implementation(libs.jolt.jni.desktop)
                // Native library matching this dev machine (macOS Apple Silicon), Release
                // build + single-precision ("Sp") flavor -- see jolt-jni's own "add to an
                // existing project" doc for the Debug/Release and Sp/Dp axes.
                runtimeOnly("com.github.stephengold:jolt-jni-MacOSX_ARM64:5.2.0:ReleaseSp")
                // Extracts + loads the native library above at runtime (see JoltNative.kt).
                implementation(libs.snaploader)
                implementation(libs.oshi.core)
            }
        }
        named("androidMain") {
            dependencies {
                // Self-contained AAR (Java classes + all Android native ABIs) -- unlike
                // desktop, no separate native-library artifact or snaploader needed;
                // System.loadLibrary("joltjni") finds it via the AAR's own jniLibs layout.
                // "SpDebug", not "SpRelease" (confirmed the hard way): the published
                // 5.2.0 "SpRelease" AAR's classes.jar has been R8-shrunk down to zero
                // .class files (only its bundled Metal/Vulkan shader resources survive) --
                // presumably built as a standalone library with no consumer keep-rules, so
                // R8 treated every class as unreachable. jolt-jni's own "add to an existing
                // project" doc suggests starting with the Debug AAR regardless, so this
                // isn't a workaround so much as the documented default.
                implementation("${libs.jolt.jni.android.get()}:SpDebug@aar")
            }
        }
        // jolt-physics (jrouwe/JoltPhysics.js) -- the official Emscripten/WASM build of the
        // same jrouwe/JoltPhysics C++ engine jolt-jni (desktop/Android) and JoltC (iOS) both
        // wrap. The default entrypoint ("jolt-physics", not "jolt-physics/wasm") resolves to
        // its "wasm-compat" flavour, whose .wasm binary is base64-embedded directly in the
        // bundle -- avoids a `locateFile`/separate-.wasm-fetch dance under Kotlin/Wasm's
        // webpack-based dev server, at the cost of a larger bundle (acceptable for a physics
        // demo). Single-threaded (not "-multithread"): the multithread flavour needs
        // SharedArrayBuffer + COOP/COEP response headers, which Kotlin/Wasm's default
        // `browser()` webpack-dev-server task doesn't set.
        named("wasmJsMain") {
            dependencies {
                implementation(npm("jolt-physics", "1.1.0"))
                // Promise<JsAny>.await() -- bridges jolt-physics's async Emscripten module
                // bootstrap into JoltPhysicsWorld.create()'s suspend factory.
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

// jolt-jni publishes device ABIs in its Android artifact and nothing for a JVM host, so
// `System.loadLibrary("joltjni")` has nothing to load here: every test in this module's
// `commonTest` fails with UnsatisfiedLinkError if run as an Android *host* test. The suite that
// covers this target is `connectedAndroidDeviceTest`, which runs the same sources on a device.
// Registered by AGP after this script runs, so matched lazily rather than looked up by name.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach { enabled = false }
