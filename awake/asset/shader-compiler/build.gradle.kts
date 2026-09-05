/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Runtime WGSL->SPIR-V via naga-as-a-library: the Rust crate in rust-native/ exports JNI
// symbols (desktop/Android) and a C ABI (iOS cinterop). Native builds are manual/on-demand
// cargo tasks, same policy as jolt's buildJoltC* -- they only change when the Rust shim or
// its pinned naga version does. wasmJs compiles a throwing actual: browsers take WGSL
// directly.
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

val rustDir = layout.projectDirectory.dir("rust-native")
val cargoBin = providers.systemProperty("user.home").map { "$it/.cargo/bin/cargo" }

// Inputs/outputs declared so Gradle itself can skip re-invoking cargo when nothing changed --
// without them every task here is an unconditional Exec, same gap NativeCMake.kt had for the
// Vulkan bindings. Cargo's own target/ cache already makes a warm rebuild a near-instant no-op
// (see the desktopTest comment below); this just lets Gradle skip the process spawn entirely.
fun registerCargoTask(
    name: String,
    description: String,
    vararg cargoArgs: String,
    configureOutputs: org.gradle.api.tasks.TaskOutputs.() -> Unit,
) = tasks.register<Exec>(name) {
    group = "generation"
    this.description = description
    workingDir = rustDir.asFile
    // PATH must carry ~/.cargo/bin for rustup's toolchain shims (and cargo-ndk).
    environment("PATH", "${System.getProperty("user.home")}/.cargo/bin:" + System.getenv("PATH"))
    commandLine(cargoBin.get(), *cargoArgs)
    inputs.dir(rustDir.dir("src")).withPropertyName("rustSource").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rustDir.file("Cargo.toml")).withPropertyName("cargoManifest")
    inputs.file(rustDir.file("Cargo.lock")).withPropertyName("cargoLock")
    outputs.configureOutputs()
}

val buildNagaDesktop = registerCargoTask(
    "buildNagaDesktop",
    "Build libawake_naga for this desktop host (cargo, release)",
    "build", "--release",
) {
    file(rustDir.file("target/release/${HostOs.libraryFileName("awake_naga")}"))
}

buildNagaDesktop.configure {
    doLast {
        val platformTag = when {
            HostOs.isMac && (System.getProperty("os.arch").lowercase().contains("arm64") || System.getProperty("os.arch").lowercase().contains("aarch64")) -> "macos-arm64"
            HostOs.isMac -> "macos-x86_64"
            HostOs.isLinux && (System.getProperty("os.arch").lowercase().contains("arm64") || System.getProperty("os.arch").lowercase().contains("aarch64")) -> "linux-arm64"
            HostOs.isLinux -> "linux-x86_64"
            HostOs.isWindows -> "windows-x86_64"
            else -> HostOs.slug
        }
        val libName = HostOs.libraryFileName("awake_naga")
        val built = rustDir.file("target/release/$libName").asFile
        if (built.exists()) {
            val nativesResDir = layout.buildDirectory.dir("generated/natives-resources/natives/$platformTag").get().asFile.also { it.mkdirs() }
            built.copyTo(File(nativesResDir, built.name), overwrite = true)
            println("Desktop Naga native library packaged for resources: ${File(nativesResDir, built.name)}")
        }
    }
}

// cargo-ndk writes build/naga-jniLibs/<abi>/libawake_naga.so; the androidMain jniLibs
// convention directory is a symlink-free copy so the AAR packs it.
registerCargoTask(
    "buildNagaAndroid",
    "Build libawake_naga.so for Android ABIs (cargo-ndk, release)",
    "ndk", "-t", "arm64-v8a", "-t", "x86_64",
    "-o", layout.projectDirectory.dir("src/androidMain/jniLibs").asFile.path,
    "build", "--release",
) {
    dir(layout.projectDirectory.dir("src/androidMain/jniLibs"))
}

registerCargoTask(
    "buildNagaIosArm64",
    "Build libawake_naga.a for iOS devices (cargo, release)",
    "build", "--release", "--target", "aarch64-apple-ios",
) {
    file(rustDir.file("target/aarch64-apple-ios/release/libawake_naga.a"))
}

registerCargoTask(
    "buildNagaIosSimulatorArm64",
    "Build libawake_naga.a for the Apple-Silicon iOS simulator (cargo, release)",
    "build", "--release", "--target", "aarch64-apple-ios-sim",
) {
    file(rustDir.file("target/aarch64-apple-ios-sim/release/libawake_naga.a"))
}

kotlin {
    // expect object NagaShaderCompiler -- the classes flavor of expect/actual is Beta; the
    // flag is the documented opt-in (KT-61573), same shape jolt's typealias actuals rely on.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "io.github.awakelab.awake.asset.shadercompiler"
    }

    // Same generated-def cinterop shape as jolt: linker flags are build-directory paths known
    // at configuration time, and a downstream framework link needs them in the .def, not in
    // this module's own binaries block.
    val cargoTargetByKotlinTarget = mapOf(
        "iosArm64" to "aarch64-apple-ios",
        "iosSimulatorArm64" to "aarch64-apple-ios-sim",
    )
    cargoTargetByKotlinTarget.forEach { (targetName, cargoTarget) ->
        val target = targets.getByName(targetName) as KotlinNativeTarget
        registerGeneratedDefCinterop(
            target = target,
            interopName = "awake_naga",
            baseDefFile = layout.projectDirectory.file("src/nativeInterop/cinterop/awake_naga.def").asFile,
            headerDirs = listOf(rustDir.dir("include").asFile),
            linkerOpts = listOf("-L${rustDir.dir("target/$cargoTarget/release").asFile.path}", "-lawake_naga"),
        )
    }

    sourceSets {
        named("desktopMain") {
            resources.srcDir(layout.buildDirectory.dir("generated/natives-resources"))
            (findProperty("awake.prebuiltNatives") as String?)
                ?.takeIf { it.isNotBlank() }
                ?.let { resources.srcDir(it) }
        }
        commonTest.dependencies {
            // The pack's ASL definitions are the naga binding's real workload; compiling the
            // terrain shader here is what proves vertex-stage texture sampling validates at all.
            // Acyclic: shader-pack does not depend on this module.
            implementation(project(":awake:asset:shader-pack"))
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Shader Compiler")
        description.set("Runtime WGSL-to-SPIR-V compilation for Awake via naga as a library")
    }
}

// The JNI library must exist before the JVM tests construct NagaShaderCompiler; cargo is a
// cheap no-op rebuild once warm.
tasks.named<Test>("desktopTest") {
    dependsOn(buildNagaDesktop)
    systemProperty(
        "awake.naga.library",
        rustDir.dir("target/release").asFile.resolve(HostOs.libraryFileName("awake_naga")).path,
    )
}

tasks.named("desktopProcessResources") {
    dependsOn(buildNagaDesktop)
}
