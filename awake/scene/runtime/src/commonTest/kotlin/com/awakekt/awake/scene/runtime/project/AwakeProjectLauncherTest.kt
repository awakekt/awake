/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.project

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals

class AwakeProjectLauncherTest {

    @Test
    fun parsesStandaloneProjectConfig() {
        val json = """
            {
                "name": "MyGame",
                "defaultScene": "scenes/level1.scene.json",
                "targetFrameRate": 120,
                "physicsTickRate": 60,
                "plugins": ["com.awakekt.awake.pro.physics-ragdoll"]
            }
        """.trimIndent()

        val config = AwakeProjectLauncher.parseConfig(json)

        assertEquals("MyGame", config.name)
        assertEquals("scenes/level1.scene.json", config.defaultScene)
        assertEquals(120, config.targetFrameRate)
        assertEquals(60, config.physicsTickRate)
        assertEquals(1, config.plugins.size)
        assertEquals("com.awakekt.awake.pro.physics-ragdoll", config.plugins[0])
    }

    @Test
    fun instantiatesSceneDocumentDirectlyIntoWorld() {
        val world = World()
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(name = "Player"),
                SceneNode(name = "MainCamera"),
            ),
        )

        val scene = AwakeProjectLauncher.loadScene(world, doc)

        assertEquals(2, scene.roots.size)
    }
}
