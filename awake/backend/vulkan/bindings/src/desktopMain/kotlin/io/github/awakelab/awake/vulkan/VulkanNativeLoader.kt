/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal object VulkanNativeLoader {
    @Volatile
    private var isLoaded = false

    @Synchronized
    fun load() {
        if (isLoaded) return

        // 1. Try standard java.library.path first (developer override / pre-configured environment)
        try {
            System.loadLibrary("awake-vulkan")
            isLoaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to embedded extraction
        }

        val platformDir = platformDirectory()
        val libFileName = libraryFileName()
        val resourcePath = "/natives/$platformDir/$libFileName"

        val bytes = VulkanNativeLoader::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            ?: throw IllegalStateException(
                "Native Vulkan library 'awake-vulkan' not found for platform '$platformDir'.\n" +
                    "Embedded resource '$resourcePath' is not available in the classpath and the library " +
                    "was not found on java.library.path.\n" +
                    "To build the host native library locally, run:\n" +
                    "  ./gradlew :awake:backend:vulkan:bindings:buildDesktopNative"
            )

        try {
            val targetFile = cacheFileFor(bytes, platformDir, libFileName)
            targetFile.parentFile?.mkdirs()
            // Only the exact bytes this build ships can occupy this path, so an existing file of
            // the right length is this library and nothing else.
            if (targetFile.length() != bytes.size.toLong()) {
                FileOutputStream(targetFile).use { it.write(bytes) }
            }
            System.load(targetFile.absolutePath)
            isLoaded = true
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to extract and load native library '$libFileName' from '$resourcePath'",
                e,
            )
        }
    }

    /**
     * Where a given build of the library is cached.
     *
     * The content hash is in the path, which is the whole point. Keying only on the file name meant
     * a cached library from an older version was reused forever: it existed, so nothing re-extracted
     * it, `System.load` accepted it, and the mismatch only surfaced as `UnsatisfiedLinkError` on the
     * first native call -- once the symbols were renamed, and silently running stale native code
     * before that. Two builds now simply cannot collide, so an upgrade cannot be shadowed by the
     * version a consumer happened to run first.
     */
    internal fun cacheFileFor(bytes: ByteArray, platformDir: String, libFileName: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val fingerprint = digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
        val userHome = System.getProperty("user.home")
        val base = if (userHome != null && File(userHome).isDirectory) {
            File(userHome, ".awake/natives")
        } else {
            File(System.getProperty("java.io.tmpdir", "."), "awake-natives")
        }
        return File(File(base, platformDir), fingerprint).resolve(libFileName)
    }

    /**
     * The `natives/<here>/` directory this host's library ships in.
     *
     * Composed from the two axes rather than enumerated pair by pair: the combinations are a
     * product, and spelling them out cost more branches than the rule has. A pair with no shipped
     * library still names itself -- the resource lookup fails either way, and "windows-arm64" says
     * more about what is missing than "unknown-aarch64" does.
     */
    internal fun platformDirectory(
        osName: String = System.getProperty("os.name", ""),
        osArch: String = System.getProperty("os.arch", ""),
    ): String {
        val os = osFamily(osName)
        val arch = archFamily(osArch)
        return if (os == null || arch == null) "unknown-${osArch.lowercase()}" else "$os-$arch"
    }

    private fun osFamily(osName: String): String? {
        val os = osName.lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("linux") || os.contains("unix") -> "linux"
            os.contains("windows") -> "windows"
            else -> null
        }
    }

    private fun archFamily(osArch: String): String? {
        val arch = osArch.lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64") -> "x86_64"
            else -> null
        }
    }

    internal fun libraryFileName(osName: String = System.getProperty("os.name", "")): String {
        val os = osName.lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> "libawake-vulkan.dylib"
            os.contains("windows") -> "awake-vulkan.dll"
            else -> "libawake-vulkan.so"
        }
    }

    /** Enough of the digest that a collision is not a practical concern, short enough to read. */
    private const val FINGERPRINT_BYTES = 8
}
