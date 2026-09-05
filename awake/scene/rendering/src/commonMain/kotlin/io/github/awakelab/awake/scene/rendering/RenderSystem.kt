/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Plane
import io.github.awakelab.awake.core.math.ScreenBounds
import io.github.awakelab.awake.core.math.Vec3
import io.github.awakelab.awake.core.math.Vec4
import io.github.awakelab.awake.core.math.containsSphere
import io.github.awakelab.awake.core.math.intersects
import io.github.awakelab.awake.core.math.isOccludedBy
import io.github.awakelab.awake.core.math.planes
import io.github.awakelab.awake.core.math.screenBounds
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.renderer.DEFAULT_SCENE_LIGHT
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.PointLight
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.renderer.directionalShadowBox
import io.github.awakelab.awake.render.renderer.shadowCascadeUniforms
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.animation.ModularCharacterComponent
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose
import io.github.awakelab.awake.scene.rendering.debug.debugSettingsOrNull
import io.github.awakelab.awake.scene.rendering.mesh.InstancedMeshRenderer
import io.github.awakelab.awake.scene.rendering.mesh.InstancedSkinnedMeshRenderer
import io.github.awakelab.awake.scene.rendering.mesh.LodGroup
import io.github.awakelab.awake.scene.rendering.mesh.MeshBounds
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
import io.github.awakelab.awake.scene.rendering.mesh.PbrMaterial
import io.github.awakelab.awake.scene.rendering.particles.ParticleEmitter
import io.github.awakelab.awake.scene.rendering.particles.currentAlpha
import io.github.awakelab.awake.scene.rendering.particles.currentColor
import io.github.awakelab.awake.scene.rendering.particles.currentFrame
import io.github.awakelab.awake.scene.rendering.spatial.Occluder
import io.github.awakelab.awake.scene.rendering.spatial.SpatialIndex
import io.github.awakelab.awake.scene.rendering.spatial.findSpatialIndex

