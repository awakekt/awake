/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.zip.ZipFile

/**
 * The platforms a release must carry a desktop native library for. Windows is absent: no CI job
 * compiles the desktop C++ there, so promising it would be a guess.
 */
val REQUIRED_DESKTOP_NATIVE_PLATFORMS = listOf("macos-arm64", "linux-x86_64")

/**
 * Builds one desktop JNI library with CMake and ships it in this module's desktop jar as
 * `natives/<platform>/<file>`, where a runtime loader extracts it.
 *
 * A host compiles only its own library, so a release jar is assembled from per-OS CI builds:
 * `-Pawake.prebuiltNatives=<root>` adds `<root>/<libName>/natives/<platform>/` to the jar. The
 * library name is part of the path so two native modules reading the same collected root do not
 * each ship the other's library. Registers `buildDesktopNative` (also copying the library where
 * `-Djava.library.path` finds it for tests) and `verifyDesktopNatives`.
 *
 * @param libName The library's base name, e.g. `awake-window` for `libawake-window.dylib`.
 * @param sourceDir The directory holding `CMakeLists.txt`.
 * @param extraInputDirs Source directories the CMake build reads outside [sourceDir].
 */
fun Project.registerDesktopNativeLibrary(
    libName: String,
    sourceDir: File,
    extraInputDirs: List<File> = emptyList(),
): TaskProvider<Exec> {
    val buildDir = layout.buildDirectory.dir("desktop-native").get().asFile
    val libDir = layout.buildDirectory.dir("desktop-native-libs")
    val resourcesRoot = layout.buildDirectory.dir("generated/natives-resources")
    val prebuiltRoot = (findProperty("awake.prebuiltNatives") as String?)
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it, libName) }

    extensions.getByType<KotlinMultiplatformExtension>().sourceSets.named("desktopMain") {
        resources.srcDir(resourcesRoot)
        prebuiltRoot?.let { resources.srcDir(it) }
    }

    val build = registerCMakeLibrary(
        name = "DesktopNative",
        sourceDir = sourceDir,
        buildDir = buildDir,
        description = "Build lib$libName for this host and package it under natives/<platform>/.",
        extraInputDirs = extraInputDirs,
    )
    build.configure {
        doLast {
            val expected = HostOs.libraryFileName(libName)
            val built = buildDir.walkTopDown().firstOrNull { it.isFile && it.name == expected }
                ?: throw GradleException("$expected not found under $buildDir")
            built.copyTo(File(libDir.get().asFile.also { it.mkdirs() }, built.name), overwrite = true)
            val packaged = resourcesRoot.get().asFile.resolve("natives/${hostNativePlatform()}/${built.name}")
            built.copyTo(packaged.also { it.parentFile.mkdirs() }, overwrite = true)
            println("lib$libName packaged for resources: $packaged")
        }
    }

    val desktopJar = tasks.named<Jar>("desktopJar")
    tasks.register("verifyDesktopNatives") {
        group = "awake native"
        description = "Fail if the desktop jar is missing lib$libName for a supported platform."
        dependsOn(desktopJar)
        doLast {
            val archive = desktopJar.get().archiveFile.get().asFile
            val entries = ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
            val missing = REQUIRED_DESKTOP_NATIVE_PLATFORMS.filter { platform ->
                "natives/$platform/${platformLibraryFileName(platform, libName)}" !in entries
            }
            check(missing.isEmpty()) {
                "$archive is missing lib$libName for: ${missing.joinToString(", ")}. A host builds only " +
                    "its own library; a release collects them from per-OS CI runs and passes " +
                    "-Pawake.prebuiltNatives=<root>, holding <root>/$libName/natives/<platform>/."
            }
            println("$archive carries lib$libName for: ${REQUIRED_DESKTOP_NATIVE_PLATFORMS.joinToString(", ")}")
        }
    }
    return build
}

/** The `natives/<here>/` directory name for this build host, matching the runtime loader. */
fun hostNativePlatform(): String {
    val arm = System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm64" in it }
    return when {
        HostOs.isMac -> if (arm) "macos-arm64" else "macos-x86_64"
        HostOs.isLinux -> if (arm) "linux-arm64" else "linux-x86_64"
        HostOs.isWindows -> "windows-x86_64"
        else -> HostOs.slug
    }
}

private fun platformLibraryFileName(platform: String, libName: String): String = when {
    platform.startsWith("macos-") -> "lib$libName.dylib"
    platform.startsWith("windows-") -> "$libName.dll"
    else -> "lib$libName.so"
}
