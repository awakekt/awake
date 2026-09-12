/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test_health")
private data class TestHealthSceneComponent(val hp: Int) : SceneComponent

private data class TestHealthEcsComponent(val hp: Int)

private class TestHealthBinding : SceneComponentBinding<TestHealthEcsComponent, TestHealthSceneComponent> {
    override val componentClass: KClass<TestHealthEcsComponent> = TestHealthEcsComponent::class
    override val schemaClass: KClass<TestHealthSceneComponent> = TestHealthSceneComponent::class
    override val serializer = TestHealthSceneComponent.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: TestHealthSceneComponent,
        context: SceneResolutionContext,
    ) {
        world.add(entity, TestHealthEcsComponent(component.hp))
    }

    override fun export(
        world: World,
        entity: Entity,
        component: TestHealthEcsComponent,
    ): TestHealthSceneComponent = TestHealthSceneComponent(component.hp)
}

class SceneComponentBindingTest {

    @Test
    fun bindingAutoResolvesBySchemaClassWithoutManualCasting() {
        val binding = TestHealthBinding()
        val matchComponent = TestHealthSceneComponent(hp = 100)
        val world = World()
        val entity = world.create()
        val dummyContext = object : SceneResolutionContext {
            override val world: World = world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) {
                // no-op for unit test
            }

            override fun recordRequest(request: Any) {
                // no-op for unit test
            }
        }

        assertTrue(binding.canResolve(matchComponent))
        binding.attach(world, entity, matchComponent, dummyContext)

        val attached = world.get<TestHealthEcsComponent>(entity)
        assertEquals(100, attached?.hp)
    }

    @Test
    fun registryResolvesAndExportsComponents() {
        val binding = TestHealthBinding()
        val registry = SceneComponentRegistry(bindings = listOf(binding))

        val world = World()
        val entity = world.create()
        world.add(entity, TestHealthEcsComponent(hp = 75))

        val exported = registry.exportComponents(world, entity)
        assertEquals(1, exported.size)
        assertEquals(TestHealthSceneComponent(hp = 75), exported.single())
    }
}
