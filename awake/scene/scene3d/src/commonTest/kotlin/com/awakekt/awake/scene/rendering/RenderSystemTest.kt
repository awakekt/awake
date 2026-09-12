/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.debug.debugSettings
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.particles.ParticleEmitter
import com.awakekt.awake.scene.rendering.particles.ParticleVisual
import com.awakekt.awake.scene.rendering.spatial.Occluder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** [com.awakekt.awake.scene.rendering.RenderSystem3D] doesn't shade anything itself -- it just resolves the scene's [com.awakekt.awake.scene.rendering.Light] entity
 * (or [com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT] when there isn't one) into the backend-neutral [com.awakekt.awake.render.passes.uniforms.SceneLight]
 * [com.awakekt.awake.render.renderer.Renderer.draw] expects, same "world state in, render-api call out" shape the mesh/camera
 * side already has. A recording fake [com.awakekt.awake.render.renderer.Renderer] (matching [Scene3DPlaygroundUiTest]'s own
 * `RecordingScene3DRenderer` shape) captures what actually reached [com.awakekt.awake.render.renderer.Renderer.draw] without
 * needing a real GPU backend. */
class RenderSystemTest {
    private class RecordingRenderer :
        Renderer,
        GpuDrawPreparationSource {
        var lastLight: SceneLight? = null
        var lastEnvironment: com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms? = null
        var lastViewProjection: Mat4? = null
        var lastDrawCalls: List<RenderDrawCommand> = emptyList()
        var sceneSubmissionCount: Int = 0
        var gpuPassSubmissionCount: Int = 0

        override val gpuDrawPreparer = GpuDrawPreparer { command, sourceIndex, _ ->
            if (sourceIndex == 0) lastDrawCalls = emptyList()
            lastDrawCalls += command
            null
        }

        val submissionCount: Int
            get() = sceneSubmissionCount + gpuPassSubmissionCount

        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override val surfaceAspect: Float = 2f
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false
        override fun createMesh(geometry: MeshGeometry): Mesh = error("not needed for this test")
        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material =
            error("not needed for this test")

        override fun createRenderTarget(width: Int, height: Int): RenderTarget =
            error("not needed for this test")

        override fun draw(input: GpuPassInput) {
            if (input == GpuPassInput.EMPTY) {
                gpuPassSubmissionCount++
                return
            }
            sceneSubmissionCount++
            lastViewProjection = input.viewProjection
            val directional = input.passUniforms
            val cascades = input.prePasses.map { it.viewProjection }
            val points = pointLightsFrom(directional)
            lastLight = SceneLight(
                direction = Vec3f(directional.getOrElse(0) { 0f }, directional.getOrElse(1) { 0f }, directional.getOrElse(2) { 0f }),
                color = Vec3f(directional.getOrElse(4) { 1f }, directional.getOrElse(5) { 1f }, directional.getOrElse(6) { 1f }),
                points = points,
                viewProjection = cascades.firstOrNull(),
                cascades = cascades.takeIf { it.size > 1 }?.let {
                    ShadowCascadeUniforms(it, FloatArray(it.size) { Float.MAX_VALUE })
                },
            )
            lastEnvironment = com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms(
                shadowsEnabled = input.prePasses.isNotEmpty(),
            )
        }

        override suspend fun readPixels(target: RenderTarget): TextureAsset =
            error("not needed for this test")

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit

        private fun pointLightsFrom(uniforms: FloatArray): List<com.awakekt.awake.render.passes.uniforms.PointLight> {
            val positionsStart = 8
            val colorsStart = positionsStart + 16
            return (0 until 4).mapNotNull { slot ->
                val p = positionsStart + slot * 4
                val range = uniforms.getOrElse(p + 3) { 0f }
                if (range <= 0f) return@mapNotNull null
                val c = colorsStart + slot * 4
                com.awakekt.awake.render.passes.uniforms.PointLight(
                    position = Vec3f(uniforms[p], uniforms[p + 1], uniforms[p + 2]),
                    color = Vec3f(uniforms.getOrElse(c) { 0f }, uniforms.getOrElse(c + 1) { 0f }, uniforms.getOrElse(c + 2) { 0f }),
                    range = range,
                )
            }
        }
    }

    private fun worldWithPrimaryCamera(): World {
        val world = World()
        val cameraEntity = world.create()
        world.add(
            cameraEntity,
            com.awakekt.awake.scene.rendering.Camera(
                Lens(
                    eye = Vec3f(
                        0f,
                        0f,
                        5f,
                    ),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 100f,
                ),
            ),
        )
        return world
    }

    private fun worldWithPrimaryDirectionalLight(): World = worldWithPrimaryCamera().also { world ->
        val lightEntity = world.create()
        world.add(lightEntity, Light(type = Light.Type.Directional))
    }

    @Test
    fun usesDefaultSceneLightWhenNoLightEntityExists() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        // Compared field by field rather than against DEFAULT_SCENE_LIGHT whole: the light this
        // system emits also carries a viewProjection, which the constant cannot.
        assertEquals(DEFAULT_SCENE_LIGHT.direction, renderer.lastLight?.direction)
        assertEquals(DEFAULT_SCENE_LIGHT.color, renderer.lastLight?.color)
    }

    @Test
    fun sceneProjectionUsesSurfaceAspectInsteadOfConservativeCullingAspect() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val expected = Lens(
            eye = Vec3f(0f, 0f, 5f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 100f,
        ).viewProjectionMatrix(2f, renderer.clipSpace)
        assertContentEquals(expected.data, requireNotNull(renderer.lastViewProjection).data)
    }

    @Test
    fun fillsInTheLightsOwnViewProjectionSoNoBackendHasToBuildOne() {
        val world = worldWithPrimaryDirectionalLight()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        // A backend renders depth from this matrix and never derives one -- null here is a scene
        // that silently cannot cast, which is how it fails if this system stops supplying it.
        assertNotNull(renderer.lastLight?.viewProjection)
    }

    @Test
    fun omitsTheViewProjectionWhenShadowsAreOff() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        // Not just unused -- not built. Building it costs a matrix per frame, and a renderer with
        // shadows off never reads it.
        assertNull(renderer.lastLight?.viewProjection)
    }

    /**
     * The debug toggle really switches the fit, rather than only the wireframe drawn over it.
     *
     * "Shadow cascades" in a debug panel used to draw cascade outlines; there was no way to turn
     * the cascades themselves OFF and see the single fixed box for comparison, which is the only
     * way to see what the cascaded fit is doing to a scene rather than read that it is on.
     */
    @Test
    fun theDebugToggleFallsBackToTheSingleShadowBox() {
        val world = worldWithPrimaryDirectionalLight()
        world.debugSettings().cascadedShadows = false
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertNull(
            renderer.lastLight?.cascades,
            "Cascades were still fitted with the toggle off, so the comparison shows the same " +
                "picture twice.",
        )
        assertNotNull(
            renderer.lastLight?.viewProjection,
            "The fallback still has to supply a matrix -- without one the scene casts nothing " +
                "at all, which is not what the old single box did.",
        )
    }

    @Test
    fun directionalLightDerivesDirectionFromTransformRotation() {
        val world = worldWithPrimaryCamera()
        val lightEntity = world.create()
        val pitch = (kotlin.math.PI / 4.0).toFloat()
        world.add(lightEntity, Transform(rotation = Vec3f(pitch, 0f, 0f)))
        world.add(lightEntity, Light(type = Light.Type.Directional, color = Vec3f(1f, 1f, 1f), intensity = 1f))
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val light = renderer.lastLight
        assertNotNull(light)
        assertEquals(0f, light.direction.x, 0.001f)
        assertEquals(-kotlin.math.sin(pitch), light.direction.y, 0.001f)
        assertEquals(kotlin.math.cos(pitch), light.direction.z, 0.001f)
    }

    @Test
    fun cascadesAreFittedUnlessTheToggleSaysOtherwise() {
        val world = worldWithPrimaryDirectionalLight()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertNotNull(renderer.lastLight?.cascades, "Cascades are the default, not the opt-in.")
    }

    @Test
    fun pointLightOnlyScenesDoNotInventDirectionalShadows() {
        val world = worldWithPrimaryCamera()
        val lightEntity = world.create()
        world.add(lightEntity, Transform(position = Vec3f(0f, 3f, 0f)))
        world.add(
            lightEntity,
            Light(type = Light.Type.Point, color = Vec3f(1f, 0.5f, 0.25f), intensity = 2f),
        )
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val light = renderer.lastLight
        assertNotNull(light)
        assertEquals(Vec3f.ZERO, light.color)
        assertEquals(1, light.points.size)
        assertNull(light.viewProjection, "Point lights must not enter the directional shadow fit.")
        assertNull(light.cascades, "Point lights do not have a cube-face shadow pass yet.")
        assertEquals(false, renderer.lastEnvironment?.shadowsEnabled)
    }

    @Test
    fun convertsTheSceneLightEntityIntoDirectionAndIntensityMultipliedColor() {
        val world = worldWithPrimaryCamera()
        val lightEntity = world.create()
        world.add(
            lightEntity,
            Light(color = Vec3f(1f, 0.5f, 0.25f), intensity = 2f, direction = Vec3f(1f, 0f, 0f)),
        )
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val light = renderer.lastLight
        assertEquals(Vec3f(1f, 0f, 0f), light?.direction)
        assertEquals(Vec3f(2f, 1f, 0.5f), light?.color)
    }

    @Test
    fun stillCallsDrawWithNoContentWhenThereIsNoPrimaryCamera() {
        // A UI-only scene (no camera, no 3D content) still needs its frame presented: `draw()`
        // is the only call that acquires/submits/presents the swapchain, and `drawUi()` alone
        // never reaches the screen. See RenderSystem3D.update's no-camera branch.
        val world = World()
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(1, renderer.submissionCount)
        assertEquals(1, renderer.gpuPassSubmissionCount)
        assertEquals(0, renderer.sceneSubmissionCount)
    }

    @Test
    fun submitsExactlyOneRenderPathPerUpdateFor300Frames() {
        // This is intentionally longer than a frames-in-flight ring. Before 2eb616490,
        // RenderSystem3D submitted both a GpuPassInput on every update;
        // real renderers executed both paths, which could present the same scene twice. Count
        // both entrypoints so a fake cannot hide that regression behind a no-op HAL method.
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)
        val frameCount = 300

        repeat(frameCount) {
            system.update(world, 1f / 60f)
        }

        assertEquals(frameCount, renderer.submissionCount)
        assertEquals(0, renderer.gpuPassSubmissionCount)
        assertEquals(frameCount, renderer.sceneSubmissionCount)
    }

    @Test
    fun instancedMeshRendererProducesOneDrawCallCarryingEveryTransform() {
        val world = worldWithPrimaryCamera()
        val mesh = fakeMesh()
        val material = fakeMaterial()
        val transforms = listOf(Mat4(), Mat4().apply { m03 = 5f })
        val entity = world.create()
        world.add(entity, InstancedMeshRenderer(mesh, material, transforms))
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val drawCall = renderer.lastDrawCalls.single()
        assertSame(mesh, drawCall.mesh)
        assertSame(material, drawCall.material)
        assertEquals(transforms, drawCall.instanceModels)
    }

    @Test
    fun featureContributesDrawsWithoutReceivingTheRenderer() {
        val world = worldWithPrimaryCamera()
        val mesh = fakeMesh()
        val material = fakeMaterial()
        val feature = object : RenderFeature3D {
            override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution =
                RenderContribution(
                    draws = listOf(RenderDrawCommand(mesh, material, model = Mat4().translate(2f, 0f, 0f))),
                )
        }
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer, features = listOf(feature)).update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
        assertSame(mesh, renderer.lastDrawCalls.single().mesh)
    }

    @Test
    fun featureLightingContributionOverridesBuiltInSceneFallback() {
        val world = worldWithPrimaryCamera()
        val featureLight = SceneLight(direction = Vec3f(1f, 0f, 0f), color = Vec3f(0.2f, 0.3f, 0.4f))
        val feature = object : RenderFeature3D {
            override fun collect(world: World, context: RenderFeatureContext3D): RenderContribution =
                RenderContribution(light = featureLight)
        }
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer, features = listOf(feature)).update(world, 1f / 60f)

        assertEquals(featureLight.direction, renderer.lastLight?.direction)
        assertEquals(featureLight.color, renderer.lastLight?.color)
    }

    @Test
    fun meshRendererWithoutBoundsAlwaysDraws() {
        val world = worldWithPrimaryCamera()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
    }

    @Test
    fun meshRendererInsideTheFrustumDraws() {
        val world = worldWithPrimaryCamera()
        val entity = world.create()
        // Lens eye=(0,0,5) looks at the origin -- a unit box sitting at the origin is
        // squarely in front of it.
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
    }

    @Test
    fun meshRendererBehindTheCameraIsCulled() {
        val world = worldWithPrimaryCamera()
        val entity = world.create()
        // Lens eye=(0,0,5) looks toward -z (at the origin) -- z=20 is behind the eye,
        // outside the frustum entirely.
        world.add(entity, Transform(worldMatrix = Mat4().translate(0f, 0f, 20f)))
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(0, renderer.lastDrawCalls.size)
    }

    @Test
    fun lodGroupPicksTheNearestLevelWhoseMaxDistanceCoversTheEntity() {
        val world = worldWithPrimaryCamera()
        val nearMesh = fakeMesh()
        val farMesh = fakeMesh()
        val entity = world.create()
        // Lens eye=(0,0,5), entity at the origin -- distance 5.
        world.add(entity, Transform())
        world.add(
            entity,
            LodGroup(
                listOf(
                    LodLevel(nearMesh, fakeMaterial(), maxDistance = 10f),
                    LodLevel(farMesh, fakeMaterial(), maxDistance = 1000f),
                ),
            ),
        )
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertSame(nearMesh, renderer.lastDrawCalls.single().mesh)
    }

    @Test
    fun lodGroupFallsBackToTheCoarsestLevelBeyondEveryThreshold() {
        val world = worldWithPrimaryCamera()
        val nearMesh = fakeMesh()
        val farMesh = fakeMesh()
        val entity = world.create()
        // Lens eye=(0,0,5) -- z=-995 is distance 1000, past both thresholds below.
        world.add(entity, Transform(worldMatrix = Mat4().translate(0f, 0f, -995f)))
        world.add(
            entity,
            LodGroup(
                listOf(
                    LodLevel(nearMesh, fakeMaterial(), maxDistance = 10f),
                    LodLevel(farMesh, fakeMaterial(), maxDistance = 100f),
                ),
            ),
        )
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        // LOD selects detail, it never culls -- the coarsest level still draws.
        assertSame(farMesh, renderer.lastDrawCalls.single().mesh)
    }

    @Test
    fun entityBehindAnOccluderIsExcludedFromDrawCalls() {
        val world = worldWithPrimaryCamera()
        val occluderEntity = world.create()
        world.add(occluderEntity, Transform())
        // Huge and centered between the eye (z=5) and the candidate (z=0) -- its screen rect
        // covers the whole viewport, guaranteeing containment regardless of exact projection.
        world.add(occluderEntity, Occluder(Aabb(Vec3f(-10f, -10f, 1.9f), Vec3f(10f, 10f, 2.1f))))
        val candidateEntity = world.create()
        world.add(candidateEntity, Transform())
        world.add(candidateEntity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(
            candidateEntity,
            MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))),
        )
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)

        system.update(world, 1f / 60f)

        assertEquals(0, renderer.lastDrawCalls.size)
        assertEquals(1, system.lastOccludedCount)
    }

    @Test
    fun entityNotCoveredByAnyOccluderStillDraws() {
        val world = worldWithPrimaryCamera()
        val occluderEntity = world.create()
        world.add(occluderEntity, Transform())
        // Off to the side -- doesn't cover the candidate sitting at the origin.
        world.add(occluderEntity, Occluder(Aabb(Vec3f(19f, -1f, 1.9f), Vec3f(21f, 1f, 2.1f))))
        val candidateEntity = world.create()
        world.add(candidateEntity, Transform())
        world.add(candidateEntity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(
            candidateEntity,
            MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))),
        )
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)

        system.update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
        assertEquals(0, system.lastOccludedCount)
    }

    @Test
    fun entityWithNoMeshBoundsIsNeverOccluded() {
        val world = worldWithPrimaryCamera()
        val occluderEntity = world.create()
        world.add(occluderEntity, Transform())
        world.add(occluderEntity, Occluder(Aabb(Vec3f(-10f, -10f, 1.9f), Vec3f(10f, 10f, 2.1f))))
        val candidateEntity = world.create()
        world.add(candidateEntity, Transform())
        world.add(candidateEntity, MeshRenderer(fakeMesh(), fakeMaterial()))
        // No MeshBounds -- same "opt-in, no bounds = always drawn" guarantee frustum culling
        // already has, occlusion must not add an implicit bounds requirement.
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
    }

    @Test
    fun noOccludersMeansOcclusionNeverRuns() {
        val world = worldWithPrimaryCamera()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)

        system.update(world, 1f / 60f)

        assertEquals(1, renderer.lastDrawCalls.size)
        assertEquals(0, system.lastOccludedCount)
    }

    @Test
    fun particlesFarOutsideTheFrustumAreExcludedFromTheDrawCall() {
        val world = worldWithPrimaryCamera() // eye (0,0,5), looking toward -Z (origin)
        val emitter = burstEmitterAt(Vec3f(0f, 0f, 0f)) // in view
        world.add(world.create(), emitter)
        val farEmitter = burstEmitterAt(Vec3f(5000f, 5000f, 5000f)) // well outside every plane
        world.add(world.create(), farEmitter)
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        // Only the in-view emitter's particle produced a RenderDrawCommand -- the far one's single
        // particle was frustum-culled, leaving it with zero live-and-visible instances.
        assertEquals(1, renderer.lastDrawCalls.size)
        assertEquals(1, renderer.lastDrawCalls[0].instanceModels?.size)
    }

    @Test
    fun visibleParticlesAreOrderedBackToFrontFromTheCameraEye() {
        val world = worldWithPrimaryCamera() // eye at z=5, looking toward -Z
        val emitter = ParticleEmitter(
            mesh = fakeMesh(),
            material = fakeMaterial(),
            origin = Vec3f(0f, 0f, 0f),
            maxParticles = 3,
            spawnRate = 0f,
            lifetime = 10f,
            startAlpha = 1f,
            scale = 0.1f,
        )
        // Manually place 3 already-alive particles at increasing distance from the eye (z=5) --
        // near (-1), mid (-3), far (-5) along the camera's forward axis.
        listOf(-1f, -3f, -5f).forEachIndexed { index, z ->
            emitter.particles[index].alive = true
            emitter.particles[index].position.set(0f, 0f, z)
            emitter.particles[index].lifetime = 10f
        }
        world.add(world.create(), emitter)
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val instanceModels = requireNotNull(renderer.lastDrawCalls[0].instanceModels)
        assertEquals(3, instanceModels.size)
        // Farthest (z=-5, distance 10 from eye) first, nearest (z=-1, distance 6) last --
        // painter's algorithm for correct alpha blending.
        assertEquals(-5f, instanceModels[0].m23)
        assertEquals(-3f, instanceModels[1].m23)
        assertEquals(-1f, instanceModels[2].m23)
    }

    /** One already-spawned, already-alive particle at [position] -- `spawnRate = 0f` so nothing
     * else spawns; a plain [ParticleEmitter] constructor call doesn't accept pre-alive
     * particles, so this reaches into the pool directly the same way the visible-ordering test
     * above does. */
    @Test
    fun stretchWithVelocityPacksAWorldSpaceStretchVectorIntoInstanceModelColumn1() {
        val world = worldWithPrimaryCamera()
        val emitter = ParticleEmitter(
            mesh = fakeMesh(), material = fakeMaterial(), origin = Vec3f(0f, 0f, 0f),
            maxParticles = 1, spawnRate = 0f, lifetime = 10f, startAlpha = 1f, scale = 0.1f,
            visual = ParticleVisual(stretchWithVelocity = true, stretchFactor = 0.5f),
        )
        emitter.particles[0].alive = true
        emitter.particles[0].position.set(0f, 0f, 0f)
        emitter.particles[0].velocity.set(2f, 0f, 0f)
        emitter.particles[0].lifetime = 10f
        world.add(world.create(), emitter)
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val model = requireNotNull(renderer.lastDrawCalls[0].instanceModels)[0]
        // stretchFactor(0.5) * velocity(2,0,0) = (1,0,0) -- packed into column 1 (m01/m11/m21).
        assertEquals(1f, model.m01, 1e-4f)
        assertEquals(0f, model.m11, 1e-4f)
        assertEquals(0f, model.m21, 1e-4f)
    }

    @Test
    fun stretchWithVelocityDisabledLeavesColumn1AtItsPlainZeroDefault() {
        val world = worldWithPrimaryCamera()
        val emitter = ParticleEmitter(
            mesh = fakeMesh(),
            material = fakeMaterial(),
            origin = Vec3f(0f, 0f, 0f),
            maxParticles = 1,
            spawnRate = 0f,
            lifetime = 10f,
            startAlpha = 1f,
            scale = 0.1f,
            // stretchWithVelocity defaults to false.
        )
        emitter.particles[0].alive = true
        emitter.particles[0].position.set(0f, 0f, 0f)
        emitter.particles[0].velocity.set(2f, 0f, 0f)
        emitter.particles[0].lifetime = 10f
        world.add(world.create(), emitter)
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, 1f / 60f)

        val model = requireNotNull(renderer.lastDrawCalls[0].instanceModels)[0]
        assertEquals(
            0f,
            model.m01,
            "no stretch must leave column 1 at its plain off-diagonal zero, byte-for-byte the old formula",
        )
    }

    private fun burstEmitterAt(position: Vec3f): ParticleEmitter {
        val emitter = ParticleEmitter(
            mesh = fakeMesh(),
            material = fakeMaterial(),
            origin = position,
            maxParticles = 1,
            spawnRate = 0f,
            lifetime = 10f,
            startAlpha = 1f,
            scale = 0.1f,
        )
        emitter.particles[0].alive = true
        emitter.particles[0].position.set(position.x, position.y, position.z)
        emitter.particles[0].lifetime = 10f
        return emitter
    }

    @Test
    fun modularCharacterEmitsDrawCallsForEquippedVisibleSlots() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        val charEntity = world.create()
        val character = ModularCharacterComponent(
            skin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4())),
        )
        val hairMesh = fakeMesh()
        val hairMat = fakeMaterial()
        character.equip("hair", hairMesh, hairMat)

        val chestMesh = fakeMesh()
        val chestMat = fakeMaterial()
        character.equip("chest", chestMesh, chestMat)

        world.add(charEntity, character)
        world.add(charEntity, Transform(position = Vec3f(0f, 0f, 0f)))
        val poseFloats = FloatArray(16) { 1f }
        world.add(charEntity, SkinnedPose(poseFloats))

        RenderSystem3D(renderer).update(world, 1f / 60f)

        assertEquals(2, renderer.lastDrawCalls.size)
        assertEquals(hairMesh, renderer.lastDrawCalls[0].mesh)
        assertEquals(chestMesh, renderer.lastDrawCalls[1].mesh)
        assertEquals(poseFloats, renderer.lastDrawCalls[0].extraUniformFloats)
        assertEquals(poseFloats, renderer.lastDrawCalls[1].extraUniformFloats)
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
