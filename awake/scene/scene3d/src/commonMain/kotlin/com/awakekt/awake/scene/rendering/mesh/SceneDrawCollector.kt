/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.DEFAULT_BASE_COLOR_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_EMISSIVE_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_METALLIC_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_ROUGHNESS_FACTOR
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.FrameCulling
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.math.sqrt

/** Extracts authored 3D mesh families into backend-neutral draw packets in scene order. */
internal class SceneDrawCollector(
    private val cullingCompiler: SceneCullingCompiler,
) {
    /**
     * Reused every frame, so they keep the capacity the scene grew them to: a fresh list grew
     * by copying, sixteen times over for 50,000 entities. Valid until the next collect; the
     * planner copies them out straight away.
     */
    private val beforeParticles = ArrayList<RenderDrawCommand>()
    private val afterParticles = ArrayList<RenderDrawCommand>()

    private val commandPool = ArrayList<RenderDrawCommand>()
    private var commandPoolCount = 0

    private val scratchAabbPool = ArrayList<Aabb>()
    private var scratchAabbCount = 0

    private val scratchMatrixPool = ArrayList<Mat4>()
    private var scratchMatrixCount = 0

    private var cachedMeshFamily: Family2<Transform, MeshRenderer>? = null
    private var cachedMeshFamilyWorld: World? = null

    private var cachedInstancedFamily: Family1<InstancedMeshRenderer>? = null
    private var cachedInstancedFamilyWorld: World? = null

    private var cachedInstancedSkinnedFamily: Family1<InstancedSkinnedMeshRenderer>? = null
    private var cachedInstancedSkinnedFamilyWorld: World? = null

    private var cachedModularFamily: Family2<Transform, ModularCharacterComponent>? = null
    private var cachedModularFamilyWorld: World? = null

    private var cachedLodFamily: Family2<Transform, LodGroup>? = null
    private var cachedLodFamilyWorld: World? = null

    private var cachedBoundsTypeId: Int = -1
    private var cachedPoseTypeId: Int = -1
    private var cachedPbrTypeId: Int = -1
    private var cachedAnimationTypeId: Int = -1
    private var cachedTypesWorld: World? = null

    /** An animated texture on an entity with no [PbrMaterial]: glTF's default factors, packed once per animation. */
    private val defaultFactorsByAnimation = HashMap<TextureAnimation, FloatArray>()

    fun collectBeforeParticles(
        world: World,
        culling: FrameCulling,
        elapsedTimeSeconds: Float,
    ): List<RenderDrawCommand> {
        val drawCalls = beforeParticles
        drawCalls.clear()
        commandPoolCount = 0
        scratchAabbCount = 0
        scratchMatrixCount = 0

        // Resolved once per world: looking a type up by class is a hash lookup, paid per entity otherwise.
        if (cachedTypesWorld !== world) {
            cachedBoundsTypeId = world.typeId(MeshBounds::class).value
            cachedPoseTypeId = world.typeId(SkinnedPose::class).value
            cachedPbrTypeId = world.typeId(PbrMaterial::class).value
            cachedAnimationTypeId = world.typeId(TextureAnimation::class).value
            cachedTypesWorld = world
        }
        val boundsType = ComponentTypeId(cachedBoundsTypeId)
        val poseType = ComponentTypeId(cachedPoseTypeId)
        val pbrType = ComponentTypeId(cachedPbrTypeId)
        val animationType = ComponentTypeId(cachedAnimationTypeId)

        val boundsStore = world.componentStore<MeshBounds>(boundsType)
        val poseStore = world.componentStore<SkinnedPose>(poseType)
        val pbrStore = world.componentStore<PbrMaterial>(pbrType)
        val animationStore = world.componentStore<TextureAnimation>(animationType)

        meshFamily(world).forEach { entity, transform, meshRenderer ->
            if (!meshRenderer.visible) return@forEach
            // Culling first, before this entity's uniforms are gathered: the PBR branch below
            // allocates, and paying that for something about to be discarded is the one ordering
            // this loop can get wrong for free.
            // A billboard turns with the camera, so its authored bounds do not describe it.
            val bounds = if (meshRenderer.billboard) null else boundsStore?.get(entity)
            if (bounds != null &&
                !cullingCompiler.passesCulling(
                    entity.id,
                    transform,
                    bounds,
                    culling,
                )
            ) {
                return@forEach
            }
            // An entity's mesh format decides which pipeline draws it (see Renderer
            // .pipelinesByFormat) -- extraUniformFloats only matters for a format whose
            // shader reads it (a skinned mesh's joint palette); every other format ignores
            // an empty array the same way it always has.
            val pose = poseStore?.get(entity)
            // The entity's own material wins; otherwise the factors its material was authored with.
            val pbr = pbrStore?.get(entity) ?: meshRenderer.defaultMaterial
            val animation = animationStore?.get(entity)
            val extras = when {
                pose != null -> pbr?.let(pose::tintedBy) ?: pose.jointPalette
                // One shared layout serves both the primary and textured pipelines. Which
                // pipeline reads it is a backend concern, not this system's.
                pbr != null -> pbr.packedFloats(animation ?: TextureAnimation.None)
                animation != null -> defaultFactorsByAnimation.getOrPut(animation) {
                    pbrMaterialFloats(
                        DEFAULT_METALLIC_FACTOR,
                        DEFAULT_ROUGHNESS_FACTOR,
                        DEFAULT_BASE_COLOR_FACTOR,
                        DEFAULT_EMISSIVE_FACTOR,
                        animation,
                    )
                }

                else -> EMPTY_DRAW_EXTRAS
            }
            drawCalls.add(
                obtainCommand(
                    mesh = meshRenderer.mesh,
                    material = meshRenderer.material,
                    model = if (meshRenderer.billboard) {
                        billboardMatrix(transform.worldMatrix, culling.camera.lens, obtainScratchMatrix())
                    } else {
                        transform.worldMatrix
                    },
                    extraUniformFloats = extras,
                    vertexAnimation = meshRenderer.vertexAnimation,
                    timeSeconds = elapsedTimeSeconds,
                    cullMode = meshRenderer.cullMode,
                    alphaMode = pbr?.alphaMode ?: AlphaMode.Opaque,
                    alphaCutoff = pbr?.alphaCutoff ?: 0.5f,
                    transparent = meshRenderer.transparent,
                    additive = meshRenderer.additive,
                    worldBounds = bounds?.writeWorldBounds(transform, obtainScratchAabb()),
                ),
            )
        }
        // One RenderDrawCommand per entity here too, but instanceModels (not model) carries every
        // copy's transform -- a backend with an instanced pipeline for this mesh's format
        // draws all of them in one GPU call. See InstancedMeshRenderer's own doc comment for
        // why this is a separate opt-in component/query rather than folding into the
        // MeshRenderer loop above.
        instancedFamily(world).forEach { _, instanced ->
            drawCalls.add(
                obtainCommand(
                    mesh = instanced.mesh,
                    material = instanced.material,
                    model = IDENTITY_MATRIX,
                    instanceModels = instanced.transforms,
                ),
            )
        }
        // Animated counterpart to the InstancedMeshRenderer loop above -- instanceModels AND
        // instanceJointPalettes both carry one entry per instance, index-for-index. See
        // InstancedSkinnedMeshRenderer's own doc comment for why this is a separate component
        // rather than folding into InstancedMeshRenderer.
        instancedSkinnedFamily(world).forEach { _, instanced ->
            drawCalls.add(
                obtainCommand(
                    mesh = instanced.mesh,
                    material = instanced.material,
                    model = IDENTITY_MATRIX,
                    instanceModels = instanced.transforms(),
                    instanceJointPalettes = instanced.jointPalettes(),
                ),
            )
        }
        // Modular character loop: all equipped visible slots on an entity are drawn using the
        // entity's transform and shared SkinnedPose joint palette without requiring dummy child entities.
        modularFamily(world).forEach { entity, transform, modularCharacter ->
            if (!modularCharacter.isVisible) return@forEach
            val bounds = boundsStore?.get(entity)
            if (bounds != null &&
                !cullingCompiler.passesCulling(
                    entity.id,
                    transform,
                    bounds,
                    culling,
                )
            ) {
                return@forEach
            }

            val pose = poseStore?.get(entity)
            val extras = pose?.let { pbrStore?.get(entity)?.let(it::tintedBy) ?: it.jointPalette } ?: EMPTY_DRAW_EXTRAS

            for (slot in modularCharacter.slots.values) {
                if (!slot.isVisible) continue
                drawCalls.add(
                    obtainCommand(
                        mesh = slot.mesh,
                        material = slot.material,
                        model = transform.worldMatrix,
                        extraUniformFloats = extras,
                        timeSeconds = elapsedTimeSeconds,
                        worldBounds = bounds?.writeWorldBounds(transform, obtainScratchAabb()),
                    ),
                )
            }
        }
        return drawCalls
    }

    fun collectAfterParticles(world: World, culling: FrameCulling, camera: Camera): List<RenderDrawCommand> {
        val drawCalls = afterParticles
        drawCalls.clear()

        if (cachedTypesWorld !== world) {
            cachedBoundsTypeId = world.typeId(MeshBounds::class).value
            cachedPoseTypeId = world.typeId(SkinnedPose::class).value
            cachedPbrTypeId = world.typeId(PbrMaterial::class).value
            cachedAnimationTypeId = world.typeId(TextureAnimation::class).value
            cachedTypesWorld = world
        }
        val boundsType = ComponentTypeId(cachedBoundsTypeId)
        val boundsStore = world.componentStore<MeshBounds>(boundsType)

        // LodGroup picks ONE level's mesh/material by distance to the camera eye -- see that
        // component's own doc comment for why an entity carries this instead of MeshRenderer,
        // not both. LOD selects detail, it doesn't cull -- MeshBounds/frustum culling still
        // applies on top when present.
        lodFamily(world).forEach { entity, transform, lodGroup ->
            val eye = camera.lens.eye
            val dx = transform.worldMatrix.m03 - eye.x
            val dy = transform.worldMatrix.m13 - eye.y
            val dz = transform.worldMatrix.m23 - eye.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            // Selection remembers what it drew last frame -- see LodGroup.selectLevel for why a
            // bare threshold flickers for an entity parked near one.
            val level = lodGroup.selectLevel(distance)

            val bounds = boundsStore?.get(entity)
            if (bounds != null &&
                !cullingCompiler.passesCulling(
                    entity.id,
                    transform,
                    bounds,
                    culling,
                )
            ) {
                return@forEach
            }
            drawCalls.add(
                obtainCommand(
                    mesh = level.mesh,
                    material = level.material,
                    model = transform.worldMatrix,
                    worldBounds = bounds?.writeWorldBounds(transform, obtainScratchAabb()),
                ),
            )
        }

        return drawCalls
    }

    private fun meshFamily(world: World): Family2<Transform, MeshRenderer> {
        if (cachedMeshFamilyWorld === world && cachedMeshFamily != null) return cachedMeshFamily!!
        return world.family<Transform, MeshRenderer>().also {
            cachedMeshFamily = it
            cachedMeshFamilyWorld = world
        }
    }

    private fun instancedFamily(world: World): Family1<InstancedMeshRenderer> {
        if (cachedInstancedFamilyWorld === world && cachedInstancedFamily != null) return cachedInstancedFamily!!
        return world.family<InstancedMeshRenderer>().also {
            cachedInstancedFamily = it
            cachedInstancedFamilyWorld = world
        }
    }

    private fun instancedSkinnedFamily(world: World): Family1<InstancedSkinnedMeshRenderer> {
        if (cachedInstancedSkinnedFamilyWorld === world && cachedInstancedSkinnedFamily != null) {
            return cachedInstancedSkinnedFamily!!
        }
        return world.family<InstancedSkinnedMeshRenderer>().also {
            cachedInstancedSkinnedFamily = it
            cachedInstancedSkinnedFamilyWorld = world
        }
    }

    private fun modularFamily(world: World): Family2<Transform, ModularCharacterComponent> {
        if (cachedModularFamilyWorld === world && cachedModularFamily != null) return cachedModularFamily!!
        return world.family<Transform, ModularCharacterComponent>().also {
            cachedModularFamily = it
            cachedModularFamilyWorld = world
        }
    }

    private fun lodFamily(world: World): Family2<Transform, LodGroup> {
        if (cachedLodFamilyWorld === world && cachedLodFamily != null) return cachedLodFamily!!
        return world.family<Transform, LodGroup>().also {
            cachedLodFamily = it
            cachedLodFamilyWorld = world
        }
    }

    @Suppress("LongParameterList")
    private fun obtainCommand(
        mesh: Mesh,
        material: Material,
        model: Mat4,
        extraUniformFloats: FloatArray = EMPTY_DRAW_EXTRAS,
        vertexAnimation: Vec3f = Vec3f.ZERO,
        timeSeconds: Float = 0f,
        instanceModels: List<Mat4>? = null,
        instanceJointPalettes: List<FloatArray>? = null,
        instanceColors: List<Vec4>? = null,
        instanceFrames: List<Float>? = null,
        cullMode: CullMode = CullMode.None,
        alphaMode: AlphaMode = AlphaMode.Opaque,
        alphaCutoff: Float = 0.5f,
        transparent: Boolean = false,
        shadowsOnly: Boolean = false,
        worldBounds: Aabb? = null,
        additive: Boolean = false,
    ): RenderDrawCommand {
        if (commandPoolCount < commandPool.size) {
            return commandPool[commandPoolCount++].set(
                mesh = mesh,
                material = material,
                model = model,
                extraUniformFloats = extraUniformFloats,
                vertexAnimation = vertexAnimation,
                timeSeconds = timeSeconds,
                instanceModels = instanceModels,
                instanceJointPalettes = instanceJointPalettes,
                instanceColors = instanceColors,
                instanceFrames = instanceFrames,
                cullMode = cullMode,
                alphaMode = alphaMode,
                alphaCutoff = alphaCutoff,
                transparent = transparent,
                shadowsOnly = shadowsOnly,
                worldBounds = worldBounds,
                additive = additive,
            )
        }
        val command = RenderDrawCommand(
            mesh = mesh,
            material = material,
            model = model,
            extraUniformFloats = extraUniformFloats,
            vertexAnimation = vertexAnimation,
            timeSeconds = timeSeconds,
            instanceModels = instanceModels,
            instanceJointPalettes = instanceJointPalettes,
            instanceColors = instanceColors,
            instanceFrames = instanceFrames,
            cullMode = cullMode,
            alphaMode = alphaMode,
            alphaCutoff = alphaCutoff,
            transparent = transparent,
            shadowsOnly = shadowsOnly,
            worldBounds = worldBounds,
            additive = additive,
        )
        commandPool.add(command)
        commandPoolCount++
        return command
    }

    private fun obtainScratchAabb(): Aabb {
        if (scratchAabbCount < scratchAabbPool.size) {
            return scratchAabbPool[scratchAabbCount++]
        }
        val aabb = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
        scratchAabbPool.add(aabb)
        scratchAabbCount++
        return aabb
    }

    private fun obtainScratchMatrix(): Mat4 {
        if (scratchMatrixCount < scratchMatrixPool.size) {
            return scratchMatrixPool[scratchMatrixCount++]
        }
        val matrix = Mat4()
        scratchMatrixPool.add(matrix)
        scratchMatrixCount++
        return matrix
    }
}

private val IDENTITY_MATRIX = Mat4()

private val EMPTY_DRAW_EXTRAS = FloatArray(0)

/** The pose's palette tinted by [material]'s factors, in one array the pose keeps and rewrites each frame. */
private fun SkinnedPose.tintedBy(material: PbrMaterial): FloatArray = skinnedMaterialFloats(
    jointPalette,
    material.baseColorFactor,
    material.emissiveFactor,
    into = tintedPalette ?: FloatArray(SkinnedMaterialLayout.total).also { tintedPalette = it },
)
