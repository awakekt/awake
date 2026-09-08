/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneCustomComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private data class HealthComponent(var hp: Int)

private class HealthComponentResolver : SceneComponentResolver {
    override fun canResolve(component: SceneComponent): Boolean =
        component is SceneCustomComponent && component.type == "health"

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val custom = component as SceneCustomComponent
        val hp = custom.payload.let {
            if (it is JsonObject && it.containsKey("hp")) {
                it["hp"].toString().toIntOrNull() ?: 100
            } else {
                100
            }
        }
        world.add(entity, HealthComponent(hp))
    }
}

class SceneComponentResolverTest {
    @Test
    fun registeredResolverAttachesCustomComponentDuringInstantiation() {
        val payload = buildJsonObject { put("hp", 85) }
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Warrior",
                    components = listOf(SceneCustomComponent(type = "health", payload = payload)),
                ),
            ),
        )

        val world = World()
        val registry = SceneComponentRegistry(listOf(HealthComponentResolver()))
        val scene = SceneLoader.instantiate(doc, world = world, componentRegistry = registry)

        assertNotNull(scene)
        var heroHp: Int? = null
        world.queryEach<HealthComponent> { _, health ->
            heroHp = health.hp
        }
        assertEquals(85, heroHp)
    }

    @Test
    fun unregisteredCustomComponentDoesNotCrashInstantiation() {
        val payload = buildJsonObject { put("unknownKey", "value") }
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Ghost",
                    components = listOf(SceneCustomComponent(type = "unregistered_type", payload = payload)),
                ),
            ),
        )

        val world = World()
        val scene = SceneLoader.instantiate(doc, world = world)
        assertNotNull(scene)
        assertEquals(1, scene.roots.size)
    }

    @Test
    fun customBindingExportsAndInstantiatesRoundTrip() {
        val healthBinding = object : SceneComponentBinding<HealthComponent, SceneCustomComponent> {
            override val componentClass = HealthComponent::class
            override val schemaClass = SceneCustomComponent::class

            override fun canResolve(component: SceneComponent): Boolean =
                component is SceneCustomComponent && component.type == "health"

            override fun attachTyped(
                world: World,
                entity: Entity,
                component: SceneCustomComponent,
                context: SceneResolutionContext,
            ) {
                val hp = (component.payload as? JsonObject)?.get("hp")?.toString()?.toIntOrNull() ?: 100
                world.add(entity, HealthComponent(hp))
            }

            override fun export(world: World, entity: Entity, component: HealthComponent): SceneCustomComponent =
                SceneCustomComponent(
                    type = "health",
                    payload = buildJsonObject { put("hp", component.hp) },
                )

            override fun exportFrom(world: World, entity: Entity): SceneCustomComponent? =
                world.get(entity, componentClass)?.let { export(world, entity, it) }
        }

        val registry = SceneComponentRegistry(bindings = listOf(healthBinding))

        val world = World()
        val hero = world.create()
        world.add(hero, com.awakekt.awake.scene.core.Name("Hero"))
        world.add(hero, com.awakekt.awake.scene.core.transform.Transform())
        world.add(hero, HealthComponent(hp = 95))

        val doc = SceneLoader.fromWorld(world, componentRegistry = registry)
        val exportedComponent = doc.nodes.single().components.single() as SceneCustomComponent
        assertEquals("health", exportedComponent.type)
        assertEquals(95, (exportedComponent.payload as JsonObject)["hp"]?.toString()?.toInt())

        val restoredWorld = World()
        SceneLoader.instantiate(doc, world = restoredWorld, componentRegistry = registry)
        var restoredHp: Int? = null
        restoredWorld.queryEach<HealthComponent> { _, health ->
            restoredHp = health.hp
        }
        assertEquals(95, restoredHp)
    }
}
