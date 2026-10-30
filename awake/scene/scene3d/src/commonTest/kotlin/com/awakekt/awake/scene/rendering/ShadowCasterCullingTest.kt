/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.CharacterSlot
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.spatial.Occluder
import com.awakekt.awake.scene.rendering.spatial.SpatialIndexSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The camera decides what the scene pass draws; whether a mesh can shadow what the camera sees
 * decides what the shadow pass draws. A mesh that fails the first and passes the second is drawn
 * with `shadowsOnly`, and every test here reads those flags off the requests a renderer receives.
 */
class ShadowCasterCullingTest {

    @Test
    fun aCasterHiddenBehindAnOccluderStillCasts() {
        val world = worldWithCamera(light = Light(direction = Vec3f(0.3f, 1f, 0.2f)))
        world.spawnOccluder(z = WALL_Z)
        val caster = world.spawnProp(x = 0f, y = 1f, z = 0f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)

        system.update(world, DELTA)

        assertEquals(1, system.lastOccludedCount, "The wall must occlude the caster, or this proves nothing.")
        assertEquals(listOf(true), renderer.drawsOf(world, caster).map { it.shadowsOnly }, "Occluded from the camera, it still shades what the camera sees.")
        assertEquals(1, system.lastShadowOnlyCount)

        // Control: the same frame with shadows off draws nothing for it.
        world.turnShadowsOff()
        system.update(world, DELTA)
        assertEquals(emptyList(), renderer.drawsOf(world, caster), "With no shadow pass an occluded caster is culled, as before.")
    }

    @Test
    fun aCasterOutsideEveryCascadeEmitsNoDraw() {
        val world = worldWithCamera(light = Light(direction = Vec3f(0f, 1f, 0f)))
        // Both behind the camera, which looks down -z from z 10, so neither is in its view.
        val nearBehind = world.spawnProp(x = 0f, y = 1f, z = 12f)
        val farBehind = world.spawnProp(x = 0f, y = 1f, z = 500f)
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, DELTA)

