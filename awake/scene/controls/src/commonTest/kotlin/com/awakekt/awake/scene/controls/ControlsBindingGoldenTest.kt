/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementControlBinding
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The saved form `MovementControlBinding` writes, byte for byte, recorded from the hand-written
 * binding before it was generated. Every float is a dyadic fraction, so it prints the same on every
 * target.
 */
class ControlsBindingGoldenTest {
    @Test
    fun movementControlDefaultsOmitNullSpeedsAndDropRuntimeIntent() {
        val json = """{"turnSpeed":0.0,"driver":"Player"}"""
        val dirtyLive = MovementControl().apply {
            moveX = 0.5f
            moveY = 0.25f
            moveZ = -0.5f
            moveSpeed = 3.5f
            jump = true
            run = true
        }
        assertExports(json, MovementControlBinding, dirtyLive)

        val live = assertRoundTrips(json, MovementControlBinding)
        assertNull(live.speed)
        assertNull(live.runSpeed)
        assertEquals(0f, live.turnSpeed)
        assertEquals(MovementDriver.Player, live.driver)
        assertEquals(0f, live.moveX)
        assertEquals(0f, live.moveY)
        assertEquals(0f, live.moveZ)
        assertNull(live.moveSpeed)
        assertFalse(live.jump)
        assertFalse(live.run)
    }

    @Test
    fun movementControlSavesAndLoadsConfiguredSpeedsAndDriverAsTheHandWrittenBindingDid() {
        val json = """{"speed":4.5,"runSpeed":7.25,"turnSpeed":2.5,"driver":"Agent"}"""
        val configured = MovementControl().apply {
            speed = 4.5f
            runSpeed = 7.25f
            turnSpeed = 2.5f
            driver = MovementDriver.Agent
        }
        assertExports(json, MovementControlBinding, configured)

        val live = assertRoundTrips(json, MovementControlBinding)
        assertEquals(4.5f, live.speed)
        assertEquals(7.25f, live.runSpeed)
        assertEquals(2.5f, live.turnSpeed)
        assertEquals(MovementDriver.Agent, live.driver)
    }
}

private val json = SceneSerializers.createJson()

private class NoLinks(override val world: World) : SceneResolutionContext {
    override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

    override fun recordRequest(request: Any) = Unit
}

private fun <C : Any, S : SceneComponent> assertExports(expected: String, binding: SceneComponentBinding<C, S>, live: C) {
    val world = World()
    val entity = world.create()
    world.add(entity, binding.componentClass, live)
    assertEquals(expected, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
}

private fun <C : Any, S : SceneComponent> assertRoundTrips(saved: String, binding: SceneComponentBinding<C, S>): C {
    val world = World()
    val entity = world.create()
    binding.attach(world, entity, json.decodeFromString(binding.serializer!!, saved), NoLinks(world))
    assertEquals(saved, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
    return world.get(entity, binding.componentClass)!!
}
