/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NativeLibraryTest {

    private val platform = "macos-arm64"
    private val fileName = "libawake-window.dylib"

    @Test
    fun twoDifferentBuildsNeverShareACachePath() {
        val one = NativeLibrary.cacheFileFor("build one".encodeToByteArray(), platform, fileName)
        val two = NativeLibrary.cacheFileFor("build two".encodeToByteArray(), platform, fileName)

        assertNotEquals(one.absolutePath, two.absolutePath, "different library bytes shared a cached file")
    }

    @Test
    fun theSameBuildAlwaysResolvesToTheSamePath() {
        // Otherwise every start-up would re-extract and the cache would buy nothing.
        val bytes = "identical".encodeToByteArray()

        assertEquals(
            NativeLibrary.cacheFileFor(bytes, platform, fileName).absolutePath,
            NativeLibrary.cacheFileFor(bytes, platform, fileName).absolutePath,
        )
    }

    @Test
    fun theCachedFileKeepsThePlatformAndTheLibraryName() {
        val path = NativeLibrary.cacheFileFor("x".encodeToByteArray(), platform, fileName)

        assertEquals(fileName, path.name)
        assertTrue(path.parentFile.parentFile.name == platform, "the platform left the path: ${path.absolutePath}")
    }

    @Test
    fun platformAndFileNameFollowTheHostOs() {
        assertEquals("macos-arm64", NativeLibrary.platformDirectory("Mac OS X", "aarch64"))
        assertEquals("linux-x86_64", NativeLibrary.platformDirectory("Linux", "amd64"))
        assertEquals("windows-x86_64", NativeLibrary.platformDirectory("Windows 11", "amd64"))

        assertEquals("libawake-window.dylib", NativeLibrary.libraryFileName("awake-window", "Mac OS X"))
        assertEquals("awake-window.dll", NativeLibrary.libraryFileName("awake-window", "Windows 11"))
        assertEquals("libawake-window.so", NativeLibrary.libraryFileName("awake-window", "Linux"))
    }

    @Test
    fun aMissingLibraryNamesItselfAndTheFix() {
        val error = assertFailsWith<IllegalStateException> {
            NativeLibrary.load("awake-no-such-library", NativeLibraryTest::class.java)
        }

        assertTrue("awake-no-such-library" in error.message.orEmpty())
        assertTrue("buildDesktopNative" in error.message.orEmpty())
    }
}
