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
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.SceneLight
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One cascade: the fixed shadow box at the origin, lit from low on the left and a little behind,
 * oblique to every axis so each of the depth row's terms matters, whose near plane sits
 * 15 units toward the light. A caster centred 50 units along the light direction is squarely in
 * the box's footprint and wholly in front of that plane.
 */
class ShadowDepthReachTest {
    private val toLight = Vec3f(-1f, 0.25f, 0.5f).normalized()
    private val box = directionalShadowBox(toLight, ClipSpace.WebGpu)
    private val cascades = GpuShadowCascadeData(listOf(box.viewProjection), floatArrayOf(10f))

    @Test
    fun aCasterInFrontOfTheNearPlanePullsItBackFarEnoughToHoldTheCaster() {
        val caster = request(boundsAround(toLight * 50f))

        val reached = cascades.reachingCasters(listOf(caster), ClipSpace.WebGpu)

        assertNotSame(cascades, reached)
        val moved = reached.viewProjections.single()
        corners(caster.worldBounds!!).forEach { corner ->
            val depth = depthOf(moved, corner)
            assertTrue(depth in 0f..1f, "corner $corner at depth $depth is still clipped")
        }
        assertTrue(depthOf(box.viewProjection, toLight * 50f) < 0f, "the fixture must start clipped, or this proves nothing")
    }

    @Test
    fun pullingBackKeepsTheFootprintTheFarPlaneAndTheReceiverDepthScale() {
        val reached = cascades.reachingCasters(listOf(request(boundsAround(toLight * 50f))), ClipSpace.WebGpu)
        val before = box.viewProjection.data
        val after = reached.viewProjections.single().data

        // Rows x, y and w (column-major: indices r, 4 + r, 8 + r, 12 + r) are untouched.
        for (row in listOf(0, 1, 3)) {
            for (column in 0 until 4) assertEquals(before[column * 4 + row], after[column * 4 + row], "row $row column $column")
        }
        // A point on the far plane stays on it.
        val farPoint = toLight * (SHADOW_LIGHT_DISTANCE - SHADOW_FAR)
        assertEquals(1f, depthOf(box.viewProjection, farPoint), 1e-4f)
        assertEquals(1f, depthOf(reached.viewProjections.single(), farPoint), 1e-4f)
        // The receiver bias converts through the depth scale, which must match the new matrix.
        val rowLength = sqrt(after[2] * after[2] + after[6] * after[6] + after[10] * after[10])
        assertEquals(rowLength, reached.depthScales.single(), 1e-6f)
        assertTrue(reached.depthScales.single() < cascades.depthScales.single(), "a longer depth range has a smaller scale")
        assertEquals(cascades.splitDistances.toList(), reached.splitDistances.toList())
        assertEquals(cascades.worldExtents.toList(), reached.worldExtents.toList())
    }

    @Test
    fun castersThatCannotSetThePlaneLeaveTheCascadesAsTheyWere() {
        val inRange = request(boundsAround(Vec3f.ZERO))
        val transparent = request(boundsAround(toLight * 50f)).apply { this.transparent = true }
        val unbounded = request(null)
        val beside = request(boundsAround(toLight * 50f + toLight.cross(Vec3f(0f, 1f, 0f)).normalized() * 40f))

        assertSame(cascades, cascades.reachingCasters(listOf(inRange, transparent, unbounded, beside), ClipSpace.WebGpu))
    }

    @Test
    fun theCompilerPullsThePlaneBackOnlyForABackendThatDoesNotClamp() {
        val drawCalls = listOf(request(boundsAround(Vec3f.ZERO)), request(boundsAround(toLight * 50f), shadowsOnly = true))
        fun compiled(clamps: Boolean) = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 5f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f),
            drawCalls = drawCalls,
            light = SceneLight(direction = toLight, color = Vec3f(1f, 1f, 1f), viewProjection = box.viewProjection),
            clipSpace = ClipSpace.WebGpu,
            aspect = 1f,
            drawPreparer = preparer(clamps),
        )

        val clamped = compiled(clamps = true)
        val clipped = compiled(clamps = false)

        assertEquals(box.viewProjection.data.toList(), clamped.shadowCascadeData!!.viewProjections.single().data.toList())
        val pulled = clipped.shadowCascadeData!!.viewProjections.single()
        assertTrue(depthOf(pulled, toLight * 50f) >= 0f, "the shadow-only caster is inside the moved cascade")
        assertEquals(pulled.data.toList(), clipped.prePasses.first().viewProjection.data.toList(), "the depth pass renders the moved cascade")
    }

    private fun preparer(clamps: Boolean) = object : GpuDrawPreparer {
        override val clampsShadowDepth = clamps

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) = GpuResolvedDraw(
            pipeline = object : PipelineHandle {},
            materialBinding = object : MaterialBinding {},
            vertexBuffer = object : BufferHandle {},
            indexBuffer = null,
            elementCount = 3,
        )
    }

    private fun request(bounds: Aabb?, shadowsOnly: Boolean = false) =
        RenderDrawCommand(mesh, material, worldBounds = bounds, shadowsOnly = shadowsOnly)

    private fun boundsAround(center: Vec3f) = Aabb(center - Vec3f(1f, 1f, 1f), center + Vec3f(1f, 1f, 1f))

    private fun corners(box: Aabb): List<Vec3f> = (0 until 8).map { bits ->
        Vec3f(
            if (bits and 1 == 0) box.min.x else box.max.x,
            if (bits and 2 == 0) box.min.y else box.max.y,
            if (bits and 4 == 0) box.min.z else box.max.z,
        )
    }

    /** Clip-space depth of [point]; w is 1 for these orthographic matrices. */
    private fun depthOf(viewProjection: Mat4, point: Vec3f): Float {
        val m = viewProjection.data
        return m[2] * point.x + m[6] * point.y + m[10] * point.z + m[14]
    }

    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
