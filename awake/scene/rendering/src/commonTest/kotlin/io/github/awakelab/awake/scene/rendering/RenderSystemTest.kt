/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.DEFAULT_SCENE_LIGHT
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.LineSegment
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.Light
import io.github.awakelab.awake.scene.rendering.RenderSystem
import io.github.awakelab.awake.scene.rendering.animation.ModularCharacterComponent
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose
import io.github.awakelab.awake.scene.rendering.debug.debugSettings
import io.github.awakelab.awake.scene.rendering.mesh.InstancedMeshRenderer
import io.github.awakelab.awake.scene.rendering.mesh.LodGroup
import io.github.awakelab.awake.scene.rendering.mesh.LodLevel
import io.github.awakelab.awake.scene.rendering.mesh.MeshBounds
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
import io.github.awakelab.awake.scene.rendering.particles.ParticleEmitter
import io.github.awakelab.awake.scene.rendering.particles.ParticleVisual
import io.github.awakelab.awake.scene.rendering.spatial.Occluder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** [io.github.awakelab.awake.scene.rendering.RenderSystem] doesn't shade anything itself -- it just resolves the scene's [io.github.awakelab.awake.scene.rendering.Light] entity
 * (or [io.github.awakelab.awake.render.renderer.DEFAULT_SCENE_LIGHT] when there isn't one) into the backend-neutral [io.github.awakelab.awake.render.renderer.SceneLight]
 * [io.github.awakelab.awake.render.renderer.Renderer.draw] expects, same "world state in, render-api call out" shape the mesh/camera
 * side already has. A recording fake [io.github.awakelab.awake.render.renderer.Renderer] (matching [Scene3DPlaygroundUiTest]'s own
 * `RecordingScene3DRenderer` shape) captures what actually reached [io.github.awakelab.awake.render.renderer.Renderer.draw] without
 * needing a real GPU backend. */
class RenderSystemTest {
    private class RecordingRenderer : Renderer {
        var lastLight: SceneLight? = null
        var lastDrawCalls: List<DrawCall> = emptyList()
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false
        override var shadowsEnabled: Boolean = true
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

        override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) {
            lastLight = light
            lastDrawCalls = drawCalls
        }

        override fun renderToTexture(
            target: RenderTarget,
            camera: Lens,
            drawCalls: List<DrawCall>,
            light: SceneLight,
        ) = Unit

        override suspend fun readPixels(target: RenderTarget): TextureAsset =
            error("not needed for this test")

        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit
    }

    private fun worldWithPrimaryCamera(): World {
        val world = World()
        val cameraEntity = world.create()
        world.add(
            cameraEntity,
            io.github.awakelab.awake.scene.rendering.Camera(
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

    @Test
    fun usesDefaultSceneLightWhenNoLightEntityExists() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

        // Compared field by field rather than against DEFAULT_SCENE_LIGHT whole: the light this
        // system emits also carries a viewProjection, which the constant cannot.
        assertEquals(DEFAULT_SCENE_LIGHT.direction, renderer.lastLight?.direction)
        assertEquals(DEFAULT_SCENE_LIGHT.color, renderer.lastLight?.color)
    }

    @Test
    fun fillsInTheLightsOwnViewProjectionSoNoBackendHasToBuildOne() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

        // A backend renders depth from this matrix and never derives one -- null here is a scene
        // that silently cannot cast, which is how it fails if this system stops supplying it.
        assertNotNull(renderer.lastLight?.viewProjection)
    }

    @Test
    fun omitsTheViewProjectionWhenShadowsAreOff() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer().apply { shadowsEnabled = false }

        RenderSystem(renderer).update(world, 1f / 60f)

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
        val world = worldWithPrimaryCamera()
        world.debugSettings().cascadedShadows = false
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

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
    fun cascadesAreFittedUnlessTheToggleSaysOtherwise() {
        val world = worldWithPrimaryCamera()
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

        assertNotNull(renderer.lastLight?.cascades, "Cascades are the default, not the opt-in.")
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

        RenderSystem(renderer).update(world, 1f / 60f)

        val light = renderer.lastLight
        assertEquals(Vec3f(1f, 0f, 0f), light?.direction)
        assertEquals(Vec3f(2f, 1f, 0.5f), light?.color)
    }

    @Test
    fun stillCallsDrawWithNoContentWhenThereIsNoPrimaryCamera() {
        // A UI-only scene (no camera, no 3D content) still needs its frame presented: `draw()`
        // is the only call that acquires/submits/presents the swapchain, and `drawUi()` alone
        // never reaches the screen. See RenderSystem.update's no-camera branch.
        val world = World()
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

        assertNotNull(renderer.lastLight)
        assertEquals(emptyList(), renderer.lastDrawCalls)
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

        RenderSystem(renderer).update(world, 1f / 60f)

        val drawCall = renderer.lastDrawCalls.single()
        assertSame(mesh, drawCall.mesh)
        assertSame(material, drawCall.material)
        assertEquals(transforms, drawCall.instanceModels)
    }

    @Test
    fun meshRendererWithoutBoundsAlwaysDraws() {
        val world = worldWithPrimaryCamera()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        val renderer = RecordingRenderer()

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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
        val system = RenderSystem(renderer)

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
        val system = RenderSystem(renderer)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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
        val system = RenderSystem(renderer)

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

        RenderSystem(renderer).update(world, 1f / 60f)

        // Only the in-view emitter's particle produced a DrawCall -- the far one's single
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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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

        RenderSystem(renderer).update(world, 1f / 60f)

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
