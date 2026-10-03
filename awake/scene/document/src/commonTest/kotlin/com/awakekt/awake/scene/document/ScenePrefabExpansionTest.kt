/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScenePrefabExpansionTest {
    private val fire = ScenePrefab(guid = "fire", root = SceneNode(name = "fire", children = listOf(SceneNode(name = "flame"))))

    private fun link(name: String, path: String, x: Float = 0f) = SceneNode(
        name = name,
        transform = SceneTransform(position = SceneVec3(x, 0f, 0f)),
        components = listOf(ScenePrefabLink(path)),
    )

    /** Every link gets the prefab's root under it, placed by the link's own transform; the file is read once. */
    @Test
    fun eachLinkHoldsThePrefabAndTheFileIsReadOnce() = runTest {
        val reads = mutableListOf<String>()
        val document = SceneDocument(nodes = listOf(SceneNode(name = "town", children = listOf(link("a", "fx/fire.prefab.json", 1f), link("b", "fx/fire.prefab.json", 2f)))))

        val expanded = document.withPrefabs { path -> reads += path; fire.toJson() }

        val (a, b) = expanded.nodes.single().children
        assertEquals(listOf("fx/fire.prefab.json"), reads)
        assertEquals(listOf(fire.root), a.children)
        assertEquals(listOf(fire.root), b.children)
        assertEquals(2f, b.transform.position.x, "the link places its copy")
        assertTrue(b.components.single() is ScenePrefabLink, "the link stays, so a save can write it back")
    }

    @Test
    fun aPrefabCanLinkAnother() = runTest {
        val camp = ScenePrefab(guid = "camp", root = SceneNode(name = "camp", children = listOf(link("fire", "fx/fire.prefab.json"))))
        val files = mapOf("fx/camp.prefab.json" to camp.toJson(), "fx/fire.prefab.json" to fire.toJson())

        val expanded = SceneDocument(nodes = listOf(link("camp", "fx/camp.prefab.json"))).withPrefabs { files.getValue(it) }

        assertEquals(listOf("flame"), expanded.nodes.single().children.single().children.single().children.single().children.map { it.name })
    }

    @Test
    fun aPrefabThatLinksItselfIsRefused() = runTest {
        val loop = ScenePrefab(guid = "loop", root = SceneNode(name = "loop", children = listOf(link("again", "fx/loop.prefab.json"))))

        val failure = assertFailsWith<IllegalArgumentException> {
            SceneDocument(nodes = listOf(link("loop", "fx/loop.prefab.json"))).withPrefabs { loop.toJson() }
        }

        assertTrue("fx/loop.prefab.json > fx/loop.prefab.json" in failure.message.orEmpty(), failure.message)
    }

    /** A link node's children would be replaced by the prefab's, so they are refused rather than lost. */
    @Test
    fun aLinkWithChildrenOfItsOwnIsRefused() = runTest {
        val crowded = link("a", "fx/fire.prefab.json").copy(children = listOf(SceneNode(name = "extra")))

        assertFailsWith<IllegalArgumentException> { SceneDocument(nodes = listOf(crowded)).withPrefabs { fire.toJson() } }
    }

    @Test
    fun aDocumentWithoutLinksIsReturnedAsItIs() = runTest {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "plain")))

        assertSame(document, document.withPrefabs { error("nothing to read") })
    }
}
