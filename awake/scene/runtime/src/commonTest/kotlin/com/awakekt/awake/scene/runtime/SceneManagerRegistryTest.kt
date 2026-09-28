/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneCustomComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A game passes its own scene components to [SceneManager] instead of registering them globally,
 * and the built-in components keep loading alongside them.
 */
class SceneManagerRegistryTest {
    private class Marker

    private object MarkerResolver : SceneComponentResolver {
        override fun canResolve(component: SceneComponent): Boolean =
            component is SceneCustomComponent && component.type == "marker"

        override fun attach(world: World, entity: Entity, component: SceneComponent, context: SceneResolutionContext) {
            world.add(entity, Marker())
        }
    }

    private val document = SceneDocument(
        nodes = listOf(
            SceneNode(
                name = "Door",
                components = listOf(SceneCustomComponent(type = "marker", payload = JsonObject(emptyMap())), SceneSpinControl(speed = 2f)),
            ),
        ),
    )

    private fun World.count(type: kotlin.reflect.KClass<*>): Int {
        var count = 0
        @Suppress("UNCHECKED_CAST")
        queryEach(type as kotlin.reflect.KClass<Any>) { _, _ -> count++ }
        return count
    }

    @Test
    fun aCallersRegistryLoadsItsComponents() {
        val world = World()
        SceneManager(world, SceneComponentRegistry().register(MarkerResolver)).switchTo(document)
        assertEquals(1, world.count(Marker::class))
    }

    @Test
    fun withoutItTheComponentIsSkippedAsBefore() {
        val world = World()
        SceneManager(world).switchTo(document)
        assertEquals(0, world.count(Marker::class))
    }

    @Test
    fun builtInComponentsStillLoadAlongsideTheCallers() {
        val world = World()
        SceneManager(world, SceneComponentRegistry().register(MarkerResolver)).switchTo(document)
        assertEquals(1, world.count(SpinControl::class), "spin_control is a built-in binding")
        assertEquals(1, world.count(Marker::class))
    }
}
