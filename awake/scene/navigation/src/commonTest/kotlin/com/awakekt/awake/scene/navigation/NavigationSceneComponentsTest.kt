/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigationSceneComponentsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generatedModuleRegistersAndRoundTripsNavigationBinding() {
        assertEquals(listOf(NavigationAgentBinding, NavigationBinding), NavigationSceneComponents.bindings)

        val registry = SceneComponentRegistry.scoped().register(NavigationSceneComponents)
        val json = registry.sceneJson()
        assertNotNull(json.serializersModule.getPolymorphic(SceneComponent::class, "navigation"))

        val saved = SceneNavigation(rows = listOf("...", ".#.", "..."), cellSize = 1.5f, originX = -2f, originZ = 4f)
        val polymorphic = PolymorphicSerializer(SceneComponent::class)
        val encoded = json.encodeToString(polymorphic, saved)
        val decoded = json.decodeFromString(polymorphic, encoded)
        assertEquals(saved, decoded)

        val world = World()
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world: World = world
            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
            override fun recordRequest(request: Any) = Unit
        }
        NavigationBinding.attach(world, entity, decoded, context)
        assertEquals(saved, NavigationBinding.exportFrom(world, entity))
    }

    @Test
    fun aNavigationAgentRoundTripsThroughTheSceneFile() {
        val registry = SceneComponentRegistry.scoped().register(NavigationSceneComponents)
        val json = registry.sceneJson()
        val saved = SceneNavigationAgent(radius = 0.6f, speed = 5f, repathInterval = 0f, destinationX = 3f, destinationZ = -4f)
        val polymorphic = PolymorphicSerializer(SceneComponent::class)
        val decoded = json.decodeFromString(polymorphic, json.encodeToString(polymorphic, saved))
        assertEquals(saved, decoded)

        val world = World()
        val entity = world.create()
        NavigationAgentBinding.attach(world, entity, decoded, context(world))
        val agent = assertNotNull(world.get<NavigationAgent>(entity))
        assertEquals(0.6f, agent.follower.agent.radius)
        assertEquals(-4f, agent.follower.destination.z, "the authored destination is where it heads")
        assertEquals(saved, NavigationAgentBinding.exportFrom(world, entity))
    }

    @Test
    fun aNavigationAgentRejectsNonsense() {
        assertTrue(SceneNavigationAgent().validate("agent").isEmpty())
        assertEquals(1, SceneNavigationAgent(speed = 0f).validate("agent").size)
        assertEquals(1, SceneNavigationAgent(destinationX = 1f).validate("agent").size, "a destination needs both coordinates")
    }

    private fun context(world: World) = object : SceneResolutionContext {
        override val world: World = world
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
        override fun recordRequest(request: Any) = Unit
    }
}
