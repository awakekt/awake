/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.PrefabLinkBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentModule
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.canvas.CanvasSceneComponents
import com.awakekt.awake.scene.core.SceneCoreSceneComponents
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinControlBinding
import com.awakekt.awake.scene.particles.ParticlesSceneComponents
import com.awakekt.awake.scene.rendering.AmbientLight
import com.awakekt.awake.scene.rendering.Fog
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.Scene3dSceneComponents
import com.awakekt.awake.scene.rendering.Skybox
import com.awakekt.awake.scene.rendering.camera.CameraBinding
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.fog.FogBinding
import com.awakekt.awake.scene.rendering.fog.SceneFog
import com.awakekt.awake.scene.rendering.light.AmbientLightBinding
import com.awakekt.awake.scene.rendering.light.LightBinding
import com.awakekt.awake.scene.rendering.light.SceneAmbientLight
import com.awakekt.awake.scene.rendering.light.SceneLight
import com.awakekt.awake.scene.rendering.mesh.MaterialBinding
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.ScenePbrMaterial
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessingBinding
import com.awakekt.awake.scene.rendering.postprocessing.ScenePostProcessing
import com.awakekt.awake.scene.rendering.sky.SceneSkybox
import com.awakekt.awake.scene.rendering.sky.SkyboxBinding
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainBinding
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.rendering.tonemapping.SceneToneMapping
import com.awakekt.awake.scene.rendering.tonemapping.ToneMapping
import com.awakekt.awake.scene.rendering.tonemapping.ToneMappingBinding
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers.install
import com.awakekt.awake.scene.scene2d.Scene2dSceneComponents
import com.awakekt.awake.scene.shader.ShaderSceneComponents
import com.awakekt.awake.scene.worldtext.WorldTextSceneComponents
import com.awakekt.awake.scene.rendering.Camera as SceneCameraComponent

/**
 * Built-in resolvers and bindings for standard core scene components.
 *
 * **Callers must invoke [install] explicitly** at their composition root —
 * typically inside [SceneAppLifecycleRuntime.initialize] or a custom bootstrap.
 */
object DefaultSceneComponentResolvers {
    /** Binding resolver for [SceneCamera] documents and [SceneCameraComponent] ECS components. */
    val CameraResolver: SceneComponentBinding<SceneCameraComponent, SceneCamera> = CameraBinding

    /** Binding resolver for [SceneLight] documents and [Light] ECS components. */
    val LightResolver: SceneComponentBinding<Light, SceneLight> = LightBinding

    /** Binding resolver for [ScenePbrMaterial] documents and [PbrMaterial] ECS components. */
    val PbrMaterialResolver: SceneComponentBinding<PbrMaterial, ScenePbrMaterial> = MaterialBinding

    /** Binding resolver for [SceneSpinControl] documents and [SpinControl] ECS components. */
    val SpinControlResolver: SceneComponentBinding<SpinControl, SceneSpinControl> =
        SpinControlBinding

    /** Binding resolver for [SceneMeshRenderer] documents and renderable mesh components. */
    val MeshRendererResolver: SceneComponentBinding<*, SceneMeshRenderer> = MeshRendererBinding

    /** Binding resolver for [SceneSkybox] documents and [Skybox] ECS components. */
    val SkyboxResolver: SceneComponentBinding<Skybox, SceneSkybox> = SkyboxBinding

    /** Binding resolver for [SceneFog] documents and [Fog] ECS components. */
    val FogResolver: SceneComponentBinding<Fog, SceneFog> = FogBinding

    /** Binding resolver for [SceneToneMapping] documents and [ToneMapping] ECS components. */
    val ToneMappingResolver: SceneComponentBinding<ToneMapping, SceneToneMapping> = ToneMappingBinding

    /** Binding resolver for [ScenePostProcessing] documents and [PostProcessing] ECS components. */
    val PostProcessingResolver: SceneComponentBinding<PostProcessing, ScenePostProcessing> = PostProcessingBinding

    /** Binding resolver for [SceneAmbientLight] documents and [AmbientLight] ECS components. */
    val AmbientLightResolver: SceneComponentBinding<AmbientLight, SceneAmbientLight> = AmbientLightBinding

    /** Binding resolver for [SceneTerrain] documents and [TerrainComponent] ECS components. */
    val TerrainResolver: SceneComponentBinding<TerrainComponent, SceneTerrain> = TerrainBinding

    /** Binding resolver for nested prefab link documents. */
    val PrefabLinkResolver: SceneComponentResolver = PrefabLinkBinding

    /**
     * The scene component modules Core installs by default, in the order they register: each module's
     * generated registry of its `@SceneBinding`s and `@RegisteredBinding`s. An app or scene that wants
     * fewer registers the modules it uses with [SceneComponentRegistry.register] instead of [install].
     */
    val modules: List<SceneComponentModule> = listOf(
        SceneCoreSceneComponents,
        Scene3dSceneComponents,
        ParticlesSceneComponents,
        Scene2dSceneComponents,
        ShaderSceneComponents,
        CanvasSceneComponents,
        WorldTextSceneComponents,
    )

    /** Every standard two-way [SceneComponentBinding]: those of [modules], in their order. */
    val bindings: List<SceneComponentBinding<*, *>> = modules.flatMap { it.bindings }

    /** Every built-in [SceneComponentResolver]: [bindings] and the prefab link resolver. */
    val all: List<SceneComponentResolver> = bindings + PrefabLinkBinding

    /**
     * Registers all built-in resolvers into [SceneComponentRegistry]'s global list.
     */
    fun install() {
        all.forEach(SceneComponentRegistry::registerGlobal)
    }

    init {
        install()
    }
}
