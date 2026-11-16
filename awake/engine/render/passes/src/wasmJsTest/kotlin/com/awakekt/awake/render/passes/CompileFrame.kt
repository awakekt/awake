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
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight

/**
 * A 3,000-draw shadowed frame for the browser compile measurements (#1621): props on a 4 m grid
 * cycling through 190 meshes, one in ten blended, under a cascaded sun, with a preparer that hands
 * back draws it made once. With [instancing] the preparer instances, as the scene's backends do, so
 * copies of a mesh fold into instanced draws; without it every draw stays its own.
 */
internal class CompileFrame(instancing: Boolean) {
    private val pipelines = List(PIPELINES) { object : PipelineHandle {} }
    private val handle = object : MaterialBinding, BufferHandle {}
    private val meshes = List(MESHES) {
        object : Mesh {
            override val format = VertexFormat.PositionNormalColor
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    /** One resolved draw per source index, made once. */
    val resolved = List(DRAWS) { index ->
        GpuResolvedDraw(
            pipeline = pipelines[index % PIPELINES],
            materialBinding = handle,
            vertexBuffer = handle,
            indexBuffer = null,
            elementCount = 3,
            batchKey = 1_000 + (index * 7919) % 100_003,
            transparent = index % 10 == 0,
            depthSortKey = (index % 13).toFloat(),
        )
    }

    /** Hands back [resolved]; instances unculled copies when the frame was built with instancing. */
    val preparer: GpuDrawPreparer = if (instancing) {
        object : GpuDrawPreparer {
            override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) =
                resolved[sourceIndex % resolved.size]

            override fun canInstance(format: VertexFormat): Boolean = true
        }
    } else {
        // An object, not a SAM-converted lambda: on Kotlin/Wasm each call through a lambda converted
        // to a fun interface allocated 28 B, which would be measured here as the compile's own cost.
        object : GpuDrawPreparer {
            override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) =
                resolved[sourceIndex % resolved.size]
        }
    }

    /** The same preparer as a lambda converted to the fun interface, to measure what that conversion costs a call. */
    val lambdaPreparer = GpuDrawPreparer { _, sourceIndex, _ -> resolved[sourceIndex % resolved.size] }

    /** The frame's draws. */
    val requests = List(DRAWS) { index ->
        val x = (index % ROW - ROW / 2) * SPACING
        val z = (index / ROW - ROW / 2) * SPACING
        RenderDrawCommand(
            meshes[index % MESHES],
            material,
            model = Mat4().translate(x, 0f, z),
            worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
            transparent = index % 10 == 0,
        )
    }

    /** The camera, moved a step by each [moved]. */
    val lens = Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
    private val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))

    /** The sun with its cascades. */
    val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, ClipSpace.WebGpu))

    /** The frame's environment. */
    val environment = EnvironmentUniforms.Default
    private var step = 0

    /** [lens], moved a little, so no compile is handed back unchanged. */
    fun moved(): Lens = lens.also { it.eye = Vec3f(step++ * 0.01f, 30f, 60f) }

    /** Compiles the frame with the camera moved, through [scratch], counting into [stats] when given. */
    fun compile(scratch: ScenePassScratch, stats: SceneDrawStats?) = ScenePassCompiler.compile(
        moved(),
        requests,
        light,
        environment,
        ClipSpace.WebGpu,
        1f,
        null,
        preparer,
        emptyList(),
        ImagePassChain.Empty,
        stats,
        scratch,
    )

    /** The frame's shape. */
    companion object {
        /** Draws a frame holds. */
        const val DRAWS = 3_000
        private const val MESHES = 190
        private const val PIPELINES = 8
        private const val ROW = 60
        private const val SPACING = 4f
    }
}
