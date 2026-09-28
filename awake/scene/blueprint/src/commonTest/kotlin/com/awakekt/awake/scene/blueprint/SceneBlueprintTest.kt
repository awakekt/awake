/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneBlueprintTest {
    private fun registry() = SceneComponentRegistry().registerBlueprints()

    @Test
    fun theBlueprintComponentLoadsFromSceneJsonAndExportsBack() {
        val registry = registry()
        val document = SceneLoader.decode(
            """
            {
              "nodes": [
                {
                  "name": "door",
                  "components": [
                    { "component": "blueprint", "graph": "door.graph.json", "variables": { "delay": 2.5, "locked": true } }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val scene = SceneLoader.instantiate(document, componentRegistry = registry)

        var loaded: BlueprintComponent? = null
        scene.world.queryEach(BlueprintComponent::class) { _, component -> loaded = component }
        val component = assertNotNull(loaded)
        assertEquals("door.graph.json", component.graph)
        assertEquals(mapOf("delay" to JsonPrimitive(2.5), "locked" to JsonPrimitive(true)), component.variables)

        val exported = SceneLoader.fromWorld(scene.world, componentRegistry = registry).nodes.single().components.single()
        assertEquals(document.nodes.single().components.single(), exported)
    }

    @Test
    fun aBlankGraphPathIsAValidationIssue() {
        assertTrue(SceneBlueprint(graph = " ").validate("door").isNotEmpty())
        assertTrue(SceneBlueprint(graph = "door.graph.json").validate("door").isEmpty())
    }
}
