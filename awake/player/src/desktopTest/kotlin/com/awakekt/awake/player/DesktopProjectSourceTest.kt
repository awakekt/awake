/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProjectSourceTest {
    @Test
    fun readsFilesWithinProjectRootAndReportsExistence() = runTest {
        val root = Files.createTempDirectory("awake-player-dir-test").toFile()
        try {
            val scenesDir = root.resolve("scenes").apply { mkdirs() }
            scenesDir.resolve("main.scene.json").writeText("""{"version":1,"name":"main","nodes":[]}""")

            val source = directoryAssetSource(root)
            assertTrue(source.exists(AssetPath("scenes/main.scene.json")))
            assertFalse(source.exists(AssetPath("scenes/missing.scene.json")))
            val bytes = source.read(AssetPath("scenes/main.scene.json")).getOrThrow()
            assertEquals("""{"version":1,"name":"main","nodes":[]}""", bytes.decodeToString())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingRootDirectory() {
        val missing = Files.createTempDirectory("awake-player-missing").toFile()
        missing.delete()
        assertFailsWith<IllegalArgumentException> {
            directoryAssetSource(missing)
        }
    }
}
