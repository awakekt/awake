/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.scene.core.transform.SpinControl
import io.github.awakelab.awake.scene.document.MeshRendererBinding
import io.github.awakelab.awake.scene.document.PrefabLinkBinding
import io.github.awakelab.awake.scene.document.SceneCamera
import io.github.awakelab.awake.scene.document.SceneComponentBinding
import io.github.awakelab.awake.scene.document.SceneComponentRegistry
import io.github.awakelab.awake.scene.document.SceneComponentResolver
import io.github.awakelab.awake.scene.document.SceneLight
import io.github.awakelab.awake.scene.document.ScenePbrMaterial
import io.github.awakelab.awake.scene.document.SceneSpinControl
import io.github.awakelab.awake.scene.document.SpinControlBinding
import io.github.awakelab.awake.scene.rendering.Light
import io.github.awakelab.awake.scene.rendering.mesh.PbrMaterial
import io.github.awakelab.awake.scene.rendering.Camera as SceneCameraComponent

/**
 * Built-in resolvers and bindings for standard core scene components.
 */
object DefaultSceneComponentResolvers {
    val CameraResolver: SceneComponentBinding<SceneCameraComponent, SceneCamera> = CameraBinding
    val LightResolver: SceneComponentBinding<Light, SceneLight> = LightBinding
    val PbrMaterialResolver: SceneComponentBinding<PbrMaterial, ScenePbrMaterial> = MaterialBinding
    val SpinControlResolver: SceneComponentBinding<SpinControl, SceneSpinControl> = SpinControlBinding
    val MeshRendererResolver: SceneComponentResolver = MeshRendererBinding
    val PrefabLinkResolver: SceneComponentResolver = PrefabLinkBinding

    val bindings: List<SceneComponentBinding<*, *>> = listOf(
        CameraBinding,
        LightBinding,
        MaterialBinding,
        SpinControlBinding,
    )

    val all: List<SceneComponentResolver> = listOf(
        CameraBinding,
        LightBinding,
        MaterialBinding,
        SpinControlBinding,
        MeshRendererBinding,
        PrefabLinkBinding,
    )

    fun install() {
        all.forEach(SceneComponentRegistry::registerGlobal)
    }

    init {
        install()
    }
}