        assertEquals(listOf(true), renderer.drawsOf(world, nearBehind).map { it.shadowsOnly }, "Inside the near cascade, so it casts -- the control for the next line.")
        assertEquals(emptyList(), renderer.drawsOf(world, farBehind), "400 units past the shadow distance, no cascade reaches it.")
    }

    @Test
    fun aSceneWithoutShadowsEmitsTheSameDrawsAsBefore() {
        val layout = scatter(Random(seed = 994), count = 300)
        val noLight = render(layout, light = null)
        val shadowsOff = render(layout, light = Light(shadowsEnabled = false))
        val shadowsOn = render(layout, light = Light(direction = Vec3f(1f, 0.6f, 0.3f)))

        assertTrue(noLight.isNotEmpty() && noLight.size < layout.size, "Culling has to keep some and drop some, or the comparison is vacuous.")
        assertTrue(noLight.none { it.shadowsOnly } && shadowsOff.none { it.shadowsOnly }, "No shadow pass, no shadow-only draws.")
        assertEquals(noLight, shadowsOff)
        assertTrue(shadowsOn.any { it.shadowsOnly }, "A shadowed scene must add casters, or the next line proves nothing.")
        assertEquals(noLight, shadowsOn.filterNot { it.shadowsOnly }, "Shadows add shadow-only draws and never change what the camera draws.")
    }

    @Test
    fun theIndexSelectsExactlyTheCastersPerEntityTestingSelects() {
        val layout = scatter(Random(seed = 4_994), count = 300)
        val light = Light(direction = Vec3f(1f, 0.5f, 0.3f))

        val withoutIndex = render(layout, light, useIndex = false)
        val withIndex = render(layout, light, useIndex = true)

        assertTrue(withoutIndex.count { it.shadowsOnly } > 0, "Some props must cast from off screen, or this compares nothing.")
        assertEquals(withoutIndex.toSet(), withIndex.toSet(), "The grid's caster query must agree with testing every entity.")
    }

    @Test
    fun aSkinnedCasterDrawnOnlyForShadowsIsPosedEveryFrame() {
        // The camera stands just in front of the model at the origin, looking away from it; an
        // overhead sun's near cascade still holds it.
        val world = World()
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 0f, 2f), center = Vec3f(0f, 0f, 10f), fovYRadians = 1f, near = 0.1f, far = 100f)))
        world.add(world.create(), Light(direction = Vec3f(0f, 1f, 0.1f)))
        val model = world.spawnSkinnedModel()
        val renderer = RecordingRenderer()
        val render = RenderSystem3D(renderer)

        render.update(world, DELTA)
        AnimationSystem().update(world, delta = 0.5f)

        assertEquals(listOf(true), renderer.drawsOf(world, model).map { it.shadowsOnly })
        assertEquals(0.5f, requireNotNull(world.get<SkinnedPose>(model)).jointPalette[12], "Its shadow shows the pose, so it must be the current one.")

        // Control: with shadows off nothing draws it, and an unseen model keeps its last pose.
        world.turnShadowsOff()
        render.update(world, DELTA)
        AnimationSystem().update(world, delta = 0.5f)
        assertEquals(0.5f, requireNotNull(world.get<SkinnedPose>(model)).jointPalette[12], "Culled, so not posed this time.")
    }

    @Test
    fun casterCutoffSkipsSmallOffScreenCastersOnly() {
        val world = worldWithCamera(light = Light(direction = Vec3f(0f, 1f, 0f)))
        val behind = world.spawnProp(x = 0f, y = 1f, z = 12f)
        val inView = world.spawnProp(x = 0f, y = 1f, z = 0f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)
        // The props' radius is sqrt(3), about 1.73; the one behind is sqrt(5), about 2.24, from
        // the eye: a ratio of 0.77.
        system.minShadowCasterRatio = 0.5f

        system.update(world, DELTA)

        assertEquals(listOf(false), renderer.drawsOf(world, inView).map { it.shadowsOnly }, "A caster the camera sees is never cut.")
        assertEquals(listOf(true), renderer.drawsOf(world, behind).map { it.shadowsOnly }, "Large for its distance, so it still casts.")

        system.minShadowCasterRatio = 1f
        system.update(world, DELTA)
        assertEquals(emptyList(), renderer.drawsOf(world, behind), "Below the cutoff, it is skipped.")
        assertEquals(listOf(false), renderer.drawsOf(world, inView).map { it.shadowsOnly })
    }

    @Test
    fun aMeshThatDoesNotCastIsDrawnButNeverCasts() {
        val world = worldWithCamera(light = Light(direction = Vec3f(0f, 1f, 0f)))
        val inView = world.spawnProp(x = 0f, y = 1f, z = 0f, castsShadows = false)
        val behind = world.spawnProp(x = 0f, y = 1f, z = 12f, castsShadows = false)
        val behindCasting = world.spawnProp(x = 0f, y = 1f, z = 12f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer)

        system.update(world, DELTA)

        assertEquals(
            listOf(Casting(shadowsOnly = false, castsShadows = false)),
            renderer.drawsOf(world, inView).map(RecordedDraw::casting),
            "In view it draws as before, flagged so the shadow passes leave it out.",
        )
        assertEquals(emptyList(), renderer.drawsOf(world, behind), "Off screen it has nothing left to draw.")
        assertEquals(
            listOf(Casting(shadowsOnly = true, castsShadows = true)),
            renderer.drawsOf(world, behindCasting).map(RecordedDraw::casting),
            "The same mesh casting, in the same place, is a shadow-only draw: the control for the line above.",
        )
        assertEquals(1, system.lastShadowOnlyCount)
    }

    @Test
    fun lodGroupsAndModularCharactersThatDoNotCastAreNeverShadowOnly() {
        val world = worldWithCamera(light = Light(direction = Vec3f(0f, 1f, 0f)))
        val lods = listOf(true, false).associateWith { casts ->
            listOf(0f, 12f).map { z -> world.spawnLod(z, casts) }
        }
        val characters = listOf(true, false).associateWith { casts ->
            listOf(0f, 12f).map { z -> world.spawnModularCharacter(z, casts) }
        }
        val renderer = RecordingRenderer()

        RenderSystem3D(renderer).update(world, DELTA)

        for (meshes in listOf(lods, characters)) {
            val (castingInView, castingBehind) = meshes.getValue(true)
            val (silentInView, silentBehind) = meshes.getValue(false)
            assertEquals(listOf(Casting(shadowsOnly = false, castsShadows = true)), renderer.drawsOfMesh(castingInView).map(RecordedDraw::casting))
            assertEquals(listOf(Casting(shadowsOnly = true, castsShadows = true)), renderer.drawsOfMesh(castingBehind).map(RecordedDraw::casting))
            assertEquals(listOf(Casting(shadowsOnly = false, castsShadows = false)), renderer.drawsOfMesh(silentInView).map(RecordedDraw::casting))
            assertEquals(emptyList(), renderer.drawsOfMesh(silentBehind), "Off screen and not casting, it draws nothing.")
        }
    }

    private data class Recorded(val model: List<Float>, val shadowsOnly: Boolean)

    private fun render(layout: List<Pair<Float, Float>>, light: Light?, useIndex: Boolean = false): List<Recorded> {
        val world = worldWithCamera(light)
        layout.forEach { (x, z) -> world.spawnProp(x, 0f, z) }
        val renderer = RecordingRenderer()
        if (useIndex) SpatialIndexSystem().update(world, DELTA)
        RenderSystem3D(renderer).update(world, DELTA)
        return renderer.draws.map { Recorded(it.model, it.shadowsOnly) }
    }

    private fun scatter(random: Random, count: Int): List<Pair<Float, Float>> =
        List(count) { (random.nextFloat() - HALF) * SPREAD to (random.nextFloat() - HALF) * SPREAD }

    private fun worldWithCamera(light: Light?): World {
        val world = World()
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 2f, 10f), center = Vec3f(0f, 1f, 0f), fovYRadians = 1f, near = 0.1f, far = 200f)))
        if (light != null) world.add(world.create(), light)
        return world
    }

    private fun World.spawnProp(x: Float, y: Float, z: Float, castsShadows: Boolean = true): Entity = create().also { entity ->
        add(entity, Transform(worldMatrix = Mat4().translate(x, y, z)))
        add(entity, MeshRenderer(FakeMesh(VertexFormat.PositionNormalColor), FakeMaterial, castsShadows = castsShadows))
        add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
    }

    /** A one-level [LodGroup] at (0, 1, [z]); returns its mesh, which is how its draws are found. */
    private fun World.spawnLod(z: Float, castsShadows: Boolean): Mesh {
        val mesh = FakeMesh(VertexFormat.PositionNormalColor)
        val entity = create()
        add(entity, Transform(worldMatrix = Mat4().translate(0f, 1f, z)))
        add(entity, LodGroup(listOf(LodLevel(mesh, FakeMaterial, maxDistance = 1_000f)), castsShadows = castsShadows))
        add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        return mesh
    }

    /** A one-slot [ModularCharacterComponent] at (0, 1, [z]); returns its slot's mesh. */
    private fun World.spawnModularCharacter(z: Float, castsShadows: Boolean): Mesh {
        val mesh = FakeMesh(VertexFormat.PositionNormalColor)
        val character = ModularCharacterComponent(Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4())), castsShadows = castsShadows)
        character.setSlot(CharacterSlot("body", mesh, FakeMaterial))
        val entity = create()
        add(entity, Transform(worldMatrix = Mat4().translate(0f, 1f, z)))
        add(entity, character)
        add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        return mesh
    }

    private fun World.spawnOccluder(z: Float) {
        val wall = create()
        add(wall, Transform(worldMatrix = Mat4().translate(0f, 1f, z)))
        add(wall, Occluder(Aabb(Vec3f(-WALL_HALF, -WALL_HALF, -HALF), Vec3f(WALL_HALF, WALL_HALF, HALF))))
    }

    /** A one-bone model at the origin whose clip moves it from x 0 to 2 over 2 s. */
    private fun World.spawnSkinnedModel(): Entity {
        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        val clip = AnimationClip(
            name = "walk",
            channels = listOf(
                AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 2f), floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f), 3)),
            ),
        )
        val player = AnimationPlayer(AnimationLibrary(skeleton, mapOf("walk" to clip))).apply { play("walk") }
        return create().also { entity ->
            add(entity, Transform())
            add(entity, MeshRenderer(FakeMesh(VertexFormat.PositionNormalColorUvSkin), FakeMaterial))
            add(entity, MeshBounds(Aabb(Vec3f(-HALF, -HALF, -HALF), Vec3f(HALF, HALF, HALF))))
            add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            add(entity, SkinnedPose(FloatArray(16)))
        }
    }

    private fun World.turnShadowsOff() {
        val lights = ArrayList<Entity>()
        queryEach<Light> { entity, _ -> lights += entity }
        lights.forEach { add(it, Light(shadowsEnabled = false)) }
    }

    /** The draws the last frame made for [entity], found by its own mesh. */
    private fun RecordingRenderer.drawsOf(world: World, entity: Entity): List<RecordedDraw> =
        drawsOfMesh(requireNotNull(world.get<MeshRenderer>(entity)).mesh)

    /** The draws the last frame made of [mesh]. */
    private fun RecordingRenderer.drawsOfMesh(mesh: Mesh): List<RecordedDraw> = draws.filter { it.mesh === mesh }

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private class RecordedDraw(val mesh: Any, val model: List<Float>, val shadowsOnly: Boolean, val castsShadows: Boolean) {
        val casting: Casting get() = Casting(shadowsOnly, castsShadows)
    }

    /** How a draw reaches the shadow passes: as a shadow-only draw, and whether it casts at all. */
    private data class Casting(val shadowsOnly: Boolean, val castsShadows: Boolean)

    private class RecordingRenderer :
        Renderer,
        GpuDrawPreparationSource {
        /** The last frame's requests, published when it is drawn, so a frame with none reads empty. */
        var draws: List<RecordedDraw> = emptyList()
        private var current = ArrayList<RecordedDraw>()
        override val gpuDrawPreparer = GpuDrawPreparer { request, _, _ ->
            current += RecordedDraw(request.mesh, request.model.data.toList(), request.shadowsOnly, request.castsShadows)
            null
        }
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false
        override fun createMesh(geometry: MeshGeometry): Mesh = error("not needed for this test")
        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material =
            error("not needed for this test")

        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("not needed for this test")

        override fun draw(input: GpuPassInput) {
            draws = current
            current = ArrayList()
        }

        override suspend fun readPixels(target: RenderTarget): TextureAsset = error("not needed for this test")
        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val SPREAD = 300f
        const val HALF = 0.5f
        const val WALL_Z = 5f
        const val WALL_HALF = 5f
    }
}
