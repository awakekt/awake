/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.ai.AiBehaviorBindings
import com.awakekt.awake.scene.ai.AiSceneComponents
import com.awakekt.awake.scene.audio.AudioSceneComponents
import com.awakekt.awake.scene.binding.SceneComponentModule
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.character.CharacterSceneComponents
import com.awakekt.awake.scene.controls.ControlsSceneComponents
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.navigation.NavigationSceneComponents
import com.awakekt.awake.scene.physics.PhysicsSceneComponents
import com.awakekt.awake.scene.worldstream.WorldstreamSceneComponents
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Core's capability-owned scene modules expose generated registries and register cleanly in a scope. */
class CapabilitySceneComponentsTest {
    private val capabilityModules: List<SceneComponentModule> = listOf(
        ControlsSceneComponents,
        WorldstreamSceneComponents,
        PhysicsSceneComponents,
        CharacterSceneComponents,
        AiSceneComponents,
        NavigationSceneComponents,
        AudioSceneComponents,
    )

    @Test
    fun capabilitiesTakeTheirBindingsFromGeneratedModuleRegistries() {
        assertEquals(ControlsSceneComponents.bindings, ControlsCapability.components)
        assertEquals(PhysicsSceneComponents.bindings + CharacterSceneComponents.bindings, PhysicsCapability.components)
        assertEquals(AiSceneComponents.bindings + NavigationSceneComponents.bindings, AiCapability.components)
        assertEquals(AiCapability.components, AiBehaviorBindings.bindings)
        assertEquals(AudioSceneComponents.bindings, AudioCapability.components)
        assertEquals(WorldstreamSceneComponents.bindings, StreamedTerrainCapability.components)

        val capabilityBindings = CORE_CAPABILITIES.flatMap { it.components }
        assertEquals(capabilityModules.flatMap { it.bindings }, capabilityBindings)
        assertEquals(capabilityBindings.size, capabilityBindings.toSet().size, "duplicate capability binding")
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun scopedProjectRegistryDecodesEveryDefaultAndCapabilityComponentId() {
        val registry = SceneComponentRegistry.scoped().registerProjectComponents()
        val json = registry.sceneJson()

        for (module in capabilityModules) {
            for (serializer in module.bindings.mapNotNull { it.serializer }) {
                val id = serializer.descriptor.serialName
                assertNotNull(module.serializersModule.getPolymorphic(SceneComponent::class, id), "${module::class.simpleName} lists $id")
                assertNotNull(json.serializersModule.getPolymorphic(SceneComponent::class, id), "project scope decodes $id")
            }
        }
    }
}
