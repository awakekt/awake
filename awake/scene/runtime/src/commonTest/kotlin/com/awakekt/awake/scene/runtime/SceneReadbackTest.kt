/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.particles.TransformPlacement
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A readback draws the scene the frame draws, not a simplified copy of it. */
class SceneReadbackTest {
    /** One pass as the renderer received it. Copied on arrival: extraction reuses its buffers. */
    private data class Pass(val draws: List<Drawn>, val light: List<Float>, val shadowViews: List<List<Float>>)

    private data class Drawn(
        val mesh: Any,
        val material: Any,
        val model: List<Float>,
        val extras: List<Float>,
        val instances: List<List<Float>>?,
        val instanceColors: List<List<Float>>?,
        val transparent: Boolean,
        val additive: Boolean,
        val otherState: List<Any?>,
    ) {
        constructor(request: GpuDrawRequest) : this(
            mesh = request.mesh,
            material = request.material,
            model = request.model.data.toList(),
            extras = request.extraUniformFloats.toList(),
            instances = request.instanceModels?.map { it.data.toList() },
            instanceColors = request.instanceColors?.map { listOf(it.x, it.y, it.z, it.w) },
            transparent = request.transparent,
            additive = request.additive,
            otherState = listOf(
                request.cullMode,
                request.alphaMode,
                request.alphaCutoff,
                request.shadowsOnly,
                request.timeSeconds,
                request.instanceFrames?.toList(),
                request.instanceJointPalettes?.map { it.toList() },
            ),
        )
    }

    private class RecordingRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val surfaceAspect: Float = 2f
        val frames = ArrayList<Pass>()
        val captures = ArrayList<Pass>()
        private var pending = ArrayList<Drawn>()

        override val gpuDrawPreparer = GpuDrawPreparer { request, _, _ ->
            pending += Drawn(request)
            null
        }

        override fun draw(input: GpuPassInput) {
            frames += take(input)
        }

        override fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
            captures += take(input)
        }

        private fun take(input: GpuPassInput) = Pass(
            draws = pending,
            light = input.passUniforms.toList(),
            shadowViews = input.prePasses.map { it.viewProjection.data.toList() },
        ).also { pending = ArrayList() }
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun aReadbackDrawsWhatTheFrameDraws() = runTest {
        val renderer = RecordingRenderer()
        val runtime = SceneAppLifecycleRuntime(
            SceneAppSpec(
                sceneName = null,
                systems = emptyList(),
                scenePopulationBlock = { populate() },
                renderableFactory = { error("no renderable requested in this test") },
                assetLibraryFactory = null,
                updateBlock = { _, _ -> },
                ui = null,
                onReadyBlock = {},
                onDisposeBlock = {},
                serviceRegistrations = emptyList(),
            ),
        )
        runtime.initialize(NoServices)

        // Ready runs one frame through the scene's RenderSystem3D, the path the window shows.
        runtime.ready(renderer)
        runtime.readback(lens, width = 200, height = 100)

        val frame = renderer.frames.single()
        assertTrue(frame.draws.any { !it.instances.isNullOrEmpty() }, "The frame draws the particles.")
        assertTrue(frame.draws.any { it.instances == null && it.extras.isNotEmpty() }, "The frame draws the tinted cube.")
        assertTrue(frame.draws.any { it.transparent && it.additive }, "The frame draws the additive glass.")
        assertEquals(frame, renderer.captures.single())
    }

    /**
     * Another world, such as the one an editor plays a scene in, draws its own entities from its own
     * camera, and the runtime's own capture afterwards is the one it always was.
     */
    @Test
    fun aReadbackOfAnotherWorldDrawsThatWorldFromItsCamera() = runTest {
        val renderer = RecordingRenderer()
        val runtime = runtime()
        runtime.ready(renderer)
        val boat = FakeMesh(VertexFormat.PositionNormalColor)
        val played = World().apply {
            add(create(), Camera(Lens(eye = Vec3f(0f, 4f, 9f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f)))
            create().also {
                add(it, Transform(position = Vec3f(3f, 0f, 0f)))
                add(it, MeshRenderer(boat, FakeMaterial))
            }
        }
        TransformSystem().update(played, 0f)

        runtime.readbackAttachment(played, width = 200, height = 100, attachment = FramebufferAttachment.Color0)
        runtime.readback(lens, width = 200, height = 100)

        val (other, own) = renderer.captures
        assertEquals(listOf(boat), other.draws.map { it.mesh }, "The other world's one entity, and nothing of the runtime's.")
        assertEquals(3f, other.draws.single().model[TRANSLATION_X], "At its own transform.")
        assertTrue(other.light != own.light, "From its own camera, which sets the pass's view.")
        assertTrue(own.draws.none { it.mesh === boat }, "The runtime's own capture after it draws only its own world.")
        assertEquals(renderer.frames.single(), own)
    }

    @Test
    fun aWorldWithNoCameraHasNothingToCapture() = runTest {
        val renderer = RecordingRenderer()
        val runtime = runtime()
        runtime.ready(renderer)

        val data = runtime.readbackAttachment(World(), width = 20, height = 10, attachment = FramebufferAttachment.Color0)

        assertTrue(!data.available, "unavailable, with a reason: ${data.reason}")
        assertTrue(renderer.captures.isEmpty(), "Nothing was drawn.")
    }

    private fun runtime() = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = { populate() },
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
        ),
    ).also { it.initialize(NoServices) }

    private fun SceneAppLifecycleRuntime.populate() {
        val cube = FakeMesh(VertexFormat.PositionNormalColor)
        world.add(world.create(), Camera(lens))
        world.add(world.create(), Light(color = Vec3f(1f, 0.5f, 0.25f)))
        world.create().also {
            world.add(it, Transform())
            world.add(it, MeshRenderer(cube, FakeMaterial))
            world.add(it, PbrMaterial(baseColorFactor = Color(1f, 0.2f, 0.2f, 1f)))
        }
        world.create().also {
            world.add(it, Transform(position = Vec3f(0f, 0f, 1f)))
            world.add(it, MeshRenderer(cube, FakeMaterial, transparent = true, additive = true))
        }
        val sparks = ParticleEmitter(
            mesh = FakeMesh(VertexFormat.PositionUv),
            material = FakeMaterial,
            origin = Vec3f.ZERO,
            maxParticles = 4,
            spawnRate = 100f,
            lifetime = 5f,
            startAlpha = 1f,
            scale = 0.2f,
            visual = ParticleVisual(additive = true),
        )
        world.add(world.create(), sparks)
        ParticleSystem(TransformPlacement).update(world, PARTICLE_SPAWN_SECONDS)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        const val PARTICLE_SPAWN_SECONDS = 0.05f

        /** A column-major model matrix's x translation. */
        const val TRANSLATION_X = 12
    }
}
