/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Tags
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.hasTag
import com.awakekt.awake.ecs.withTag
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.SceneTag
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.withPrefabs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A node's `tag` loads as its entity's [Tags], answers gameplay's lookups, and saves back as authored. */
class SceneTagTest {

    @Test
    fun tagsRoundTripThroughTheDocumentAndTheWorld() {
        val document = SceneLoader.decode(SCENE)
        assertTrue(SceneValidator.validate(document).isEmpty(), "${SceneValidator.validate(document)}")
        assertEquals(document, SceneLoader.decode(SceneLoader.encode(document)), "decode and encode round-trip")

        val world = SceneLoader.instantiate(document).world
        val saved = SceneLoader.decode(SceneLoader.encode(SceneLoader.fromWorld(world, name = "harbor")))

        assertEquals(document.tagsByName(), saved.tagsByName())
    }

    @Test
    fun gameplayFindsEntitiesByTagWhateverElseTheyAreTagged() {
        val world = SceneLoader.instantiate(SceneLoader.decode(SCENE)).world

        assertEquals(setOf("Raider", "Drone"), world.withTag("enemy").names(world))
        assertEquals(setOf("Drone"), world.withTag("flying").names(world))
        assertEquals(emptySet(), world.withTag("boss").names(world))
        assertTrue(world.hasTag(world.named("Drone"), "enemy"))
        assertFalse(world.hasTag(world.named("Crate"), "enemy"), "an entity with no tags has none")
    }

    @Test
    fun aTagThatIsNotOneFailsValidation() {
        val document = SceneLoader.decode(
            """{ "version": 1, "name": "bad", "nodes": [ { "name": "Raider", "components": [ { "component": "tag", "tags": ["enemy", "two words", ""] } ] } ] }""",
        )

        val issues = SceneValidator.validate(document).map { it.message }

        assertEquals(2, issues.size, "$issues")
        assertTrue(issues.any { "\"two words\"" in it } && issues.any { "\"\"" in it }, "$issues")
    }

    /** A prefab's tags ride on its root, which becomes the linking node's child; the link keeps its own. */
    @Test
    fun aPrefabsTagsRideOnItsRootAndTheLinkKeepsItsOwn() = runTest {
        val raider = ScenePrefab(guid = "raider", root = SceneNode(name = "Raider", components = listOf(SceneTag(setOf("enemy")))))
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Ambush",
                    components = listOf(ScenePrefabLink("npc/raider.prefab.json"), SceneTag(setOf("spawn"))),
                ),
            ),
        ).withPrefabs { raider.toJson() }

        val world = SceneLoader.instantiate(document).world

        assertEquals(setOf("Raider"), world.withTag("enemy").names(world))
        assertEquals(setOf("Ambush"), world.withTag("spawn").names(world))
    }

    private fun SceneDocument.tagsByName(): Map<String?, Set<String>> =
        nodes.associate { node -> node.name to node.components.filterIsInstance<SceneTag>().flatMap { it.tags }.toSet() }

    private fun List<Entity>.names(world: World): Set<String> =
        mapNotNull { world.get<Name>(it)?.value }.toSet()

    private fun World.named(name: String): Entity =
        withNames().getValue(name)

    private fun World.withNames(): Map<String, Entity> = buildMap {
        queryEach(Name::class) { entity, value -> put(value.value, entity) }
    }

    private companion object {
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Raider", "components": [ { "component": "tag", "tags": ["enemy"] } ] },
  { "name": "Drone", "components": [ { "component": "tag", "tags": ["enemy", "flying"] } ] },
  { "name": "Crate" }
] }
"""

        init {
            DefaultSceneComponentResolvers.install()
        }
    }
}
