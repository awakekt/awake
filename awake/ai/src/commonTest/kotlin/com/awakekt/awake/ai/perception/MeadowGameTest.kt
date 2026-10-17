/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.definition.NodeDefinition
import com.awakekt.awake.ai.definition.NodeKindId
import com.awakekt.awake.ai.definition.StateDefinition
import com.awakekt.awake.ai.definition.StateMachineDefinition
import com.awakekt.awake.ai.definition.TransitionDefinition
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ai.fsm.AiStateMachineSystem
import com.awakekt.awake.core.math.Vec3f
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A made-up wildlife game: deer graze in a meadow with a boulder in it and bolt from wolves they
 * hear or see. Deer and wolves are factions in the game's data; the deer's machine is built from
 * Core's kinds and the perception conditions.
 */
class MeadowGameTest {
    private val deer = FactionId("deer")
    private val wolves = FactionId("wolves")
    private val factions = FactionTable(listOf(FactionRelation(deer, wolves, Relation.Hostile, mutual = false)))

    private val scene = PerceptionScene()
    private val doe = scene.spawn(0f, 0f, faceX = 0f, faceZ = 1f)
    private val wolf = scene.spawn(-8f, 0f)
    private val system = PerceptionSystem(scene, factions, Wall(x = -4f, fromZ = -2f, toZ = 2f))

    private fun condition(kind: String, json: String = "{}") =
        NodeDefinition(NodeKindId(kind), params = Json.parseToJsonElement(json).jsonObject)

    private val machine = StateMachineDefinition(
        initial = "graze",
        states = listOf(StateDefinition("graze"), StateDefinition("bolt")),
        transitions = listOf(
            TransitionDefinition(from = "graze", to = "bolt", condition = condition("ai.perception.heard", """{"withinSeconds":0.5}""")),
            TransitionDefinition(from = "graze", to = "bolt", condition = condition("ai.perception.sees")),
            TransitionDefinition(
                from = "bolt",
                to = "graze",
                after = 1f,
                condition = NodeDefinition(NodeKindId("ai.inverter"), children = listOf(condition("ai.perception.remembers", """{"relation":"hostile"}"""))),
            ),
        ),
    )

    init {
        val settings = PerceptionSettings(
            sight = SightSettings(focusRange = 20f, focusAngle = 90f, peripheralRange = 6f, peripheralAngle = 300f),
            hearing = HearingSettings(range = 10f, muffledBy = 0.5f),
            forgetAfter = 2f,
            updateInterval = 0.25f,
        )
        scene.world.add(doe, Perceiver(deer, settings))
        scene.world.add(doe, AiRegistry().compileStateMachine(machine).newComponent())
        scene.world.add(wolf, Perceivable(wolves))
    }

    private val behaviour = AiStateMachineSystem()

    private fun step(seconds: Float = 0.25f): String {
        system.update(scene.world, seconds)
        behaviour.update(scene.world, seconds)
        return scene.world.get<AiStateMachineComponent>(doe)!!.stateMachine.currentState.name
    }

    @Test
    fun aQuietStepBehindTheBoulderIsMuffledButALoudOneIsHeard() {
        assertEquals("graze", step())
        // 8 m away behind the boulder: a quiet step reaches 7 m, halved by the boulder.
        system.hear(SoundEvent(Vec3f(-8f, 0f, 0f), loudness = 0.7f, source = wolf))
        assertEquals("graze", step())
        assertNull(scene.world.get<Perceiver>(doe)!!.memory.noise)

        // A howl carries 20 m, so even halved it reaches.
        system.hear(SoundEvent(Vec3f(-8f, 0f, 0f), loudness = 2f, source = wolf))
        assertEquals("bolt", step())
        val board = scene.world.get<AiStateMachineComponent>(doe)!!.blackboard
        assertEquals(Vec3f(-8f, 0f, 0f), board.get<Vec3f>("perception.noise.position"))
        assertEquals(wolf, board.get("perception.target"))
        assertEquals(Sense.Hearing, scene.world.get<Perceiver>(doe)!!.memory[wolf]!!.sense)
    }

    @Test
    fun theDoeBoltsFromAWolfItSeesAndCalmsOnceItForgets() {
        assertEquals("graze", step())
        scene.place(wolf, 0f, 15f)
        assertEquals("bolt", step())
        scene.place(wolf, 0f, 40f)
        val states = List(12) { step() }
        // Forgotten 2 s after it was last seen, then 1 s more in "bolt" at least.
        assertEquals("bolt", states[6])
        assertEquals("graze", states.last())
    }
}
