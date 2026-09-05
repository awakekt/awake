/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal object NagaNativeLoader {
    @Volatile
    private var isLoaded = false

    @Synchronized
    fun load() {
        if (isLoaded) return

        // 1. Explicit developer override via system property
        val explicit = System.getProperty("awake.naga.library")
        if (explicit != null) {
            val file = File(explicit)
            if (file.exists()) {
                System.load(file.absolutePath)
                isLoaded = true
                return
            }
        }

        // 2. Try standard java.library.path
        try {
            System.loadLibrary("awake_naga")
            isLoaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to embedded classpath extraction
        }

        val platformDir = platformDirectory()
        val libFileName = libraryFileName()
        val resourcePath = "/natives/$platformDir/$libFileName"

        val bytes = NagaNativeLoader::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }

        if (bytes != null) {
            try {
                val targetFile = cacheFileFor(bytes, platformDir, libFileName)
                targetFile.parentFile?.mkdirs()
                if (targetFile.length() != bytes.size.toLong()) {
                    FileOutputStream(targetFile).use { it.write(bytes) }
                }
                System.load(targetFile.absolutePath)
                isLoaded = true
                return
            } catch (e: Exception) {
                System.err.println("Warning: Failed to extract embedded native library from $resourcePath: ${e.message}")
            }
        }

        // 3. Fallback: check cached user home ~/.awake/natives
        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            val fallback = File(userHome, ".awake/natives/$platformDir/$libFileName")
            if (fallback.exists()) {
                System.load(fallback.absolutePath)
                isLoaded = true
                return
            }
        }

        throw IllegalStateException(
            "Native Naga library 'awake_naga' not found for platform '$platformDir'.\n" +
                "Embedded resource '$resourcePath' is not available in the classpath and the library " +
                "was not found on java.library.path or in ~/.awake/natives/$platformDir/.\n" +
                "To build the host native library locally, run:\n" +
                "  ./gradlew :awake:asset:shader-compiler:buildNagaDesktop",
        )
    }

    private fun cacheFileFor(bytes: ByteArray, platformDir: String, libFileName: String): File {
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
            os.contains("mac") || os.contains("darwin") -> "libawake_naga.dylib"
            os.contains("windows") -> "awake_naga.dll"
            else -> "libawake_naga.so"
        }
    }

    private const val FINGERPRINT_BYTES = 8
}
