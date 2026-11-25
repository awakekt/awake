/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.terrain.TerrainSurface
import com.awakekt.awake.terrain.TerrainSurfaceProvider
import com.awakekt.awake.terrain.TerrainSurfaceReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Draws every visible [TerrainComponent] entity by attaching a terrain content feature to [host],
 * and detaches it when the entity or its component goes away or becomes hidden.
 *
 * Terrains with `isVisible == false` (such as collider-only terrain areas) defer surface
 * resolution and GPU feature upload until `isVisible` becomes `true`, at which point [update]
 * lazily attaches them on that frame. Flipping `isVisible` back to `false` cancels any in-flight
 * resolution and detaches the GPU feature so its heightmap, surface textures, and uniform slots
 * are reclaimed immediately.
 *
 * A [TerrainSurfaceReference] beside the component picks the surface from [providers]; without
 * one, or when its provider is not installed, the terrain draws with [baseShaders] and the scene
 * keeps the reference unchanged.
 *
 * Each terrain lies at its entity's `Transform` translation, read every [update]: the heightmap's
 * centre goes there, as the terrain's collider does. Rotation and scale are not applied; a
 * heightmap's own scale sizes it.
 *
 * Providers resolve in [scope], which may resume anywhere. Attaching uploads textures, and the
 * first terrain of a shader set builds its pipeline, so it starts from [update] on the frame
 * thread instead. Any number of terrains draw at once; those sharing a shader set share a pipeline,
 * and those sharing a clipmap configuration share one reference-counted GPU clipmap mesh.
 *
 * With [surfacedOnly], a terrain without a reference is left alone, so another renderer (an
 * editor's mutable preview, say) can draw it.
 *
 * Call [detachAll] when the scene is disposed: systems have no dispose hook of their own.
 */
class TerrainContentSystem(
    private val host: ContentFeatureHost,
    private val scope: CoroutineScope,
    private val providers: Map<String, TerrainSurfaceProvider> = emptyMap(),
    private val baseShaders: ShaderSet = PackShaderSets.Terrain,
    private val surfacedOnly: Boolean = false,
) : System {
    private val log = Logger("scene-terrain")

    private val tracked = ArrayList<Tracked>()
    private var bySlot = arrayOfNulls<Tracked>(INITIAL_SLOT_CAPACITY)
    private val resolved = Channel<Resolved>(Channel.UNLIMITED)
    private val attached = Channel<Attached>(Channel.UNLIMITED)

    // Resolved once per world generation so per-frame updates use direct type ids rather than KClass lookups.
    private var resolvedWorld: World? = null
    private var resolvedGeneration = -1
    private var transformTypeId = ComponentTypeId(0)
    private var terrainTypeId = ComponentTypeId(0)
    private var referenceTypeId = ComponentTypeId(0)

    // Steady-state memoization: when neither terrain nor surface-reference families have changed,
    // no terrain has been added, removed, or replaced.
    private var syncedWorld: World? = null
    private var syncedGeneration = -1
    private var syncedTerrainChanges = -1
    private var syncedReferenceChanges = -1

    override fun update(world: World, delta: Float) {
        resolveTypes(world)
        val terrainFamily = world.family(TERRAIN_CLASS)
        val referenceFamily = world.family(REFERENCE_CLASS)
        val worldChanged = syncedWorld !== world || syncedGeneration != world.generation
        val familyChanged =
            syncedTerrainChanges != terrainFamily.changes || syncedReferenceChanges != referenceFamily.changes
        val membershipChanged = worldChanged || familyChanged
        if (membershipChanged) {
            releaseRemoved(world)
        }
        syncTracked(world)
        applyAttached()
        applyResolved()
        if (membershipChanged) {
            terrainFamily.forEach { entity, terrain ->
                if (find(entity) == null) {
                    val reference = world.get<TerrainSurfaceReference>(entity, referenceTypeId)
                    if (reference != null || !surfacedOnly) track(world, entity, terrain, reference)
                }
            }
            syncedWorld = world
            syncedGeneration = world.generation
            syncedTerrainChanges = terrainFamily.changes
            syncedReferenceChanges = referenceFamily.changes
        }
    }

    /** Detaches every terrain this system attached and cancels what is still resolving. */
    fun detachAll() {
        tracked.forEach(Tracked::release)
        tracked.clear()
        bySlot.fill(null)
        syncedWorld = null
        syncedGeneration = -1
        syncedTerrainChanges = -1
        syncedReferenceChanges = -1
        while (true) {
            val pending = attached.tryReceive().getOrNull() ?: break
            pending.feature.detach()
        }
        while (resolved.tryReceive().isSuccess) {
            // Drain remaining resolved completions.
        }
    }

    private fun resolveTypes(world: World) {
        if (resolvedWorld === world && resolvedGeneration == world.generation) return
        transformTypeId = world.typeId(TRANSFORM_CLASS)
        terrainTypeId = world.typeId(TERRAIN_CLASS)
        referenceTypeId = world.typeId(REFERENCE_CLASS)
        resolvedWorld = world
        resolvedGeneration = world.generation
    }

    private fun releaseRemoved(world: World) {
        for (index in tracked.indices.reversed()) {
            val entry = tracked[index]
            val current = if (world.isAlive(entry.entity)) world.get<TerrainComponent>(entry.entity, terrainTypeId) else null
            if (current !== entry.terrain) {
                entry.release()
                val slot = entry.entity.id
                if (slot < bySlot.size && bySlot[slot] === entry) bySlot[slot] = null
                tracked.removeAt(index)
            }
        }
    }

    /**
     * Updates visible terrains' world origins in place and lazily attaches/detaches when
     * `TerrainComponent.isVisible` toggles, allocating nothing on steady-state frames.
     */
    private fun syncTracked(world: World) {
        for (index in tracked.indices) {
            val entry = tracked[index]
            if (entry.terrain.isVisible) {
                terrainOrigin(world, entry.entity, entry.origin, transformTypeId)
                if (!entry.started) {
                    val reference = world.get<TerrainSurfaceReference>(entry.entity, referenceTypeId)
                    if (reference != null || !surfacedOnly) beginAttach(entry, reference)
                }
            } else if (entry.started) {
                entry.release()
            }
        }
    }

    /** A result for an entity that has since gone, changed its terrain, or hidden is freed straight away. */
    private fun applyAttached() {
        while (true) {
            val result = attached.tryReceive().getOrNull() ?: return
            val entry = find(result.entity)?.takeIf {
                it.terrain === result.terrain && it.started && it.attachEpoch == result.epoch && it.terrain.isVisible
            }
            if (entry == null) {
                result.feature.detach()
            } else {
                entry.feature?.detach()
                entry.feature = result.feature
            }
        }
    }

    private fun applyResolved() {
        while (true) {
            val result = resolved.tryReceive().getOrNull() ?: return
            val entry = find(result.entity)?.takeIf {
                it.terrain === result.terrain && it.started && it.attachEpoch == result.epoch && it.terrain.isVisible
            } ?: continue
            startAttach(entry, result.epoch, result.surface)
        }
    }

    private fun track(world: World, entity: Entity, terrain: TerrainComponent, reference: TerrainSurfaceReference?) {
        val entry = Tracked(entity, terrain).also {
            tracked += it
            val slot = entity.id
            if (slot >= bySlot.size) bySlot = bySlot.copyOf(maxOf(slot + 1, bySlot.size * 2))
            bySlot[slot] = it
        }
        if (!terrain.isVisible) return
        terrainOrigin(world, entity, entry.origin, transformTypeId)
        beginAttach(entry, reference)
    }

    // TooGenericExceptionCaught: a provider that fails for any reason still leaves a drawn terrain.
    @Suppress("TooGenericExceptionCaught")
    private fun beginAttach(entry: Tracked, reference: TerrainSurfaceReference?) {
        entry.started = true
        val epoch = ++entry.attachEpoch
        val provider = reference?.let { providers[it.provider] }
        if (reference == null || provider == null) {
            if (reference != null) {
                log.warn {
                    "No terrain surface provider '${reference.provider}' is installed; drawing " +
                        "base shading. The scene keeps its surface unchanged."
                }
            }
            startAttach(entry, epoch, TerrainSurface(baseShaders))
            return
        }
        entry.job = scope.launch {
            val surface = try {
                provider.resolve(reference)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error(failure) { "Terrain surface '${reference.provider}' failed to resolve; drawing base shading." }
                TerrainSurface(baseShaders)
            }
            resolved.trySend(Resolved(entry.entity, entry.terrain, epoch, surface))
        }
    }

    // TooGenericExceptionCaught: whatever stops one terrain attaching must not stop the frame.
    @Suppress("TooGenericExceptionCaught")
    private fun startAttach(entry: Tracked, epoch: Int, surface: TerrainSurface) {
        entry.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val feature = host.attachContentFeature(
                    terrainContentFeature(surface.shaders, entry.terrain, surface.textures, surface.samplerTextures) { entry.origin },
                )
                attached.trySend(Attached(entry.entity, entry.terrain, epoch, feature))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error(failure) { "Terrain on entity ${entry.entity} could not be attached and will not be drawn." }
            }
        }
    }

    private fun find(entity: Entity): Tracked? {
        val slot = entity.id
        val inSlot = if (slot < bySlot.size) bySlot[slot] else null
        return if (inSlot != null && inSlot.entity == entity) inSlot else null
    }

    private companion object {
        val TRANSFORM_CLASS = Transform::class
        val TERRAIN_CLASS = TerrainComponent::class
        val REFERENCE_CLASS = TerrainSurfaceReference::class
    }

    private class Tracked(val entity: Entity, val terrain: TerrainComponent) {
        /** The entity's translation, as of the last [update]; the drawn terrain reads it every frame. */
        val origin = Vec3f(0f, 0f, 0f)
        var started = false
        var attachEpoch = 0
        var job: Job? = null
        var feature: AttachedContentFeature? = null

        fun release() {
            started = false
            attachEpoch++
            job?.cancel()
            job = null
            feature?.detach()
            feature = null
        }
    }

    private class Resolved(
        val entity: Entity,
        val terrain: TerrainComponent,
        val epoch: Int,
        val surface: TerrainSurface,
    )

    private class Attached(
        val entity: Entity,
        val terrain: TerrainComponent,
        val epoch: Int,
        val feature: AttachedContentFeature,
    )
}

