/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A build reads its bundle key from a file or the environment, in raw or hex form, and never prints it. */
class BundleKeyFilesTest {
    private val folder: File = createTempDirectory("awake-key").toFile()
    private val key = ByteArray(32) { (it * 13 + 5).toByte() }
    private val hex = key.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun aKeyFileHoldsRawBytesOrHexDigits() {
        val raw = folder.resolve("raw.key").apply { writeBytes(key) }
        val text = folder.resolve("hex.key").apply { writeText("$hex\n") }

        assertEquals(key.toList(), BundleKeyFiles.read("k", raw).bytes.toList())
        assertEquals(key.toList(), BundleKeyFiles.read("k", text).bytes.toList())
        assertEquals("k", BundleKeyFiles.read("k", text).keyId)
    }

    @Test
    fun aKeyFileOfAnotherSizeOrMissingFailsWithoutShowingIt() {
        val short = folder.resolve("short.key").apply { writeText(hex.dropLast(2)) }

        val wrong = assertFailsWith<IllegalArgumentException> { BundleKeyFiles.read("k", short) }
        val missing = assertFailsWith<IllegalArgumentException> { BundleKeyFiles.read("k", folder.resolve("none.key")) }

        assertTrue(wrong.message.orEmpty().contains("64 hex digits"), "$wrong")
        assertFalse(wrong.message.orEmpty().contains(hex.dropLast(2)))
        assertTrue(missing.message.orEmpty().contains("doesn't exist"), "$missing")
    }

    @Test
    fun anEnvironmentVariableHoldsHexDigits() {
        val environment = mapOf("BUNDLE_KEY" to " $hex ", "BAD_KEY" to "not-a-key")

        assertEquals(key.toList(), BundleKeyFiles.fromEnvironment("k", "BUNDLE_KEY", environment::get).bytes.toList())
        val bad = assertFailsWith<IllegalArgumentException> { BundleKeyFiles.fromEnvironment("k", "BAD_KEY", environment::get) }
        val unset = assertFailsWith<IllegalArgumentException> { BundleKeyFiles.fromEnvironment("k", "NO_KEY", environment::get) }

        assertFalse(bad.message.orEmpty().contains("not-a-key"), "$bad")
        assertTrue(unset.message.orEmpty().contains("NO_KEY") && unset.message.orEmpty().contains("isn't set"), "$unset")
    }

    @Test
    fun theGradleWorkerTakesExactlyOneKeySource() {
        val file = folder.resolve("bundle.key").apply { writeBytes(key) }

        assertFailsWith<IllegalArgumentException> { bundleKey("k", file, "BUNDLE_KEY") }
        assertFailsWith<IllegalArgumentException> { bundleKey("k", null, null) }
        assertEquals("k", bundleKey("k", file, null).keyId)
    }
}
