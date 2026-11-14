/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * A backend that clips shadow depth pulls each cascade's near plane back to its casters (#1555).
 * Measuring every caster's reach toward the light once a frame leaves the cascades exactly as
 * scanning every caster in every cascade does, while a moving camera scans a small share of them.
 *
 * The scene: 3,000 props on a grid, with a row of towers far toward a low sun that pull some
 * cascades and not others, seen by a camera that sweeps across it.
 */
class LightDepthReachTest {

    @Test
    fun measuredReachLeavesEveryCascadeAsTheFullScanDoes() {
        val measured = ScenePassScratch()
        val full = ScenePassScratch().apply { reach = null }
        var pulledFrames = 0
        repeat(FRAMES) { frame ->
            val lens = lensAt(frame)
            val reached = compile(lens, measured).shadowCascadeData!!
            val reference = compile(lens, full).shadowCascadeData!!
            reached.viewProjections.indices.forEach { cascade ->
                assertContentEquals(reference.viewProjections[cascade].data, reached.viewProjections[cascade].data, "frame $frame, cascade $cascade")
            }
            assertContentEquals(reference.depthScales, reached.depthScales, "frame $frame's depth scales")
            if (!reached.depthScales.contentEquals(unpulled(lens).depthScales)) pulledFrames++
        }
        assertTrue(pulledFrames > 0, "some frame must pull a cascade, or this proves nothing")
    }

    @Test
    fun aMovingCameraScansAFewOfTheCasters() {
        val scratch = ScenePassScratch()
        repeat(FRAMES) { compile(lensAt(it), scratch) }
        val everyCaster = FRAMES.toLong() * DRAWS.size * DEFAULT_SHADOW_CASCADES
        val scanned = scratch.reach!!.scanned
        assertTrue(scanned * SCAN_SHARE < everyCaster, "scanned $scanned of $everyCaster caster-cascade pairs")
    }

    private fun compile(lens: Lens, scratch: ScenePassScratch): GpuPassInput = ScenePassCompiler.compile(
        lens, DRAWS, light(lens), EnvironmentUniforms.Default, ClipSpace.WebGpu, 1f, null, null, emptyList(),
        ImagePassChain.Empty, null, scratch,
    )

    /** The cascades as fitted, before any is pulled: a backend that clamps depth keeps these. */
    private fun unpulled(lens: Lens) = light(lens).cascades!!

    private companion object {
        const val FRAMES = 40
        const val COLUMNS = 55
        const val SPACING = 4f
        const val TOWERS = 8

        /** The share of caster-cascade pairs a moving frame may scan at most: one in this many. */
        const val SCAN_SHARE = 3

        val SUN = Vec3f(0.9f, 0.35f, 0.25f)

        val mesh = object : Mesh {
            override val format = VertexFormat.PositionNormalColor
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }

        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }

        val DRAWS: List<RenderDrawCommand> = List(3000) { index ->
            val x = (index % COLUMNS - COLUMNS / 2) * SPACING
            val z = (index / COLUMNS - COLUMNS / 2) * SPACING
            box(Vec3f(x, 1f, z), 1f, 1f)
        } + List(TOWERS) { index -> box(Vec3f(150f, 40f, (index - TOWERS / 2) * 20f), 3f, 40f) }

        fun box(center: Vec3f, half: Float, halfHeight: Float) = RenderDrawCommand(
            mesh,
            material,
            worldBounds = Aabb(center - Vec3f(half, halfHeight, half), center + Vec3f(half, halfHeight, half)),
        )

        fun lensAt(frame: Int) =
            Lens(eye = Vec3f(frame * 2f - 40f, 12f, 30f), center = Vec3f(frame * 2f - 40f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 200f)

        fun light(lens: Lens): SceneLight {
            val sun = SceneLight(direction = SUN, color = Vec3f(1f, 1f, 1f))
            return sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, ClipSpace.WebGpu))
        }
    }
}
