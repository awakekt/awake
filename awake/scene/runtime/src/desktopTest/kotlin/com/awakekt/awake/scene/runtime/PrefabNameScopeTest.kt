/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ai.behavior.chase.SceneChase
import com.awakekt.awake.ai.behavior.registerAiBehaviors
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefabLink
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefabNameScopeTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    /**
     * The same prefab placed twice, as `withPrefabs` expands it: each instance's `chase` finds its own
     * node, the document's its own.
     */
    @Test
    fun aLinkResolvesInsideItsOwnPrefabInstanceFirst() {
        val fire = SceneNode(
            name = "fire",
            children = listOf(SceneNode(name = "flame"), SceneNode(name = "smoke", components = listOf(SceneChase(target = "flame")))),
        )
        fun camp(name: String) =
            SceneNode(name = name, components = listOf(ScenePrefabLink("fx/fire.prefab.json")), children = listOf(fire))
        val document = SceneDocument(
            nodes = listOf(
                camp("camp 0"),
                camp("camp 1"),
                SceneNode(name = "flame"),
                SceneNode(name = "watcher", components = listOf(SceneChase(target = "flame"))),
            ),
        )
        val world = World()

        document.instantiate(world, SceneComponentRegistry().registerAiBehaviors())

        val followers = buildMap {
            world.queryEach(ChaseBehavior::class) { entity, chase -> put(world.get<Name>(entity)!!.value + " of " + ancestorName(world, entity), chase.target!!) }
        }
        val smoke0 = followers.getValue("smoke of camp 0")
        val smoke1 = followers.getValue("smoke of camp 1")
        assertEquals("camp 0", ancestorName(world, smoke0), "camp 0's smoke follows camp 0's flame")
        assertEquals("camp 1", ancestorName(world, smoke1), "camp 1's smoke follows camp 1's flame")
        assertEquals(null, ancestorName(world, followers.getValue("watcher of null")), "the document's watcher follows the document's flame")
    }

    /** The name of [entity]'s outermost ancestor, the camp it was placed under; null at the top level. */
    private fun ancestorName(world: World, entity: Entity): String? {
        var current = entity
        var outermost: Entity? = null
        while (true) {
            val parent = world.get<Transform>(current)?.parent ?: break
            outermost = parent
            current = parent
        }
        return outermost?.let { world.get<Name>(it)?.value }
    }
}
