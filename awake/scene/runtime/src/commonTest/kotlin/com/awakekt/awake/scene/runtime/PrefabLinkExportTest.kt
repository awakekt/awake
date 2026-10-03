/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.PrefabLink
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.document.withPrefabs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefabLinkExportTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    /** The prefab's entities load under the link; saving writes the link alone, not a copy of the prefab. */
    @Test
    fun aLinkedPrefabLoadsItsEntitiesAndExportsAsItsLink() = runTest {
        val fire = ScenePrefab(guid = "fire", root = SceneNode(name = "fire", children = listOf(SceneNode(name = "flame"))))
        val authored = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "camp fire",
                    transform = SceneTransform(position = SceneVec3(3f, 0f, 0f)),
                    components = listOf(ScenePrefabLink("fx/fire.prefab.json")),
                ),
            ),
        )
        val world = World()

        authored.withPrefabs { fire.toJson() }.instantiate(world = world)

        val names = mutableListOf<String>().also { list -> world.queryEach(Name::class) { _, name -> list += name.value } }
        assertEquals(setOf("camp fire", "fire", "flame"), names.toSet())
        val links = mutableListOf<PrefabLink>().also { list -> world.queryEach(PrefabLink::class) { _, link -> list += link } }
        assertEquals(listOf(PrefabLink("fx/fire.prefab.json")), links)
        assertEquals(authored.nodes, SceneLoader.fromWorld(world).nodes)
    }
}
