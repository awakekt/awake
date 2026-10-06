/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.asset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetConverterRegistryTest {
    @Test
    fun registersAndFindsConverterByExtension() {
        val registry = AssetConverterRegistry()
        val dummyConverter = object : AssetConverter {
            override val supportedExtensions = setOf("o3d", ".custom")

            override fun convertToGltf(fileName: String, sourceBytes: ByteArray): GltfConversionResult = GltfConversionResult(glbBytes = byteArrayOf(1, 2, 3), name = fileName)
        }

        registry.register(dummyConverter)

        assertTrue(registry.canConvert("o3d"))
        assertTrue(registry.canConvert("O3D"))
        assertTrue(registry.canConvert(".custom"))
        assertTrue(registry.canConvert("custom"))
        assertFalse(registry.canConvert("png"))

        assertNotNull(registry.findConverter("o3d"))
        assertNull(registry.findConverter("png"))

        val result = registry.convert("test.o3d", byteArrayOf(9, 9))
        assertNotNull(result)
        assertEquals("test.o3d", result.name)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(result.glbBytes))
    }

    @Test
    fun unregisteringAConverterRestoresTheOneItReplaced() {
        val registry = AssetConverterRegistry()
        val builtIn = converter("fbx")
        val plugin = converter("fbx", "o3d")
        registry.register(builtIn)
        registry.register(plugin)
        assertEquals(plugin, registry.findConverter("fbx"), "the newest converter wins")

        registry.unregister(plugin)

        assertEquals(builtIn, registry.findConverter("fbx"), "the overridden converter comes back")
        assertFalse(registry.canConvert("o3d"), "an extension only the plugin handled is gone")
        assertEquals(listOf(builtIn), registry.converters)
    }

    @Test
    fun registeringAConverterAgainDoesNotStackIt() {
        val registry = AssetConverterRegistry()
        val plugin = converter("fbx")
        registry.register(plugin)
        registry.register(plugin)

        registry.unregister(plugin)

        assertFalse(registry.canConvert("fbx"))
    }

    private fun converter(vararg extensions: String) = object : AssetConverter {
        override val supportedExtensions = extensions.toSet()
        override fun convertToGltf(fileName: String, sourceBytes: ByteArray) =
            GltfConversionResult(glbBytes = byteArrayOf(), name = fileName)
    }
}
