/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.document.MeshRendererBinding
import com.awakekt.awake.scene.document.PrefabLinkBinding
import com.awakekt.awake.scene.document.SceneCamera
import com.awakekt.awake.scene.document.SceneComponentBinding
import com.awakekt.awake.scene.document.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneComponentResolver
import com.awakekt.awake.scene.document.SceneLight
import com.awakekt.awake.scene.document.ScenePbrMaterial
import com.awakekt.awake.scene.document.SceneSpinControl
import com.awakekt.awake.scene.document.SpinControlBinding
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.Camera as SceneCameraComponent

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
