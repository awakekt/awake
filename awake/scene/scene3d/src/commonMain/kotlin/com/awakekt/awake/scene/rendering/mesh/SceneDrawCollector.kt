/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Vec3
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.spatial.FrameCulling
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler

/** Extracts authored 3D mesh families into backend-neutral draw packets in scene order. */
internal class SceneDrawCollector(
    private val cullingCompiler: SceneCullingCompiler,
) {
    fun collectBeforeParticles(
        world: World,
        culling: FrameCulling,
        elapsedTimeSeconds: Float,
    ): ArrayList<RenderDrawCommand> {
        val drawCalls = ArrayList<RenderDrawCommand>()
        world.family<Transform, MeshRenderer>().forEach { entity, transform, meshRenderer ->
            if (!meshRenderer.visible) return@forEach
            // Culling first, before this entity's uniforms are gathered: the PBR branch below
            // allocates, and paying that for something about to be discarded is the one ordering
            // this loop can get wrong for free.
            val bounds = world.get<MeshBounds>(entity)
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
            val pose = world.get<SkinnedPose>(entity)
            val pbr = world.get<PbrMaterial>(entity)
            val extras = when {
                pose != null -> pose.jointPalette
                // One shared layout serves both the primary and textured pipelines. Which
                // pipeline reads it is a backend concern, not this system's.
                pbr != null -> pbrMaterialFloats(
                    metallic = pbr.metallic,
                    roughness = pbr.roughness,
                    baseColorFactor = pbr.baseColorFactor,
                    emissiveFactor = pbr.emissiveFactor,
                )

                else -> EMPTY_DRAW_EXTRAS
            }
            drawCalls.add(
                RenderDrawCommand(
                    mesh = meshRenderer.mesh,
                    material = meshRenderer.material,
                    model = transform.worldMatrix,
                    extraUniformFloats = extras,
                    vertexAnimation = meshRenderer.vertexAnimation,
                    timeSeconds = elapsedTimeSeconds,
                    cullMode = meshRenderer.cullMode,
                    alphaMode = pbr?.alphaMode ?: com.awakekt.awake.render.pipeline.AlphaMode.Opaque,
                    alphaCutoff = pbr?.alphaCutoff ?: 0.5f,
                    transparent = meshRenderer.transparent,
                ),
            )
        }
        // One RenderDrawCommand per entity here too, but instanceModels (not model) carries every
        // copy's transform -- a backend with an instanced pipeline for this mesh's format
        // draws all of them in one GPU call. See InstancedMeshRenderer's own doc comment for
        // why this is a separate opt-in component/query rather than folding into the
        // MeshRenderer loop above.
        world.family<InstancedMeshRenderer>().forEach { _, instanced ->
            drawCalls.add(
                RenderDrawCommand(
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
                RenderDrawCommand(
                    mesh = instanced.mesh,
                    material = instanced.material,
                    instanceModels = instanced.instances.map { it.transform },
                    instanceJointPalettes = instanced.instances.map { it.jointPalette },
                ),
            )
        }
        // Modular character loop: all equipped visible slots on an entity are drawn using the
        // entity's transform and shared SkinnedPose joint palette without requiring dummy child entities.
        world.family<Transform, ModularCharacterComponent>()
            .forEach { entity, transform, modularCharacter ->
                if (!modularCharacter.isVisible) return@forEach
                val bounds = world.get<MeshBounds>(entity)
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

                val pose = world.get<SkinnedPose>(entity)
                val extras = pose?.jointPalette ?: EMPTY_DRAW_EXTRAS

                for (slot in modularCharacter.slots.values) {
                    if (!slot.isVisible) continue
                    drawCalls.add(
                        RenderDrawCommand(
                            mesh = slot.mesh,
                            material = slot.material,
                            model = transform.worldMatrix,
                            extraUniformFloats = extras,
                            timeSeconds = elapsedTimeSeconds,
                        ),
                    )
                }
            }
        return drawCalls
    }

    fun collectAfterParticles(world: World, culling: FrameCulling, camera: Camera): ArrayList<RenderDrawCommand> {
        val drawCalls = ArrayList<RenderDrawCommand>()
        // LodGroup picks ONE level's mesh/material by distance to the camera eye -- see that
        // component's own doc comment for why an entity carries this instead of MeshRenderer,
        // not both. LOD selects detail, it doesn't cull -- MeshBounds/frustum culling still
        // applies on top when present.
        world.family<Transform, LodGroup>().forEach { entity, transform, lodGroup ->
            val worldPosition = Vec3(
                transform.worldMatrix.m03,
                transform.worldMatrix.m13,
                transform.worldMatrix.m23,
            )
            val distance = (worldPosition - camera.lens.eye).length3()
            // Selection remembers what it drew last frame -- see LodGroup.selectLevel for why a
            // bare threshold flickers for an entity parked near one.
            val level = lodGroup.selectLevel(distance)

            val bounds = world.get<MeshBounds>(entity)
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
                RenderDrawCommand(
                    mesh = level.mesh,
                    material = level.material,
                    model = transform.worldMatrix,
                ),
            )
        }

        return drawCalls
    }
}

private val EMPTY_DRAW_EXTRAS = FloatArray(0)
