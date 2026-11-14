/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.directionalShadowBox
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opaque instanced casters of different meshes share one depth group on both backends (#1555):
 * a depth pass binds about one group rather than one per mesh, and they cast what they cast with a
 * group each.
 *
 * Before, each instanced draw's depth group was its own: three meshes, three groups, in every
 * cascade. The separate frame gives each mesh a different wavelength and no amplitude, so nothing
 * sways but no two read alike, and the shadows must come out the same.
 */
class SharedDepthGroupParityTest {

    @Test
    fun instancedCastersOfDifferentMeshesShareOneDepthGroupAndCastTheSame() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = openSession(backend).renderer
            val shared = renderer.frame(POST_HALVES.map { STILL })
            val separate = renderer.frame(POST_HALVES.indices.map { Vec3f(0f, 1f + it, 0f) })

            assertEquals(POST_HALVES.size, shared.casters, "$backend: every mesh is one instanced caster")
            assertEquals(1, shared.groups, "$backend: still casters bind one depth group between them")
            assertEquals(POST_HALVES.size, separate.groups, "$backend: casters that read differently keep a group each")
            assertEquals(0, shared.pixels.differingChannels(separate.pixels), "$backend: shared groups cast what a group each does")
        }
    }

    @Test
    fun aSwayingCasterKeepsItsOwnGroup() {
        BACKEND_ORDER.forEach { backend ->
            val frame = openSession(backend).renderer.frame(listOf(STILL, STILL, Vec3f(0.3f, 2f, 1f)))
            assertEquals(2, frame.groups, "$backend: the still posts share one, the swaying one has its own")
        }
    }

    private class Frame(val casters: Int, val groups: Int, val pixels: ByteArray)

    /** The renderer's preparer, keeping each draw it resolves. */
    private class RecordingPreparer(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        val resolved = mutableListOf<GpuResolvedDraw>()

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? =
            delegate.prepare(request, sourceIndex, context)?.also { resolved += it }
    }

    /** Three posts of different sizes, two copies each, over the ground, each post animated as [animations] says. */
    private fun Renderer.frame(animations: List<Vec3f>): Frame {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val ground = createMesh(plane(GROUND_HALF, y = 0f))
        val groundMaterial = createMaterial(LitShadowUniformLayout)
        val posts = POST_HALVES.map { createMesh(texturedPlane(it, y = CASTER_Y)) }
        val material = texturedMaterial(SolidOrange)
        val preparer = RecordingPreparer((this as GpuDrawPreparationSource).gpuDrawPreparer!!)
        return try {
            val draws = listOf(RenderDrawCommand(ground, groundMaterial)) + posts.mapIndexed { index, post ->
                val x = (index - 1) * POST_SPACING
                RenderDrawCommand(
                    post,
                    material,
                    vertexAnimation = animations[index],
                    timeSeconds = TIME,
                    instanceModels = listOf(Mat4().translate(x, 0f, -1f), Mat4().translate(x, 0f, 1f)),
                )
            }
            val input = ScenePassCompiler.compile(
                lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                drawCalls = draws,
                light = SceneLight(direction = SUN, color = Vec3f(1f, 1f, 1f)).let {
                    it.copy(viewProjection = directionalShadowBox(it.direction, clipSpace).viewProjection)
                },
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = preparer,
            )
            renderToTexture(target, input)
            val casters = preparer.resolved.filter { it.depthRenderKey?.kind == DepthCasterKind.Instanced }
            val groups = casters.mapNotNull { it.depthMaterialBinding }.distinctBy { System.identityHashCode(it) }.size
            Frame(casters.size, groups, runBlocking { readPixels(target) }.data)
        } finally {
            ground.destroy()
            groundMaterial.destroy()
            posts.forEach { it.destroy() }
            material.destroy()
            target.destroy()
        }
    }

    private fun ByteArray.differingChannels(other: ByteArray): Int =
        indices.count { kotlin.math.abs((this[it].toInt() and 0xFF) - (other[it].toInt() and 0xFF)) > 0 }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun openSession(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        val STILL = Vec3f(0f, 0f, 0f)
        val SUN = Vec3f(2f, 1f, 0f)
        val POST_HALVES = listOf(0.4f, 0.55f, 0.7f)
        const val POST_SPACING = 2.5f
        const val TIME = 1.5f
    }
}