class RenderSystem(
    private val renderer: Renderer,
) : System {
    private val drawCalls = ArrayList<DrawCall>()
    private val occluderBounds = ArrayList<ScreenBounds>()

    /** This frame's frustum-visible ids when a SpatialIndex exists -- see [visibleByIndex]. */
    private val visibleIds = HashSet<Int>()
    private var elapsedTimeSeconds = 0f

    /** Refilled each frame by [sceneLight]; see its own note on why it is not a local. */
    private val pointLights = ArrayList<PointLight>()

    /** How many entities occlusion culling actually excluded last frame -- zero when no
     * [Occluder] entities exist, or when none of them fully cover anything. Exists purely so a
     * demo/debug panel or a test can observe "did this actually cull anything" without pixel-
     * level verification, same reasoning [io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings]'s
     * toggles get a live readout for. */
    var lastOccludedCount: Int = 0
        private set

    override fun update(world: World, delta: Float) {
        elapsedTimeSeconds += delta.coerceAtLeast(0f)
        val camera = primaryCamera(world) ?: run {
            // No scene camera -- e.g. a UI-only sample with an empty World (see ui-showcase's
            // GameModule). There is nothing 3D to draw, but the swapchain must still be
            // presented: `drawUi()` already staged this frame's UI overlay, and `renderer.draw()`
            // is the only call that acquires/submits/presents a frame. Skipping it here left the
            // window showing nothing at all, forever, even though the UI pass had real content.
            renderer.draw(FALLBACK_LENS, emptyList(), DEFAULT_SCENE_LIGHT)
            return
        }
        drawCalls.clear()
        occluderBounds.clear()
        lastOccludedCount = 0
        // Gated on occluderFamily.size, not built unconditionally: a scene with zero Occluder
        // entities (the common case) pays one extra family-cache lookup, not a view-projection
        // matrix build, per frame. See Occluder's own doc comment for why occlusion is an
        // independent opt-in from MeshBounds.
        val occluderFamily = world.family<Transform, Occluder>()
        val occlusionViewProjection: Mat4? = if (occluderFamily.size > 0) {
            val viewProjection = camera.lens.viewProjectionMatrix(CONSERVATIVE_ASPECT, renderer.clipSpace)
            occluderFamily.forEach { _, transform, occluder ->
                val worldBounds = occluder.localBounds.transformed(transform.worldMatrix)
                camera.lens.screenBounds(worldBounds, viewProjection)?.let { occluderBounds.add(it) }
            }
            viewProjection
        } else {
            null
        }
        val visible = visibleByIndex(world, camera)
        val culling = FrameCulling(
            camera = camera,
            visible = visible,
            // Once per frame, not once per entity: Frustum.intersects(lens, aspect, box) rebuilds
            // six planes on every call, which at 10k entities measured 1446us against 34us for the
            // same tests against one shared list (SpatialCullingBenchmarks). Unused when the index
            // already answered the frustum question for the whole scene.
            planes = if (visible == null) Frustum.planes(camera.lens, CONSERVATIVE_ASPECT) else null,
            occlusionViewProjection = occlusionViewProjection,
        )
        world.family<Transform, MeshRenderer>().forEach { entity, transform, meshRenderer ->
            if (!meshRenderer.visible) return@forEach
            // Culling first, before this entity's uniforms are gathered: the PBR branch below
            // allocates, and paying that for something about to be discarded is the one ordering
            // this loop can get wrong for free.
            val bounds = world.get<MeshBounds>(entity)
            if (bounds != null && !passesCulling(entity.id, transform, bounds, culling)) return@forEach
            // An entity's mesh format decides which pipeline draws it (see Renderer
            // .pipelinesByFormat) -- extraUniformFloats only matters for a format whose
            // shader reads it (a skinned mesh's joint palette); every other format ignores
            // an empty array the same way it always has.
            val pose = world.get<SkinnedPose>(entity)
            val pbr = world.get<PbrMaterial>(entity)
            val extras = when {
                pose != null -> pose.jointPalette
                // [metallic, roughness, pad, pad, baseColorFactor.rgba, emissiveFactor.rgb,
                // pad] -- the primary lit pipeline only reads the first 4 (see
                // RendererDraw3D.pbrMaterialFloats), the textured/glTF pipeline reads all 12
                // (see that backend's own pbr-factor helper). One packing serves both since
                // which pipeline a given mesh format resolves to is a backend concern, not
                // this system's.
                pbr != null -> floatArrayOf(
                    pbr.metallic, pbr.roughness, 0f, 0f,
                    pbr.baseColorFactor[0], pbr.baseColorFactor[1], pbr.baseColorFactor[2], pbr.baseColorFactor[3],
                    pbr.emissiveFactor[0], pbr.emissiveFactor[1], pbr.emissiveFactor[2], 0f,
                )
                else -> EMPTY_EXTRAS
            }
            drawCalls.add(
                DrawCall(
                    mesh = meshRenderer.mesh,
                    material = meshRenderer.material,
                    model = transform.worldMatrix,
                    extraUniformFloats = extras,
                    vertexAnimation = meshRenderer.vertexAnimation,
                    timeSeconds = elapsedTimeSeconds,
                    cullMode = meshRenderer.cullMode,
                    transparent = meshRenderer.transparent,
                ),
            )
        }
        // One DrawCall per entity here too, but instanceModels (not model) carries every
        // copy's transform -- a backend with an instanced pipeline for this mesh's format
        // draws all of them in one GPU call. See InstancedMeshRenderer's own doc comment for
        // why this is a separate opt-in component/query rather than folding into the
        // MeshRenderer loop above.
        world.family<InstancedMeshRenderer>().forEach { _, instanced ->
            drawCalls.add(
                DrawCall(
                    mesh = instanced.mesh,
                    material = instanced.material,
                    instanceModels = instanced.transforms,
                ),
            )
        }
        // Animated counterpart to the InstancedMeshRenderer loop above -- instanceModels AND
        // instanceJointPalettes both carry one entry per instance, index-for-index. See
        // InstancedSkinnedMeshRenderer's own doc comment for why this is a separate component
        // rather than folding into InstancedMeshRenderer.
        world.family<InstancedSkinnedMeshRenderer>().forEach { _, instanced ->
            drawCalls.add(
                DrawCall(
                    mesh = instanced.mesh,
                    material = instanced.material,
                    instanceModels = instanced.instances.map { it.transform },
                    instanceJointPalettes = instanced.instances.map { it.jointPalette },
                ),
            )
        }
        // Modular character loop: all equipped visible slots on an entity are drawn using the
        // entity's transform and shared SkinnedPose joint palette without requiring dummy child entities.
        world.family<Transform, ModularCharacterComponent>().forEach { entity, transform, modularCharacter ->
            if (!modularCharacter.isVisible) return@forEach
            val bounds = world.get<MeshBounds>(entity)
            if (bounds != null && !passesCulling(entity.id, transform, bounds, culling)) return@forEach

            val pose = world.get<SkinnedPose>(entity)
            val extras = pose?.jointPalette ?: EMPTY_EXTRAS

            for (slot in modularCharacter.slots.values) {
                if (!slot.isVisible) continue
                drawCalls.add(
                    DrawCall(
                        mesh = slot.mesh,
                        material = slot.material,
                        model = transform.worldMatrix,
                        extraUniformFloats = extras,
                        timeSeconds = elapsedTimeSeconds,
                    ),
                )
            }
        }
        // Billboard particles -- one DrawCall per emitter, instanceModels/instanceColors carry
        // one entry per LIVE particle (dead pool slots are skipped, not drawn as invisible
        // instances). cameraRight/cameraUp are the same forward/right/up basis Frustum.corners
        // already computes, just used here to keep every particle facing the camera in the
        // shader rather than baking a per-instance rotation into its own model matrix -- see
        // ParticleEmitter's own doc comment for why particles never carry rotation at all.
        val particleFamily = world.family<ParticleEmitter>()
        if (particleFamily.size > 0) {
            val forward = (camera.lens.center - camera.lens.eye).normalized()
            val right = forward.cross(camera.lens.up).normalized()
            val cameraUp = right.cross(forward)
            val cameraBasis = floatArrayOf(right.x, right.y, right.z, 0f, cameraUp.x, cameraUp.y, cameraUp.z, 0f)
            // Built once per frame, shared by every emitter's own per-particle test below --
            // Frustum.planes recomputes 6 planes from the camera each call, so sharing it across
            // hundreds of particles (instead of recomputing per particle) is the whole point.
            val frustumPlanes = Frustum.planes(camera.lens, CONSERVATIVE_ASPECT)
            particleFamily.forEach { _, emitter ->
                addParticleDrawCalls(emitter, cameraBasis, frustumPlanes, camera.lens.eye)
            }
        }
        // LodGroup picks ONE level's mesh/material by distance to the camera eye -- see that
        // component's own doc comment for why an entity carries this instead of MeshRenderer,
        // not both. LOD selects detail, it doesn't cull -- MeshBounds/frustum culling still
        // applies on top when present.
        world.family<Transform, LodGroup>().forEach { entity, transform, lodGroup ->
            val worldPosition = Vec3(transform.worldMatrix.m03, transform.worldMatrix.m13, transform.worldMatrix.m23)
            val distance = (worldPosition - camera.lens.eye).length3()
            // Selection remembers what it drew last frame -- see LodGroup.selectLevel for why a
            // bare threshold flickers for an entity parked near one.
            val level = lodGroup.selectLevel(distance)

            val bounds = world.get<MeshBounds>(entity)
            if (bounds != null && !passesCulling(entity.id, transform, bounds, culling)) return@forEach
            drawCalls.add(DrawCall(mesh = level.mesh, material = level.material, model = transform.worldMatrix))
        }
        renderer.draw(camera.lens, drawCalls, sceneLight(world, camera))
    }

    /**
     * Whether this entity survives frustum and occlusion culling.
     *
     * With an index the frustum half is a set lookup, because the index answered it for the whole
     * scene in one query; without one it is the same per-entity test as before, against planes
     * built once this frame. Occlusion stays per entity either way -- it depends on what else is
     * on screen, which the index knows nothing about.
     */
    private fun passesCulling(
        entityId: Int,
        transform: Transform,
        bounds: MeshBounds,
        culling: FrameCulling,
    ): Boolean {
        val inFrustum = if (culling.visible != null) {
            entityId in culling.visible
        } else {
            culling.planes?.intersects(bounds.worldBounds(transform.worldMatrix)) != false
        }
        if (!inFrustum) return false
        val occluded = culling.occlusionViewProjection != null &&
            isOccluded(
                culling.camera.lens,
                bounds.worldBounds(transform.worldMatrix),
                culling.occlusionViewProjection,
            )
        if (occluded) lastOccludedCount++
        return !occluded
    }

    /**
     * This frame's culling inputs, gathered once.
     *
     * A holder rather than five parameters threaded through every call site: the plane list and
     * the index's answer are both per frame, and passing them one by one is how one loop ends up
     * using the shared planes while another rebuilds them.
     */
    private class FrameCulling(
        val camera: Camera,
        val visible: Set<Int>?,
        val planes: List<Plane>?,
        val occlusionViewProjection: Mat4?,
    )

    /**
     * This frame's visible ids from the scene's [SpatialIndex], or null when no system maintains
     * one.
     *
     * Null is the fallback path, not an error: without an index every entity is frustum-tested
     * individually, which is what this system did before `SpatialIndexSystem` existed and what a
     * scene that never installs it keeps doing.
     *
     * The set is reused across frames -- this runs every frame, and a fresh `HashSet` per frame
     * is garbage proportional to what is on screen.
     */
    private fun visibleByIndex(world: World, camera: Camera): MutableSet<Int>? {
        val grid = world.findSpatialIndex()?.grid ?: return null
        visibleIds.clear()
        grid.queryFrustum(camera.lens, CONSERVATIVE_ASPECT, visibleIds)
        return visibleIds
    }

    /** `false` when [occlusionViewProjection] is `null` (no [Occluder] entities exist this
     * frame) or [worldBounds] straddles the near plane (see [io.github.awakelab.awake.core.math.screenBounds]'s
     * own doc comment) -- both are "can't tell, so don't cull" cases, same conservative bias
     * [Frustum.intersects] already keeps. */
    private fun isOccluded(
        camera: io.github.awakelab.awake.core.math.Lens,
        worldBounds: io.github.awakelab.awake.core.math.Aabb,
        occlusionViewProjection: Mat4?,
    ): Boolean {
        if (occlusionViewProjection == null || occluderBounds.isEmpty()) return false
        val candidateBounds = camera.screenBounds(worldBounds, occlusionViewProjection) ?: return false
        return occluderBounds.any { isOccludedBy(candidateBounds, it) }
    }

    /** Builds and adds [emitter]'s own particle `DrawCall` (skipped if it has no live particles),
     * then recurses into every [ParticleEmitter.children] entry -- children have no `Entity` of
     * their own, so they're not reachable via `world.family<ParticleEmitter>()` and must be
     * walked here explicitly, same recursion shape [ParticleSystem.simulate] already uses to
     * advance them. [cameraBasis] is shared across the whole tree (computed once per frame by the
     * caller), not recomputed per emitter. */
    private fun addParticleDrawCalls(
        emitter: ParticleEmitter,
        cameraBasis: FloatArray,
        frustumPlanes: List<Plane>,
        eye: Vec3,
    ) {
        // Reused buffers, cleared (not reallocated) every frame -- see
        // ParticleEmitter.instanceModelsBuffer's own doc comment for why this replaced
        // a `particles.filter{}.map{}.map{}` chain (3 list allocations + no-op work on
        // dead slots, every emitter, every frame). visibleParticlesBuffer holds PARTICLE
        // references (not floats) so it can be frustum-filtered and depth-sorted before the
        // instance buffers are built from it, instead of building instance data first and
        // discovering the order/visibility needs fixing after.
        val visible = emitter.visibleParticlesBuffer
        visible.clear()
        emitter.particles.forEach { particle ->
            if (!particle.alive) return@forEach
            if (!frustumPlanes.containsSphere(particle.position, particle.scale)) return@forEach
            visible += particle
        }
        // Back-to-front (farthest first): alpha-blended particles don't write depth, so draw
        // order IS the only thing deciding which one wins where two overlap -- the standard
        // painter's-algorithm fix. Squared distance, not length3(): the ordering is identical
        // and it skips a sqrt plus a Vec3 allocation per comparison.
        visible.sortByDescending { it.position.squaredDistanceTo(eye) }
        val instanceModels = emitter.instanceModelsBuffer
        val instanceColors = emitter.instanceColorsBuffer
        val instanceFrames = emitter.instanceFramesBuffer
        instanceModels.clear()
        instanceColors.clear()
        instanceFrames.clear()
        visible.forEachIndexed { index, particle ->
            // Pooled and mutated in place -- see ParticleEmitter.modelPool's own doc comment.
            while (emitter.modelPool.size <= index) emitter.modelPool += Mat4()
            while (emitter.colorPool.size <= index) emitter.colorPool += Vec4()
            val model = emitter.modelPool[index].setTranslationScale(
                particle.position.x,
                particle.position.y,
                particle.position.z,
                particle.scale,
            )
            // ParticleVisual.stretchWithVelocity: column 1 (m01/m11/m21) is otherwise dead --
            // particle.wgsl only ever reads column 0 (width) and column 3 (center), never
            // column 1's own diagonal scale value `.scale()` happens to leave there -- so a
            // world-space stretch vector rides there for free instead of needing a whole new
            // per-instance GPU buffer/binding just for this one optional capability.
            if (emitter.visual.stretchWithVelocity) {
                val speed = particle.velocity.length3()
                if (speed > 0f) {
                    val stretch = particle.velocity * emitter.visual.stretchFactor
                    model.m01 = stretch.x
                    model.m11 = stretch.y
                    model.m21 = stretch.z
                }
            }
            instanceModels += model
            // Per-PARTICLE color+alpha (each ages independently, so a burst's
            // later-spawned particles sit at an earlier point in the emitter's
            // startColor->endColor gradient than its first-spawned ones) -- see
            // Particle.currentColor's own doc comment.
            val color = particle.currentColor(emitter)
            instanceColors += emitter.colorPool[index].also {
                it.x = color.x
                it.y = color.y
                it.z = color.z
                it.w = particle.currentAlpha()
            }
            // Per-PARTICLE desynced sprite-strip frame -- see Particle.currentFrame's own doc
            // comment.
            instanceFrames += particle.currentFrame(emitter)
        }
        if (instanceModels.isNotEmpty()) {
            // Camera basis (shared, every emitter this frame) + this emitter's frame count --
            // see ParticleVisual.frameCount's own doc comment. frameInfo.y is unused/reserved
            // now that frame cycling is per-particle (instanceFrames), not emitter-wide.
            val uniformFloats = emitter.uniformFloatsBuffer
            cameraBasis.copyInto(uniformFloats)
            uniformFloats[8] = emitter.visual.frameCount.toFloat()
            uniformFloats[9] = 0f
            uniformFloats[10] = 0f
            uniformFloats[11] = 0f
            drawCalls.add(
                DrawCall(
                    mesh = emitter.mesh,
                    material = emitter.material,
                    instanceModels = instanceModels,
                    instanceColors = instanceColors,
                    instanceFrames = instanceFrames,
                    extraUniformFloats = uniformFloats,
                ),
            )
        }
        emitter.children.forEach { child -> addParticleDrawCalls(child, cameraBasis, frustumPlanes, eye) }
    }

    /**
     * The scene's lighting: the first [Light.Type.Directional] entity plus every
     * [Light.Type.Point] one, converted to the backend-neutral [SceneLight].
     *
     * [DEFAULT_SCENE_LIGHT] when no directional light exists -- the direction and colour every
     * lit shader hardcoded before this component did, so an un-lit scene renders as it always
     * did. Point lights still apply on top of that default; a scene can be lit entirely by them.
     *
     * A point light's position comes from its entity's `Transform`, so a point light without one
     * is skipped rather than silently placed at the origin.
     */
    private fun sceneLight(world: World, camera: Camera): SceneLight {
        var directional: Light? = null
        // Reused, not rebuilt: this runs every frame, and `skills/awake-core-math` rules out
        // allocating inside System.update.
        //
        // The list is reused; the PointLight instances still are not -- one per point light per
        // frame. Pooling them would mean making PointLight mutable, and it is a public value type
        // the render contract hands to backends. A handful of small objects is the accepted cost;
        // the ArrayList, two mapped Lists and 2N Pairs this replaces were not.
        pointLights.clear()
        world.family<Light>().forEach { entity, light ->
            when (light.type) {
                Light.Type.Directional -> if (directional == null) directional = light
                Light.Type.Point -> {
                    val transform = world.get<Transform>(entity) ?: return@forEach
                    pointLights += PointLight(
                        position = transform.position,
                        color = light.color * light.intensity,
                        range = light.range,
                    )
                }
            }
        }
        val sun = directional
        val base = if (sun == null) {
            DEFAULT_SCENE_LIGHT.copy(points = pointLights)
        } else {
            SceneLight(sun.direction, sun.color * sun.intensity, pointLights)
        }
        // The volume a directional light covers is a content decision, so it is made here rather
        // than inside a backend -- see docs/reference/render-extensibility.md. A backend renders
        // depth from whatever matrix it is handed and never builds one.
        //
        // Allocates per frame, but only while shadows are on, and the caller that used to build
        // one fixed box allocated the same kind of thing.
        if (!renderer.shadowsEnabled) return base
        return shadowedLight(world, camera, base)
    }

    /** [base] plus whichever shadow fit is in force -- cascades, or the single box. */
    private fun shadowedLight(world: World, camera: Camera, base: SceneLight): SceneLight {
        // Fitted to THIS camera's frustum, in slices. The fixed box `directionalShadowBox` still
        // builds covers a volume at the origin, which is right for a demo scene sitting there and
        // wrong for anything that walks away from it -- the shadows simply stop.
        // The REAL viewport aspect, not CONSERVATIVE_ASPECT. That constant is deliberately wider
        // than any real viewport so this system's own cull test never drops something visible --
        // harmless there, expensive here: a frustum three times too wide gives cascade boxes
        // roughly 1.4x too large in every direction, and a cascade's texel size IS its box size
        // over the map's fixed 2048. That was half of why shadow edges looked like stairs.
        val aspect = renderer.sceneViewport?.aspect ?: DEFAULT_SHADOW_FIT_ASPECT
        // The single fixed box, when a viewer asks for it: `SceneLight.shadowCascades()` turns a
        // lone viewProjection into a one-cascade set, so a backend still has exactly one path.
        val boxOnly = world.debugSettingsOrNull()?.cascadedShadows == false
        val cascades = if (boxOnly) {
            null
        } else {
            shadowCascadeUniforms(base, camera.lens, aspect, renderer.clipSpace)
        }
        if (cascades == null) {
            return base.copy(
                viewProjection = directionalShadowBox(base.direction, renderer.clipSpace).viewProjection,
            )
        }
        // viewProjection stays the near cascade: the debug visualiser draws its wireframe, and a
        // backend that reads it alone still gets the cascade covering what is nearest.
        return base.copy(viewProjection = cascades.viewProjections.first(), cascades = cascades)
    }
}

private val EMPTY_EXTRAS = FloatArray(0)

/** Used only to drive [Renderer.draw]'s acquire/submit/present cycle when a scene has no camera
 * -- see [RenderSystem.update]'s no-camera branch. Never actually frames anything: draw calls are
 * empty in that branch, so its eye/target/fov never reach a shader. */
private val FALLBACK_LENS = Lens.perspective()

/**
 * The aspect cascades are fitted with when nothing has set a scene viewport.
 *
 * A full-window render sets no rect, and the renderer's surface size is not on the shared
 * contract -- so this is a plain widescreen guess rather than a lie about being conservative.
 * Too narrow costs shadow at the edges of an ultrawide window; too wide costs resolution
 * everywhere, which is the failure being fixed.
 */
private const val DEFAULT_SHADOW_FIT_ASPECT = 16f / 9f
