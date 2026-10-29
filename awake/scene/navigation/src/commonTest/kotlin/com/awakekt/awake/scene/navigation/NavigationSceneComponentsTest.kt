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

class NavigationSceneComponentsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generatedModuleRegistersAndRoundTripsNavigationBinding() {
        assertEquals(listOf(NavigationBinding), NavigationSceneComponents.bindings)

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
}
