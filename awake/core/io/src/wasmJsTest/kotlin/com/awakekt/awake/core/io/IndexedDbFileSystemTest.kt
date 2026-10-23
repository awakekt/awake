/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndexedDbFileSystemTest {
    @Test
    fun projectFilesAndMetadataSurviveANewFileSystemInstance() = runTest {
        val root = "metadata-round-trip-${Random.nextLong()}"
        val first = createPlatformFileSystem(root)
        val path = FilePath.of("scenes/harbor.scene.json")
        val bytes = "{\"name\":\"Harbor Town\"}".encodeToByteArray()

        assertTrue(first.list(recursive = true).getOrThrow().isEmpty())
        first.write(path, bytes).getOrThrow()
        val metadata = assertNotNull(first.stat(path).getOrThrow())

        val reopened = createPlatformFileSystem(root)
        assertEquals(metadata, reopened.stat(path).getOrThrow())
        assertEquals(bytes.size.toLong(), metadata.sizeBytes)
        assertContentEquals(bytes, reopened.read(path).getOrThrow())
        assertEquals(
            listOf("scenes", "scenes/harbor.scene.json"),
            reopened.list(recursive = true).getOrThrow().map { it.path.value }.sorted(),
        )

        reopened.delete(FilePath.of("scenes"), recursive = true).getOrThrow()
        assertTrue(createPlatformFileSystem(root).list(recursive = true).getOrThrow().isEmpty())
    }
}
