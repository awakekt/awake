/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.SceneAssetsDsl

/** The material every built-in mesh and untextured model draws with. */
const val LIT_SHADOW_MATERIAL = "lit-shadow"

/**
 * Registers the neutral meshes and material a scene can name without shipping a file: `cube`,
 * `sphere`, `ground`, `plane` and the [LIT_SHADOW_MATERIAL] material. Anything a template draws
 * beyond these is a project asset.
 *
 * [onGeometry] sees each mesh's geometry as it is built, for callers that need its bounds.
 */
fun SceneAssetsDsl.builtInSceneAssets(onGeometry: (name: String, geometry: MeshGeometry) -> Unit = { _, _ -> }) {
    fun builtIn(name: String, geometry: () -> MeshGeometry) = mesh(name) {
        renderer.createMesh(geometry().also { onGeometry(name, it) })
    }
    builtIn("cube") { generate { cube(size = 1f, colored = true) } }
    builtIn("sphere") { generate { sphere(radius = 0.5f, colored = true) } }
    builtIn("ground") { generate { plane(size = 10f, colored = false) } }
    builtIn("plane") { generate { plane(size = 5f, colored = true) } }
    material(LIT_SHADOW_MATERIAL) { renderer.createMaterial(LitShadowUniformLayout) }
}
