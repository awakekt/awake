/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A view plans the shadow faces of only the point lights that can light what it sees (#1621).
 *
 * A canvas view's portrait, an orthographic camera far above the world framing one subject, drew
 * every shadowed lamp's six faces over the casters around it: 78 draws for one lamp in the
 * reproduction. A point light's falloff reaches zero at its range, so one whose range misses the
 * view's frustum changes none of its pixels.
 */
class ViewPointLightCullingTest {
    private var mainPass: GpuPassInput? = null
    private val renderer = object : Renderer by NoopRenderer() {
        override fun draw(input: GpuPassInput) {
            mainPass = input
        }
    }
    private val world = World()
    private val handle = object : PipelineHandle, MaterialBinding, BufferHandle {}

    /** Resolves every draw to one stub packet: this test reads which passes are planned, not their draws' resources. */
    private val preparer = object : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext) =
            GpuResolvedDraw(pipeline = handle, materialBinding = handle, vertexBuffer = handle, indexBuffer = null, elementCount = 3)

        override fun canInstance(format: VertexFormat) = true

        override val redrawsPreparedFrames get() = false
    }
    private val system = RenderSystem3D(renderer, drawPreparer = preparer)
    private val transforms = TransformSystem()

    // Every pre-pass that is not one of the cascades the input says it drew (#1709).
    private fun pointFaces(pass: GpuPassInput) = pass.prePasses.size - (pass.shadowCascadeData?.count ?: 0)

    @Test
    fun aViewFarFromALampPlansNoneOfItsShadowFacesAndTheMainViewKeepsThem() {
        val mesh = renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor))
        val material = renderer.createMaterial()
        repeat(PROPS) { index ->
            val prop = world.create()
            world.add(prop, Transform(position = Vec3f((index % ROW - ROW / 2) * 4f, 0f, (index / ROW - 2) * 4f)))
            world.add(prop, MeshRenderer(mesh, material))
            world.add(prop, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        }
        val subject = world.create()
        world.add(subject, Transform(position = Vec3f(0f, HIGH, 0f)))
        world.add(subject, MeshRenderer(mesh, material))
        world.add(subject, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)))
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        val lamp = world.create()
        world.add(lamp, Transform(position = Vec3f(0f, 3f, 0f)))
        world.add(lamp, Light(type = Light.Type.Point, range = 25f))
        val portrait = Camera(
            Lens(eye = Vec3f(0f, HIGH, 5f), center = Vec3f(0f, HIGH, 0f), fovYRadians = 0.6f, near = 0.1f, far = 20f).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = 1.5f
            },
            isPrimary = false,
        )

        transforms.update(world, 1f / 60f)
        system.update(world, 1f / 60f)
        val main = checkNotNull(mainPass) { "the main view was drawn" }
        val mainFaces = pointFaces(main)
        val capture = system.planCapture(world, portrait, SIZE, SIZE)

        assertEquals(FACES, mainFaces, "the main view sees the lamp's reach and keeps its six faces")
        assertEquals(0, pointFaces(capture), "the portrait sees nothing the lamp reaches, so it plans none of its faces")
        assertTrue(capture.resolvedOpaqueDraws.isNotEmpty(), "the portrait still draws its subject")
    }

    private companion object {
        const val PROPS = 100
        const val ROW = 20
        const val HIGH = 20_000f
        const val SIZE = 64
        const val FACES = 6
    }
}
