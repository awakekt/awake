/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Loads a JNI library that a desktop jar ships as `natives/<platform>/<file>`.
 *
 * `java.library.path` wins, so a local build or a packaged app can supply its own copy. Otherwise
 * the library is extracted from the jar into a per-user cache and loaded from there.
 */
object NativeLibrary {
    private val loaded = mutableSetOf<String>()

    /**
     * Loads [name] (`awake-window` for `libawake-window.dylib`) once per process.
     *
     * [anchor] is a class from the jar that ships the library, whose class loader finds the
     * resource. Throws [IllegalStateException] with the build command when it is missing.
     */
    @Synchronized
    fun load(name: String, anchor: Class<*>) {
        if (name in loaded) return
        try {
            System.loadLibrary(name)
            loaded += name
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to the copy in the jar.
        }

        val platform = platformDirectory()
        val fileName = libraryFileName(name)
        val resourcePath = "/natives/$platform/$fileName"
        val bytes = anchor.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            ?: error(
                "Native library '$name' is not available for '$platform': '$resourcePath' is not on " +
                    "the classpath and the library is not on java.library.path. Build it for this " +
                    "host with the owning module's buildDesktopNative task.",
            )
        val target = cacheFileFor(bytes, platform, fileName)
        target.parentFile?.mkdirs()
        // Only the exact bytes this build ships can occupy this path, so a file of the right length
        // is this library and nothing else.
        if (target.length() != bytes.size.toLong()) {
            FileOutputStream(target).use { it.write(bytes) }
        }
        System.load(target.absolutePath)
        loaded += name
    }

    /**
     * Where one build of a library is cached. The content hash is in the path: keyed by name alone,
     * an older cached build was reused forever and only failed on the first renamed native call.
     */
    internal fun cacheFileFor(bytes: ByteArray, platform: String, fileName: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val fingerprint = digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
        val userHome = System.getProperty("user.home")
        val base = if (userHome != null && File(userHome).isDirectory) {
            File(userHome, ".awake/natives")
        } else {
            File(System.getProperty("java.io.tmpdir", "."), "awake-natives")
        }
        return File(File(base, platform), fingerprint).resolve(fileName)
    }

    /** The `natives/<here>/` directory for this host, e.g. `macos-arm64`. */
    internal fun platformDirectory(
        osName: String = System.getProperty("os.name", ""),
        osArch: String = System.getProperty("os.arch", ""),
    ): String {
        val os = osFamily(osName.lowercase())
        val arch = archFamily(osArch.lowercase())
        return if (os == null || arch == null) "unknown-${osArch.lowercase()}" else "$os-$arch"
    }

    private fun osFamily(os: String): String? = when {
        "mac" in os || "darwin" in os -> "macos"
        "linux" in os || "unix" in os -> "linux"
        "windows" in os -> "windows"
        else -> null
    }

    private fun archFamily(arch: String): String? = when {
        "aarch64" in arch || "arm64" in arch -> "arm64"
        "x86_64" in arch || "amd64" in arch || "x64" in arch -> "x86_64"
        else -> null
    }

    internal fun libraryFileName(name: String, osName: String = System.getProperty("os.name", "")): String {
        val os = osName.lowercase()
        return when {
            "mac" in os || "darwin" in os -> "lib$name.dylib"
            "windows" in os -> "$name.dll"
            else -> "lib$name.so"
        }
    }

    /** Enough of the digest that a collision is not a practical concern, short enough to read. */
    private const val FINGERPRINT_BYTES = 8
}
