/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilePathTest {
    @Test
    fun normalizesSeparatorsAndDotSegments() {
        assertEquals("assets/models/cube.glb", FilePath.of("assets\\models/./cube.glb").value)
    }

    @Test
    fun rejectsAbsoluteAndTraversalPaths() {
        assertFailsWith<IllegalArgumentException> { FilePath.of("/tmp/file") }
        assertFailsWith<IllegalArgumentException> { FilePath.of("assets/../secret") }
        assertFailsWith<IllegalArgumentException> { FilePath.of("C:/tmp/file") }
    }

    @Test
    fun resolvesRelativeAssetReferences() {
        assertEquals("assets/textures/albedo.png", AssetPath("assets/models/scene.gltf").resolve("../textures/albedo.png").value)
    }
}
