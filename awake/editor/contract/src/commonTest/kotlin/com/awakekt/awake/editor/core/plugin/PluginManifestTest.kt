/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginManifestTest {
    @Test
    fun serializesAndDeserializesCanonicalManifest() {
        val manifest = PluginManifest(
            id = "com.awakekt.plugin.terrain",
            name = "Terrain Tools",
            version = "1.2.0",
            author = "Awake",
            description = "Heightmap sculpting and painting",
            entrypointClass = "com.awakekt.plugin.terrain.TerrainPlugin",
            requiredApiVersion = 1,
            minEngineVersion = "0.1.0-beta.1",
            supportedPlatforms = listOf("desktop", "wasmJs"),
            targetJvmVersion = 17,
            dependencies = listOf(
                PluginDependency(id = "com.awakekt.plugin.core", version = "1.0.0"),
                PluginDependency(id = "com.awakekt.plugin.extra", optional = true),
            ),
            isPro = true,
            requiredLicense = "worldstream",
            category = "World",
            tags = listOf("terrain", "brush"),
            documentationUrl = "https://docs.awakekt.com/plugins/terrain",
            contributesDockTab = true,
            dockTabTitle = "Terrain Editor",
        )

        val json = manifest.toJson()
        val parsed = PluginManifest.fromJson(json)

        assertEquals(manifest, parsed)
        assertEquals(2, parsed.dependencies.size)
        assertEquals("com.awakekt.plugin.core", parsed.dependencies[0].id)
        assertFalse(parsed.dependencies[0].optional)
        assertTrue(parsed.dependencies[1].optional)
    }

    @Test
    fun rejectsBlankIdentityFields() {
        assertFailsWith<IllegalArgumentException> {
            PluginManifest(id = "", name = "Test", version = "1.0.0")
        }
        assertFailsWith<IllegalArgumentException> {
            PluginManifest(id = "test", name = "", version = "1.0.0")
        }
        assertFailsWith<IllegalArgumentException> {
            PluginManifest(id = "test", name = "Test", version = "")
        }
    }

    @Test
    fun rejectsBlankDependencyId() {
        assertFailsWith<IllegalArgumentException> {
            PluginDependency(id = "")
        }
    }
}
