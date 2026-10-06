/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives animated transforms on 2D sprite cards and stages 2D UI overlay primitives.
 *
 * Demonstrates:
 * 1. Orthographic parallel projection where objects maintain size across depth (Z).
 * 2. 2D layer depth sorting without perspective foreshortening.
 * 3. Animated translation / rotation in the 2D plane (XY).
 * 4. Staging screen-space 2D graphics alongside scene geometry.
 */
internal object Sprites2dExampleDriver {
    private var backCardTransform: Transform? = null
    private var midCardTransform: Transform? = null
    private var frontCardTransform: Transform? = null
    private var elapsed = 0f

    /** Quad geometry in the XY plane facing +Z with unit extents (-0.5..0.5). */
    val spriteQuadGeometry: MeshGeometry = MeshGeometry(
        vertices = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 0.35f, 0.45f, 0.65f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0.35f, 0.45f, 0.65f,
            0.5f, 0.5f, 0f, 0f, 0f, 1f, 0.75f, 0.85f, 0.95f,
            -0.5f, 0.5f, 0f, 0f, 0f, 1f, 0.75f, 0.85f, 0.95f,
        ),
        indices = intArrayOf(0, 1, 2, 2, 3, 0),
        format = VertexFormat.PositionNormalColor,
    )

    fun createMesh(runtime: SceneAppLifecycleRuntime): Mesh =
        runtime.renderer.createMesh(spriteQuadGeometry)

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val back = instance.roots.find { it.name == "sprite-card-back" }
        val mid = instance.roots.find { it.name == "sprite-card-mid" }
        val front = instance.roots.find { it.name == "sprite-card-front" }

        backCardTransform = back?.let { runtime.world.get<Transform>(it.entity) }
        midCardTransform = mid?.let { runtime.world.get<Transform>(it.entity) }
        frontCardTransform = front?.let { runtime.world.get<Transform>(it.entity) }
        elapsed = 0f
    }

    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        elapsed += delta

        val bob0 = sin(elapsed * 2.0f) * 0.4f
        val bob1 = cos(elapsed * 2.5f) * 0.5f
        val bob2 = sin(elapsed * 1.8f + 1.0f) * 0.4f

        backCardTransform?.let { transform ->
            transform.position.y = bob0
            transform.rotation.z = sin(elapsed * 1.2f) * 0.1f
        }
        midCardTransform?.let { transform ->
            transform.position.y = bob1
            transform.rotation.z = -sin(elapsed * 1.5f) * 0.12f
        }
        frontCardTransform?.let { transform ->
            transform.position.y = bob2
            transform.rotation.z = cos(elapsed * 1.4f) * 0.08f
        }

        runtime.stageUi(overlayCommands(bob1))
    }

    fun detach() {
        backCardTransform = null
        midCardTransform = null
        frontCardTransform = null
        elapsed = 0f
    }

    private fun overlayCommands(offset: Float): List<UiDrawPrimitive> = listOf(
        DrawCommand.RoundedQuad(
            x = 32f,
            y = 32f + offset * 10f,
            w = 180f,
            h = 44f,
            color = Color(r = 0.15f, g = 0.2f, b = 0.35f, a = 0.85f),
            radius = 8f,
            tokenId = "engine-showcase-sprites-2d-badge",
        ),
    )
}
