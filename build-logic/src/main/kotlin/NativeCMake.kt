/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * CMake plumbing shared by every module that builds a native library.
 *
 * Three modules do -- `backend:vulkan:bindings` (desktop JNI), its `android-native` sibling, and
 * `backend:jolt` (iOS static libs) -- and each had hand-rolled the same configure/build `Exec`
 * pair, the same ccache discovery, and the same "manual, CMake is slow" rationale. Registering
 * them once means a fix reaches all of them: the missing `dependsOn` that left `desktopTest`
 * failing with `UnsatisfiedLinkError` in every fresh clone was exactly the kind of wiring that
 * gets fixed in one module and forgotten in the others.
 *
 * Deliberately not a plugin that owns the whole build. What varies between these modules is
 * real -- iOS needs a sysroot, architecture and deployment target; desktop needs none of that
 * and copies its output somewhere the JVM's `java.library.path` can see. Those stay at the call
 * site; only the identical parts move here.
 */
object NativeCMake {

    /**
     * ccache, if installed. Null means no compiler launcher is passed and the build is slower,
     * never broken.
     *
     * PATH first, then the two homebrew prefixes. Those exist because Gradle's own launch
     * environment does not reliably carry a login shell's PATH -- a macOS fallback, not the
     * primary lookup. The version this replaced checked ONLY them, which silently meant no
     * ccache anywhere except macOS.
     */
    fun findCcache(): String? =
        findOnPath("ccache")
            ?: listOf("/opt/homebrew/bin/ccache", "/usr/local/bin/ccache")
                .firstOrNull { File(it).exists() }

    /**
     * [executable] resolved against PATH, or null.
     *
     * Tries the bare name and `.exe` -- a check for `cmake` alone reports "not installed" on a
     * Windows machine where `cmake.exe` is right there.
     */
    fun findOnPath(executable: String): String? {
        val candidates = listOf(executable, "$executable.exe")
        return System.getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .flatMap { dir -> candidates.asSequence().map { File(dir, it) } }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }
}

/**
 * Registers a `configure<Name>`/`build<Name>` CMake task pair and returns the build task.
 *
 * @param name Capitalised suffix for the task names, e.g. `DesktopNative` produces
 * `configureDesktopNative` and `buildDesktopNative`.
 * @param sourceDir The directory holding `CMakeLists.txt`.
 * @param buildDir Where CMake writes its build tree.
 * @param buildType `CMAKE_BUILD_TYPE`.
 * @param defines Extra `-D` arguments, without the `-D` prefix -- iOS sysroot, architecture and
 * anything else only one caller needs.
 * @param buildArgs Extra arguments after `cmake --build <dir>`, e.g. `--target joltc`.
 * @param description What the build task does, for `./gradlew tasks`.
 * @return The build task, so a caller can hang a copy step or a `dependsOn` on it.
 */
@Suppress("LongParameterList")
fun Project.registerCMakeLibrary(
    name: String,
    sourceDir: File,
    buildDir: File,
    buildType: String = "Debug",
    defines: List<String> = emptyList(),
    buildArgs: List<String> = emptyList(),
    description: String,
): TaskProvider<Exec> {
    val configure = tasks.register<Exec>("configure$name") {
        group = NATIVE_GROUP
        this.description = "Configure the $name CMake build -- rerun after any C++ source change."
        doFirst { buildDir.mkdirs() }
        commandLine(
            buildList {
                add("cmake")
                add("-S"); add(sourceDir.absolutePath)
                add("-B"); add(buildDir.absolutePath)
                add("-DCMAKE_BUILD_TYPE=$buildType")
                defines.forEach { add("-D$it") }
                // ccache caches object files by source+flags hash, so a clean rebuild restores
                // instead of recompiling. It matters most for the vendored engines, where the
                // dependency is the slow part rather than our own sources.
                NativeCMake.findCcache()?.let {
                    add("-DCMAKE_C_COMPILER_LAUNCHER=$it")
                    add("-DCMAKE_CXX_COMPILER_LAUNCHER=$it")
                }
            },
        )
    }

    return tasks.register<Exec>("build$name") {
        group = NATIVE_GROUP
        this.description = description
        dependsOn(configure)
        workingDir = buildDir
        commandLine(buildList { add("cmake"); add("--build"); add(buildDir.absolutePath); addAll(buildArgs) })
    }
}

private const val NATIVE_GROUP = "native"
