/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The extracted-library cache must not be able to serve one build's library to another.
 *
 * It could. The cache path was `~/.awake/natives/<platform>/<libname>`, and extraction was skipped
 * whenever a file already sat there. A consumer who ran any earlier version kept that version's
 * library forever: `System.load` accepted it, so nothing failed at load time, and the mismatch only
 * appeared as `UnsatisfiedLinkError` on the first native call -- and only once the symbols had been
 * renamed. Before that it silently ran the old native code against new Kotlin.
 *
 * Found by publishing to mavenLocal and consuming it from a project outside the build, which is the
 * only arrangement where the cache is real. Every in-repo test supplies `java.library.path` and
 * never reaches this path at all.
 */
class VulkanNativeLoaderCacheTest {

    private val platform = "macos-arm64"
    private val libName = "libawake-vulkan.dylib"

    @Test
    fun twoDifferentLibrariesNeverShareACachePath() {
        val one = VulkanNativeLoader.cacheFileFor("build one".encodeToByteArray(), platform, libName)
        val two = VulkanNativeLoader.cacheFileFor("build two".encodeToByteArray(), platform, libName)

        assertNotEquals(
            one.absolutePath,
            two.absolutePath,
            "different library bytes must not resolve to the same cached file",
        )
    }

    @Test
    fun theSameLibraryAlwaysResolvesToTheSamePath() {
        // Otherwise every start-up would re-extract, and the cache would buy nothing.
        val bytes = "identical".encodeToByteArray()

        assertEquals(
            VulkanNativeLoader.cacheFileFor(bytes, platform, libName).absolutePath,
            VulkanNativeLoader.cacheFileFor(bytes, platform, libName).absolutePath,
        )
    }

    @Test
    fun theCachedFileKeepsThePlatformAndTheLibrarysOwnName() {
        val path = VulkanNativeLoader.cacheFileFor("x".encodeToByteArray(), platform, libName)

        assertEquals(libName, path.name, "the loaded file must still be named like a library")
        assertTrue(
            path.parentFile.parentFile.name == platform,
            "the platform must stay in the path, was ${path.absolutePath}",
        )
    }

    @Test
    fun platformAndLibraryNameFollowTheHostOs() {
        assertEquals("macos-arm64", VulkanNativeLoader.platformDirectory("Mac OS X", "aarch64"))
        assertEquals("linux-x86_64", VulkanNativeLoader.platformDirectory("Linux", "amd64"))
        assertEquals("windows-x86_64", VulkanNativeLoader.platformDirectory("Windows 11", "amd64"))

        assertEquals("libawake-vulkan.dylib", VulkanNativeLoader.libraryFileName("Mac OS X"))
        assertEquals("awake-vulkan.dll", VulkanNativeLoader.libraryFileName("Windows 11"))
        assertEquals("libawake-vulkan.so", VulkanNativeLoader.libraryFileName("Linux"))
    }
}
