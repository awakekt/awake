/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * The machine Gradle is running on, named the way Kotlin names it.
 *
 * Backed by [HostManager], which is the Kotlin Gradle Plugin's own host detection -- the same
 * logic it uses to decide which native targets this machine can build. Two reasons to prefer it
 * over reading `os.name`:
 *
 * - **It is arch-aware.** [slug] is `macos_arm64`, not `Mac OS X`; identifying a host from
 *   `os.name` alone silently ignores whether it is arm64 or x86_64, which matters the moment a
 *   prebuilt native library is involved.
 * - **It is one spelling.** This replaced five call sites with three:
 *   `os.name.lowercase().contains("mac")` in three build files, `os.name.startsWith("Mac")` in a
 *   fourth, and a bare `os.name.lowercase()` in a convention plugin. They agreed; nothing made
 *   them agree.
 *
 * Host, not target. A Kotlin *target* is already named canonically by the Kotlin plugin
 * (`iosArm64`, `desktop`, `wasmJs`); this answers "what machine is compiling", which is what
 * decides tool paths, JVM flags and native library filenames.
 *
 * One caveat worth knowing: `org.jetbrains.kotlin.konan.target` carries no API-stability
 * guarantee. It is widely used from build logic and is on the classpath wherever the Kotlin
 * plugin is, but a Kotlin upgrade could move it. If that happens, everything needed to fall back
 * to `os.name`/`os.arch` is in this one file.
 */
object HostOs {

    val isMac: Boolean get() = HostManager.hostIsMac

    val isWindows: Boolean get() = HostManager.hostIsMingw

    val isLinux: Boolean get() = HostManager.hostIsLinux

    /**
     * Kotlin's canonical name for this host, architecture included -- `macos_arm64`,
     * `linux_x64`, `mingw_x64`.
     *
     * This is the naming to reach for when prebuilt natives eventually ship inside a published
     * artifact and need a per-host directory. Note it is NOT the JNA/`wgpu4k-native` spelling
     * (`darwin-aarch64`, `win32-x86-64`); if Awake ever has to match that layout, translate at
     * the packaging boundary rather than adopting a second scheme here.
     */
    val slug: String get() = HostManager.host.name

    /**
     * The filename this platform gives a native library called [base].
     *
     * `libawake-vulkan.dylib` on macOS, `libawake-vulkan.so` on Linux, `awake-vulkan.dll` on
     * Windows -- note Windows has no `lib` prefix. That is what a pattern written
     * `lib?awake-vulkan\.(dylib|so|dll)` got wrong: it reads as "li" plus an optional "b", so it
     * missed the Windows name entirely while matching `liawake-vulkan.so`. An exact name is both
     * correct and stricter than any pattern.
     */
    fun libraryFileName(base: String): String = when {
        isWindows -> "$base.dll"
        isMac -> "lib$base.dylib"
        else -> "lib$base.so"
    }
}
