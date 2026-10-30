/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Tags
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinControlBinding
import com.awakekt.awake.scene.core.transform.StaticTransform
import com.awakekt.awake.scene.core.transform.StaticTransformBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The saved form each scene-core binding writes, byte for byte, recorded from the hand-written
 * bindings before they were generated. Every float is a dyadic fraction, so it prints the same on
 * every target.
 */
class SceneCoreBindingGoldenTest {
    @Test
    fun tagsSaveAndLoadAsTheHandWrittenBindingDid() {
        val json = """{"tags":["enemy","flying"]}"""
        assertExports(json, TagBinding, Tags(linkedSetOf("enemy", "flying")))
        val live = assertRoundTrips(json, TagBinding)
        assertEquals(setOf("enemy", "flying"), live.names)
    }

    @Test
    fun spinControlSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"radians":0.5,"speed":1.5}"""
        assertExports(json, SpinControlBinding, SpinControl().also { it.radians = 0.5f; it.speed = 1.5f })
        val live = assertRoundTrips(json, SpinControlBinding)
        assertEquals(0.5f, live.radians)
        assertEquals(1.5f, live.speed)
    }

    @Test
    fun staticTransformSavesAndLoadsAsTheHandWrittenBindingDid() {
        assertExports("{}", StaticTransformBinding, StaticTransform)
        assertSame(StaticTransform, assertRoundTrips("{}", StaticTransformBinding))
    }
}

private val json = SceneSerializers.createJson()

private class NoLinks(override val world: World) : SceneResolutionContext {
    override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

    override fun recordRequest(request: Any) = Unit
}

/** Asserts [binding] exports [live] as exactly [expected]. */
internal fun <C : Any, S : SceneComponent> assertExports(expected: String, binding: SceneComponentBinding<C, S>, live: C) {
    val world = World()
    val entity = world.create()
    world.add(entity, binding.componentClass, live)
    assertEquals(expected, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
}

/** Asserts [saved] loads through [binding] and saves back as exactly [saved]; returns the live component. */
internal fun <C : Any, S : SceneComponent> assertRoundTrips(saved: String, binding: SceneComponentBinding<C, S>): C {
    val world = World()
    val entity = world.create()
    binding.attach(world, entity, json.decodeFromString(binding.serializer!!, saved), NoLinks(world))
    assertEquals(saved, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
    return world.get(entity, binding.componentClass)!!
}
