/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.cos
import kotlin.math.sin

/**
 * The browser frame tests' scene (#1621): [PROPS] shadowed props over [MESHES] meshes in a square field,
 * the camera flying a loop low over it, so every frame is planned and drawn afresh.
 */
/** Copies of [MESHES] props in a square field, the camera flying a loop low over it. */
internal class MovingField(private val renderer: Renderer) {
    private val target = renderer.createRenderTarget(BundleBrowserEngine.SIZE, BundleBrowserEngine.SIZE)
    private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
    private val meshes = List(MESHES) { renderer.createMesh(box(0.3f + it * 0.002f)) }
    private val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
    private val scratch = ScenePassScratch()
    val defaultPreparer: GpuDrawPreparer? = (renderer as GpuDrawPreparationSource).gpuDrawPreparer
    private val draws = List(PROPS) { index ->
        val x = (index % ROW - ROW / 2) * SPACING
        val z = (index / ROW - ROW / 2) * SPACING
        RenderDrawCommand(
            meshes[index % MESHES],
            lit,
            model = Mat4().translate(x, 0f, z),
            worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
        )
    }

    /** Frame [index]'s pass, prepared by [preparer] into [scratch]. */
    fun compile(index: Int, preparer: GpuDrawPreparer? = defaultPreparer, scratch: ScenePassScratch = this.scratch): GpuPassInput {
        val angle = index * STEP
        val eye = Vec3f(sin(angle) * RADIUS, HEIGHT, cos(angle) * RADIUS)
        val lens = Lens(eye = eye, center = Vec3f(eye.x * 0.5f, 0f, eye.z * 0.5f - 10f), fovYRadians = 1f, near = 0.1f, far = 200f)
        val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
        return ScenePassCompiler.compile(
            lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
            ImagePassChain.Empty, null, scratch,
        )
    }

    /** Draws [pass] into the field's target. */
    fun draw(pass: GpuPassInput) = renderer.renderToTexture(target, pass)

    /** Frame [index], planned and drawn. */
    fun frame(index: Int) = draw(compile(index))

    companion object {
        const val PROPS = 3_000
        const val MESHES = 190
        const val ROW = 55
        const val SPACING = 2f
        const val RADIUS = 30f
        const val HEIGHT = 18f

        /** One loop in [WARM_UP] frames: warm-up visits every view the measured frames see. */
        const val WARM_UP = 60
        const val STEP = (2 * kotlin.math.PI / WARM_UP).toFloat()
        const val MEASURED = 30
        const val KB = 1024.0

        /** An upright box [half] wide: six quads, lit from every side. */
        fun box(half: Float): MeshGeometry {
            val vertices = ArrayList<Float>()
            val indices = ArrayList<Int>()
            val faces = listOf(
                Vec3f(1f, 0f, 0f),
                Vec3f(-1f, 0f, 0f),
                Vec3f(0f, 1f, 0f),
                Vec3f(0f, -1f, 0f),
                Vec3f(0f, 0f, 1f),
                Vec3f(0f, 0f, -1f),
            )
            for (normal in faces) {
                val base = vertices.size / FLOATS
                val (u, v) = axesOf(normal)
                for ((a, b) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    val p = Vec3f(
                        (normal.x + u.x * a + v.x * b) * half,
                        (normal.y + u.y * a + v.y * b) * half + half,
                        (normal.z + u.z * a + v.z * b) * half,
                    )
                    vertices += listOf(p.x, p.y, p.z, normal.x, normal.y, normal.z, 0.7f, 0.6f, 0.5f)
                }
                indices += listOf(base, base + 1, base + 2, base + 2, base + 3, base)
            }
            return MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), VertexFormat.PositionNormalColor)
        }

        const val FLOATS = 9

        fun axesOf(normal: Vec3f): Pair<Vec3f, Vec3f> = when {
            normal.x != 0f -> Vec3f(0f, 1f, 0f) to Vec3f(0f, 0f, 1f)
            normal.y != 0f -> Vec3f(1f, 0f, 0f) to Vec3f(0f, 0f, 1f)
            else -> Vec3f(1f, 0f, 0f) to Vec3f(0f, 1f, 0f)
        }
    }
}

/** Whether the page can collect garbage on demand and read its heap to the byte. */
@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
internal external fun heapMeasurable(): Boolean

/** The JS heap's size in bytes after a full collection. */
@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
internal external fun collectedHeap(): Double

/** The JS heap's size in bytes now. */
@JsFun("() => performance.memory.usedJSHeapSize")
internal external fun heapNow(): Double
