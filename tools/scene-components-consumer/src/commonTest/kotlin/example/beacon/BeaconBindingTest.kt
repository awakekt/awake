/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package example.beacon

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class BeaconBindingTest {
    @Test
    fun publishedPluginGeneratesBindingRegistryAndSerializableSchema() = with(BeaconBinding) {
        assertSame(BeaconBinding, BeaconSceneComponents.bindings.single())
        val json = SceneComponentRegistry.scoped(bindings = BeaconSceneComponents.bindings).sceneJson()
        val saved = SceneBeacon(7.5f)
        // Wasm requires an explicit serializer for a non-serializable interface.
        val serializer = PolymorphicSerializer(SceneComponent::class)
        assertEquals(saved, json.decodeFromString(serializer, json.encodeToString(serializer, saved)))
        val world = World()
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world: World = world
            override fun deferNodeLink(targetNodeName: String, onResolved: (Entity) -> Unit) = Unit
            override fun recordRequest(request: Any) = Unit
        }
        attachTyped(world, entity, saved, context)
        val live = requireNotNull(world.get<Beacon>(entity))
        assertEquals(7.5f, live.strength)
        live.pressed = true
        assertEquals(saved, export(world, entity, live))
        assertFalse(live.toSceneComponent().toComponent().pressed)
    }
}
