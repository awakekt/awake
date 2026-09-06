/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.fixture

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioSceneRegistryTest {
    @BeforeTest
    fun setUp() {
        StudioSceneRegistry.resetToDefaults()
    }

    @AfterTest
    fun tearDown() {
        StudioSceneRegistry.resetToDefaults()
    }

    @Test
    fun `defaults include standard demo scenes`() {
        try {
            StudioSceneRegistry.resetToDefaults()
            val all = StudioSceneRegistry.all
            assertEquals(2, all.size)
            assertEquals("rotating-cube", all[0].id)
            assertEquals("cesium-man", all[1].id)
        } finally {
            StudioSceneRegistry.resetToDefaults()
        }
    }

    @Test
    fun `register adds and replaces scenes dynamically`() {
        try {
            val custom = StudioSceneDescriptor(
                id = "dungeon-1",
                title = "Dungeon Level 1",
                path = "assets/scenes/dungeon1.scene.json",
            )
            StudioSceneRegistry.register(custom)
            assertEquals(3, StudioSceneRegistry.all.size)
            assertEquals(custom, StudioSceneRegistry.findById("dungeon-1"))
            assertEquals(custom, StudioSceneRegistry.findByPath("assets/scenes/dungeon1.scene.json"))

            val updated = custom.copy(title = "Dungeon Remastered")
            StudioSceneRegistry.register(updated)
            assertEquals(3, StudioSceneRegistry.all.size)
            assertEquals("Dungeon Remastered", StudioSceneRegistry.findById("dungeon-1")?.title)
        } finally {
            StudioSceneRegistry.resetToDefaults()
        }
    }

    @Test
    fun `clear removes all scenes and allows standalone custom list`() {
        try {
            StudioSceneRegistry.clear()
            assertTrue(StudioSceneRegistry.all.isEmpty())

            val custom = StudioSceneDescriptor(
                id = "standalone",
                title = "Standalone Game Scene",
                path = "scenes/main.scene.json",
            )
            StudioSceneRegistry.register(custom)
            assertEquals(1, StudioSceneRegistry.all.size)
            assertEquals("standalone", StudioSceneRegistry.defaultScene.id)
        } finally {
            StudioSceneRegistry.resetToDefaults()
        }
    }
}
