/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * Opaque instanced casters of different meshes share one depth group on WebGPU (#1555), so a depth
 * pass binds about one group rather than one per mesh, and they cast what they cast with a group each.
 *
 * Before, each instanced draw's depth group was built from its own uniform buffer: three meshes,
 * three groups, in every cascade.
 */
class WebGpuSharedDepthGroupTest {

    @Test
    fun instancedCastersOfDifferentMeshesShareOneDepthGroup() = withScene { scene ->
        val frame = scene.frame(MESHES.map { NO_ANIMATION })
        assertEquals(MESHES.size, frame.instancedCasters, "every mesh is one instanced caster")
        assertEquals(1, frame.depthGroups, "and they bind one depth group between them")
    }

    @Test
    fun aCasterThatAnimatesItsVerticesKeepsItsOwnGroup() = withScene { scene ->
        val frame = scene.frame(listOf(NO_ANIMATION, NO_ANIMATION, Vec3f(0.3f, 2f, 1f)))
        assertEquals(2, frame.depthGroups, "the still meshes share one, the swaying one has its own")
    }

    @Test
    fun sharedGroupsCastTheSameShadowsAsAGroupEach() = withScene { scene ->
        val shared = scene.frame(MESHES.map { NO_ANIMATION })
        // No amplitude, so nothing moves, but a different wavelength each: no two read alike.
        val separate = scene.frame(MESHES.indices.map { Vec3f(0f, 1f + it, 0f) })
        assertEquals(MESHES.size, separate.depthGroups, "a group each")
        assertContentEquals(separate.pixels, shared.pixels, "the same shadows either way")
    }

    private fun <T> withScene(block: (Scene) -> T): T = webGpuHeadlessScene().use { session ->
        Scene(session.renderer).use(block)
    }

    private class Frame(val instancedCasters: Int, val depthGroups: Int, val pixels: ByteArray)

    /** The renderer's preparer, keeping each draw it resolves. */
    private class RecordingPreparer(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        val resolved = mutableListOf<GpuResolvedDraw>()

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? =
            delegate.prepare(request, sourceIndex, context)?.also { resolved += it }
    }

    private class Scene(val renderer: RenderRenderer) : AutoCloseable {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val ground = renderer.createMesh(quad(GROUND_HALF))
        private val props = MESHES.map { renderer.createMesh(texturedQuad(it)) }
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val textured = renderer.createMaterial(
            texture = WHITE,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val preparer = RecordingPreparer((renderer as GpuDrawPreparationSource).gpuDrawPreparer!!)

        fun frame(animations: List<Vec3f>): Frame {
            val lens = Lens(eye = Vec3f(0f, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val draws = listOf(RenderDrawCommand(ground, lit, model = Mat4(), timeSeconds = TIME)) +
                props.mapIndexed { index, prop ->
                    val x = (index - 1) * SPACING
                    RenderDrawCommand(
                        prop,
                        textured,
                        vertexAnimation = animations[index],
                        timeSeconds = TIME,
                        instanceModels = listOf(translation(x, PROP_HEIGHT, -1f), translation(x, PROP_HEIGHT, 1f)),
                    )
                }
            preparer.resolved.clear()
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty,
            )
            renderer.renderToTexture(target, pass)
            val casters = preparer.resolved.filter { it.depthRenderKey?.kind == DepthCasterKind.Instanced }
            val groups = casters.mapNotNull { it.depthMaterialBinding }.distinctBy { System.identityHashCode(it) }.size
            return Frame(casters.size, groups, runBlocking { renderer.readPixels(target) }.data)
        }

        override fun close() {
            ground.destroy()
            props.forEach { it.destroy() }
            lit.destroy()
            textured.destroy()
            target.destroy()
        }
    }

    private companion object {
        const val SIZE = 96
        const val GROUND_HALF = 6f
        const val PROP_HEIGHT = 1.5f
        const val SPACING = 2.5f
        const val TIME = 1.5f
        val NO_ANIMATION = Vec3f(0f, 0f, 0f)

        val WHITE = TextureAsset(data = ByteArray(4) { -1 }, width = 1, height = 1)

        /** Three meshes, a square each of its own size. */
        val MESHES = listOf(0.5f, 0.7f, 0.9f)

        fun translation(x: Float, y: Float, z: Float) = Mat4().apply {
            data[12] = x
            data[13] = y
            data[14] = z
        }

        /** A flat white square facing up. */
        fun quad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f) }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColor)
        }

        /** The same, textured: the headless scene instances the textured format. */
        fun texturedQuad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f, 0.5f, 0.5f) }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUv)
        }
    }
}
