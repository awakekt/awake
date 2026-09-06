/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.files

import com.awakekt.awake.editor.panels.files.EditorFileEntry
import com.awakekt.awake.studio.ui.STUDIO_FILES
import com.awakekt.awake.studio.ui.StudioFileContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioFilesTest {

    @Test
    fun inMemoryFileOpsScansDirectoryAndReadsFiles() {
        val fileOps = InMemoryStudioFileOps(
            files = mutableMapOf(
                "/project/scenes/main.scene.json" to "{}".encodeToByteArray(),
                "/project/models/hero.glb" to byteArrayOf(1, 2, 3),
                "/outside/other.txt" to "other".encodeToByteArray(),
            ),
        )

        val scanned = fileOps.scanDirectory("/project")
        assertEquals(2, scanned.size)
        assertTrue(scanned.any { it.name == "main.scene.json" })
        assertTrue(scanned.any { it.name == "hero.glb" })

        val bytes = fileOps.readFileBytes("/project/scenes/main.scene.json")
        assertNotNull(bytes)
        assertEquals("{}", bytes.decodeToString())

        fileOps.writeText("/project/docs/readme.md", "# Readme")
        assertEquals("# Readme", fileOps.readFileBytes("/project/docs/readme.md")?.decodeToString())
    }

    @Test
    fun studioFileContentsIntegratesScannedEntriesWithBundledFiles() {
        val fileOps = InMemoryStudioFileOps(
            files = mutableMapOf(
                "/my-game/scene.json" to """{"version":1}""".encodeToByteArray(),
            ),
        )
        val contents = StudioFileContents(fileOps = fileOps)

        assertTrue(contents.allEntries.containsAll(STUDIO_FILES))

        val scanned = contents.scanProjectDirectory("/my-game")
        assertEquals(1, scanned.size)
        assertEquals(STUDIO_FILES.size + 1, contents.allEntries.size)

        val entry = scanned.first()
        val read = contents[entry]
        assertNotNull(read)
        assertEquals("""{"version":1}""", read.decodeToString())

        contents.writeText("/my-game/saved.json", "{}")
        val savedEntry = EditorFileEntry(path = "/my-game/saved.json", name = "saved.json")
        assertEquals("{}", contents[savedEntry]?.decodeToString())
    }
}
