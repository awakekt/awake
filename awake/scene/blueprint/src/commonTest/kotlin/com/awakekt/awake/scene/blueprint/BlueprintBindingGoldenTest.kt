/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The saved form `BlueprintBinding` writes, byte for byte, recorded from the hand-written binding
 * before it was generated. Every float is a dyadic fraction, so it prints the same on every target.
 */
class BlueprintBindingGoldenTest {
    @Test
    fun blueprintWithoutVariablesSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"graph":"blueprints/door.blueprint.json","variables":{}}"""
        assertExports(json, BlueprintBinding, BlueprintComponent("blueprints/door.blueprint.json"))

        val live = assertRoundTrips(json, BlueprintBinding)
        assertEquals("blueprints/door.blueprint.json", live.graph)
        assertEquals(emptyMap(), live.variables)
        assertNull(live.instance)
    }

    @Test
    fun blueprintWithVariablesSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"graph":"blueprints/patrol_gate.blueprint.json","variables":{"speed":2.5,"open":true,"label":"North Gate"}}"""
        val variables = linkedMapOf(
            "speed" to JsonPrimitive(2.5f),
            "open" to JsonPrimitive(true),
            "label" to JsonPrimitive("North Gate"),
        )
        assertExports(json, BlueprintBinding, BlueprintComponent("blueprints/patrol_gate.blueprint.json", variables))

        val live = assertRoundTrips(json, BlueprintBinding)
        assertEquals("blueprints/patrol_gate.blueprint.json", live.graph)
        assertEquals(variables, live.variables)
        assertNull(live.instance)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generatedModuleRegistersBlueprintBinding() {
        assertEquals(listOf(BlueprintBinding), BlueprintSceneComponents.bindings)
        val scoped = SceneComponentRegistry.scoped().register(BlueprintSceneComponents)
        assertNotNull(scoped.sceneJson().serializersModule.getPolymorphic(SceneComponent::class, "blueprint"))
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
