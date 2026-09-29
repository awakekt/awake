/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.MeshGeometryBuilder
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.SceneAssetsDsl

/** The material every built-in mesh and untextured model draws with. */
const val LIT_SHADOW_MATERIAL = "lit-shadow"

/**
 * Registers the meshes and material that scenes name without shipping a file: `cube`, `sphere`,
 * `ground`, `plane`, `checkered-floor` and the [LIT_SHADOW_MATERIAL] material.
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
    builtIn("checkered-floor") { checkeredFloorGeometry() }
    material(LIT_SHADOW_MATERIAL) { renderer.createMaterial(LitShadowUniformLayout) }
}

/**
 * A [size] × [size] floor of [tiles] × [tiles] slate tiles in two shades, raised over a dark grout
 * plane that shows through [groutWidth] gaps.
 */
@Suppress("MagicNumber")
fun checkeredFloorGeometry(
    size: Float = 80f,
    tiles: Int = 40,
    groutWidth: Float = 0.04f,
): MeshGeometry {
    val lightSlate = Vec3f(0.86f, 0.88f, 0.90f)
    val darkSlate = Vec3f(0.27f, 0.29f, 0.33f)
    val groutColor = Vec3f(0.14f, 0.15f, 0.17f)
    val builder = MeshGeometryBuilder(VertexFormat.PositionNormalColor, (1 + tiles * tiles) * 4)
    var next = 0

    fun quad(minX: Float, minZ: Float, maxX: Float, maxZ: Float, y: Float, color: Vec3f) {
        builder.vertex(next, Vec3f(minX, y, minZ), Vec3f.UP, color)
        builder.vertex(next + 1, Vec3f(maxX, y, minZ), Vec3f.UP, color)
        builder.vertex(next + 2, Vec3f(maxX, y, maxZ), Vec3f.UP, color)
        builder.vertex(next + 3, Vec3f(minX, y, maxZ), Vec3f.UP, color)
        builder.quad(next, next + 2, next + 1, next + 3)
        next += 4
    }

    val half = size * 0.5f
    quad(-half, -half, half, half, y = -0.02f, groutColor)
    val tileSize = size / tiles
    val halfGrout = groutWidth * 0.5f
    for (tz in 0 until tiles) {
        for (tx in 0 until tiles) {
            quad(
                minX = -half + tx * tileSize + halfGrout,
                minZ = -half + tz * tileSize + halfGrout,
                maxX = -half + (tx + 1) * tileSize - halfGrout,
                maxZ = -half + (tz + 1) * tileSize - halfGrout,
                y = 0f,
                color = if ((tx + tz) % 2 == 0) lightSlate else darkSlate,
            )
        }
    }
    return builder.build()
}