/**
 * Writes into [out] the translation [entity]'s terrain lies at in [world], or `(0, 0, 0)` without a
 * [Transform]. A child whose [Transform.worldMatrix] `TransformSystem` has built reads that matrix's
 * translation; before it has run, or in a world that runs none, it sums local positions up the parent
 * chain, since terrains apply neither rotation nor scale.
 */
internal fun terrainOrigin(world: World, entity: Entity, out: Vec3f): Vec3f =
    terrainOrigin(world, entity, out, world.typeId(Transform::class))

internal fun terrainOrigin(world: World, entity: Entity, out: Vec3f, transformTypeId: ComponentTypeId): Vec3f {
    val transform = world.get<Transform>(entity, transformTypeId)
    return when {
        transform == null -> out.set(0f, 0f, 0f)
        transform.parent == null -> out.set(transform.position)
        transform.worldVersion > 0 -> {
            val placed = transform.worldMatrix
            out.set(placed.m03, placed.m13, placed.m23)
        }
        else -> sumParentPositions(world, transform, out, transformTypeId)
    }
}

private fun sumParentPositions(world: World, start: Transform, out: Vec3f, transformTypeId: ComponentTypeId): Vec3f {
    var x = start.position.x
    var y = start.position.y
    var z = start.position.z
    var parent = start.parent
    var depth = 0
    while (parent != null && depth < MAX_TERRAIN_PARENT_DEPTH) {
        val above = world.get<Transform>(parent, transformTypeId)
        if (above == null) {
            parent = null
        } else if (above.parent != null && above.worldVersion > 0) {
            val placed = above.worldMatrix
            x += placed.m03
            y += placed.m13
            z += placed.m23
            parent = null
        } else {
            x += above.position.x
            y += above.position.y
            z += above.position.z
            parent = above.parent
            depth++
        }
    }
    return out.set(x, y, z)
}

private const val MAX_TERRAIN_PARENT_DEPTH = 64
private const val INITIAL_SLOT_CAPACITY = 64
