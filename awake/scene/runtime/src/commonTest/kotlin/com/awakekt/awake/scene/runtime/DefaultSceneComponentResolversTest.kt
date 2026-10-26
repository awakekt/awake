/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.PrefabLinkBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The default resolvers, now composed from module registries, are the set the hand-kept list held. */
class DefaultSceneComponentResolversTest {
    private val handKeptList = setOf(
        "CameraBinding", "LightBinding", "MaterialBinding", "SpinControlBinding", "TagBinding", "StaticTransformBinding",
        "MeshRendererBinding", "SkyboxBinding", "FogBinding", "ToneMappingBinding", "PostProcessingBinding",
        "AmbientLightBinding", "TerrainBinding", "CanvasElementBinding", "CanvasScalerBinding", "CanvasControlsBinding",
        "CanvasFollowLimitBinding", "CanvasFollowOcclusionBinding", "LocomotionAnimationBinding", "KeyframeAnimationBinding",
        "DayCycleBinding", "ParticleEmitterBinding", "TextureAnimationBinding", "TextureClipsBinding", "SpriteBinding",
        "TilemapBinding", "SpriteClipsBinding", "ShaderEffectBinding",
    )

    @Test
    fun theModulesHoldEveryBindingTheHandKeptListHeldAndNoOther() {
        val names = DefaultSceneComponentResolvers.bindings.map { it::class.simpleName }
        assertEquals(names.size, names.toSet().size, "a binding is listed twice: $names")
        assertEquals(handKeptList, names.toSet())
        assertEquals(DefaultSceneComponentResolvers.bindings + PrefabLinkBinding, DefaultSceneComponentResolvers.all)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun aScopeRegisteringTheModulesDecodesEachOfTheirComponentIds() {
        val registry = SceneComponentRegistry.scoped()
        DefaultSceneComponentResolvers.modules.forEach { registry.register(it) }
        val json = registry.sceneJson()
        for (module in DefaultSceneComponentResolvers.modules) {
            for (serializer in module.bindings.mapNotNull { it.serializer }) {
                val id = serializer.descriptor.serialName
                assertNotNull(json.serializersModule.getPolymorphic(SceneComponent::class, id), "the scope decodes $id")
                assertNotNull(module.serializersModule.getPolymorphic(SceneComponent::class, id), "${module::class.simpleName} lists $id")
            }
        }
    }
}
