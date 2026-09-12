/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.PrefabLinkBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinControlBinding
import com.awakekt.awake.scene.rendering.AmbientLight
import com.awakekt.awake.scene.rendering.Environment
import com.awakekt.awake.scene.rendering.Fog
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.Skybox
import com.awakekt.awake.scene.rendering.camera.CameraBinding
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.environment.AmbientLightBinding
import com.awakekt.awake.scene.rendering.environment.EnvironmentBinding
import com.awakekt.awake.scene.rendering.environment.FogBinding
import com.awakekt.awake.scene.rendering.environment.SceneAmbientLight
import com.awakekt.awake.scene.rendering.environment.SceneEnvironment
import com.awakekt.awake.scene.rendering.environment.SceneFog
import com.awakekt.awake.scene.rendering.environment.SceneSkybox
import com.awakekt.awake.scene.rendering.environment.SkyboxBinding
import com.awakekt.awake.scene.rendering.light.LightBinding
import com.awakekt.awake.scene.rendering.light.SceneLight
import com.awakekt.awake.scene.rendering.mesh.MaterialBinding
import com.awakekt.awake.scene.rendering.mesh.MeshRendererBinding
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.ScenePbrMaterial
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers.install
import com.awakekt.awake.scene.rendering.Camera as SceneCameraComponent

/**
 * Built-in resolvers and bindings for standard core scene components.
 *
 * **Callers must invoke [install] explicitly** at their composition root —
 * typically inside [SceneAppLifecycleRuntime.initialize] or a custom bootstrap.
 */
object DefaultSceneComponentResolvers {
    val CameraResolver: SceneComponentBinding<SceneCameraComponent, SceneCamera> = CameraBinding
    val LightResolver: SceneComponentBinding<Light, SceneLight> = LightBinding
    val PbrMaterialResolver: SceneComponentBinding<PbrMaterial, ScenePbrMaterial> = MaterialBinding
    val SpinControlResolver: SceneComponentBinding<SpinControl, SceneSpinControl> =
        SpinControlBinding
    val MeshRendererResolver: SceneComponentBinding<*, SceneMeshRenderer> = MeshRendererBinding
    val EnvironmentResolver: SceneComponentBinding<Environment, SceneEnvironment> = EnvironmentBinding
    val SkyboxResolver: SceneComponentBinding<Skybox, SceneSkybox> = SkyboxBinding
    val FogResolver: SceneComponentBinding<Fog, SceneFog> = FogBinding
    val AmbientLightResolver: SceneComponentBinding<AmbientLight, SceneAmbientLight> = AmbientLightBinding
    val PrefabLinkResolver: SceneComponentResolver = PrefabLinkBinding

    val bindings: List<SceneComponentBinding<*, *>> = listOf(
        CameraBinding,
        LightBinding,
        MaterialBinding,
        SpinControlBinding,
        MeshRendererBinding,
        EnvironmentBinding,
        SkyboxBinding,
        FogBinding,
        AmbientLightBinding,
    )

    val all: List<SceneComponentResolver> = listOf(
        CameraBinding,
        LightBinding,
        MaterialBinding,
        SpinControlBinding,
        MeshRendererBinding,
        EnvironmentBinding,
        SkyboxBinding,
        FogBinding,
        AmbientLightBinding,
        PrefabLinkBinding,
    )

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
