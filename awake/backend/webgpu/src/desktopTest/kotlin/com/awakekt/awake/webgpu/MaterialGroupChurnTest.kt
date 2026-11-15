/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A textured draw's material group is built once for its uniform slot and found again, frame
 * after frame, while a camera sweeps a field of textured props and a second scene is drawn each
 * frame beside it, as a canvas view draws one (#1621).
 *
 * Before, a prop that left the view and came back, or that the other scene's batch outlived, took
 * whichever slot was free and built a group for a buffer it had never had.
 */
class MaterialGroupChurnTest {

    @Test
    fun eachTexturedDrawBuildsOneGroupThroughASweepAndASecondScene() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
        val materials = List(MATERIALS) {
            renderer.createMaterial(texture = white, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null) as Material
        }
        // Every other prop untextured, through another pipeline whose uniform slots share the same numbers.
        val plain = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) as Material
        val field = List(PROPS) { index ->
            val x = (index % ROW - ROW / 2) * SPACING
            val z = (index / ROW - ROW / 2) * SPACING
            if (index % 2 == 0) prop(renderer.createMesh(quad()), materials[index / 2 % MATERIALS], x, z) else prop(renderer.createMesh(quad(uv = false)), plain, x, z)
        }
        val canvas = List(CANVAS) { index -> prop(renderer.createMesh(quad()), materials[index % MATERIALS], x = index * 0.3f, z = 0f) }
        val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        fun frame(index: Int, radius: Float, phase: Float) {
            val angle = index * STEP + phase
            val eye = Vec3f(sin(angle) * radius, 4f, cos(angle) * radius)
            val lens = Lens(eye = eye, center = Vec3f(eye.x * 0.3f, 0f, eye.z * 0.3f), fovYRadians = 0.8f, near = 0.1f, far = 30f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(lens = lens, drawCalls = field, light = light, clipSpace = renderer.clipSpace, aspect = 1f, drawPreparer = preparer),
            )
            renderer.renderToTexture(target, ScenePassCompiler.compile(lens = CANVAS_LENS, drawCalls = canvas, clipSpace = renderer.clipSpace, aspect = 1f, drawPreparer = preparer))
        }
        fun created() = materials.sumOf { it.drawGroupsCreated }
        fun depthGroups() = checkNotNull(renderer.depthPrePass) { "a shadowed scene has a depth pre-pass" }.depthGroupsCreated

        // Every prop is drawn once on the wide loop; the measured loop is a narrower one, out of
        // phase, so props leave and re-enter the view in an order the warm-up never saw.
        repeat(LOOP) { frame(it, radius = WIDE, phase = 0f) }
        val warm = created()
        val warmSlots = renderer.bufferPools.uniformSlotCount
        val warmDepth = depthGroups()
        repeat(LOOP) { frame(it, radius = NARROW, phase = 0.37f) }
        val slots = renderer.bufferPools.uniformSlotCount

        println("Material groups: $warm warming up, ${created() - warm} over a second loop of $LOOP frames; uniform slots $warmSlots, then $slots; depth groups $warmDepth, then ${depthGroups()}")
        assertEquals(warmSlots, slots, "the second loop creates no uniform slot")
        assertTrue(warmDepth > 0, "the casters' depth groups are built, or this proves nothing")
        assertEquals(warmDepth, depthGroups(), "the second loop builds no caster depth group")
        // Before, many more over these two loops for the same draws, as slots changed hands.
        assertEquals((PROPS / 2 + CANVAS).toLong(), created(), "one group per textured draw, built once")
        target.destroy()
    }

    private fun prop(mesh: com.awakekt.awake.render.mesh.GpuMesh, material: Material, x: Float, z: Float) = RenderDrawCommand(
        mesh = mesh,
        material = material,
        model = Mat4().translate(x, 0f, z),
        worldBounds = Aabb(Vec3f(x - 0.2f, -0.2f, z - 0.2f), Vec3f(x + 0.2f, 0.2f, z + 0.2f)),
    )

    private fun quad(uv: Boolean = true): MeshGeometry {
        val corners = listOf(-0.2f to -0.2f, 0.2f to -0.2f, 0.2f to 0.2f, -0.2f to 0.2f)
        val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f) + if (uv) listOf(0f, 0f) else emptyList() }
        val format = if (uv) VertexFormat.PositionNormalColorUv else VertexFormat.PositionNormalColor
        return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), format)
    }

    private companion object {
        const val SIZE = 32
        const val PROPS = 60
        const val MATERIALS = 6
        const val CANVAS = 4
        const val ROW = 10
        const val SPACING = 1.5f
        const val WIDE = 7f
        const val NARROW = 4.5f
        const val LOOP = 24
        const val STEP = (2 * kotlin.math.PI / LOOP).toFloat()
        val CANVAS_LENS = Lens(eye = Vec3f(0f, 3f, 3f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f)
    }
}
