/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.Perceivable
import com.awakekt.awake.ai.perception.Perceiver
import com.awakekt.awake.ai.perception.PerceptionSettings
import com.awakekt.awake.ai.perception.PerceptionSystem
import com.awakekt.awake.ai.perception.Relation
import com.awakekt.awake.ai.perception.SightSettings
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.ai.perception.Factions
import com.awakekt.awake.scene.ai.perception.SceneFactionRelation
import com.awakekt.awake.scene.ai.perception.SceneFactions
import com.awakekt.awake.scene.ai.perception.ScenePerceivable
import com.awakekt.awake.scene.ai.perception.ScenePerceiver
import com.awakekt.awake.scene.ai.perception.TransformPerceptionSpace
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.SceneVec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenePerceptionTest {
    private val registry = SceneComponentRegistry().registerAiBehaviors()

    private val document = SceneDocument(
        nodes = listOf(
            SceneNode(
                name = "World",
                components = listOf(SceneFactions(listOf(SceneFactionRelation("watch", "thieves", Relation.Hostile)))),
            ),
            SceneNode(
                name = "Guard",
                transform = SceneTransform(rotation = SceneVec3(0f, (PI / 2).toFloat(), 0f)),
                components = listOf(ScenePerceiver("watch", PerceptionSettings(sight = SightSettings(focusRange = 12f), updateInterval = 0f))),
            ),
            SceneNode(
                name = "Thief",
                transform = SceneTransform(position = SceneVec3(8f, 0f, 0f)),
                components = listOf(ScenePerceivable("thieves", height = 0.5f)),
            ),
        ),
    )

    @Test
    fun theComponentsRoundTripThroughASceneDocument() {
        val world = SceneLoader.instantiate(SceneLoader.decode(SceneLoader.encode(document)), componentRegistry = registry).world
        assertEquals(
            document.nodes.map { it.components },
            SceneLoader.fromWorld(world, componentRegistry = registry).nodes.map { it.components },
        )
    }

    @Test
    fun aGuardFacingByItsTransformSeesTheThief() {
        val world = SceneLoader.instantiate(document, componentRegistry = registry).world
        val factions = buildList { world.queryEach(Factions::class) { _, factions -> add(factions.table) } }.single()
        PerceptionSystem(TransformPerceptionSpace, factions).update(world, 0.1f)
        val guard = buildList { world.queryEach(Name::class) { e, n -> if (n.value == "Guard") add(e) } }.single()
        val memory = world.get<Perceiver>(guard)!!.memory
        val seen = memory.entities.single()
        assertEquals(Relation.Hostile, seen.relation)
        assertEquals(Vec3f(8f, 0f, 0f), seen.lastKnownPosition)

        // Turned to face -X, the thief is behind it and out of the side cone's reach.
        world.get<Transform>(guard)!!.rotation.y = (-PI / 2).toFloat()
        PerceptionSystem(TransformPerceptionSpace, factions).update(world, 0.1f)
        assertTrue(memory.entities.none { it.inSight })
        assertEquals(FactionId("thieves"), world.get<Perceivable>(seen.entity)!!.faction)
    }

    @Test
    fun contradictoryFactionsAndBlankNamesAreInvalid() {
        val bad = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "World",
                    components = listOf(
                        SceneFactions(
                            listOf(
                                SceneFactionRelation("a", "b", Relation.Hostile),
                                SceneFactionRelation("b", "a", Relation.Friendly),
                            ),
                        ),
                        ScenePerceiver(" "),
                    ),
                ),
            ),
        )
        val messages = SceneValidator.validate(bad).map { it.message }
        assertTrue(messages.any { "both hostile" in it.lowercase() || "both Hostile" in it }, "$messages")
        assertTrue(messages.any { "perceiver.faction must not be blank" in it }, "$messages")
    }
}
