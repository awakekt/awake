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
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Draws every [TerrainComponent] entity by attaching a terrain content feature to [host], and
 * detaches it when the entity or its component goes away.
 *
 * A [TerrainSurfaceReference] beside the component picks the surface from [providers]; without
 * one, or when its provider is not installed, the terrain draws with [baseShaders] and the scene
 * keeps the reference unchanged.
 *
 * Providers resolve in [scope], which may resume anywhere. Attaching creates a pipeline and
 * uploads textures, so it starts from [update] on the frame thread instead. A host accepts one
 * terrain per shader set; a second is logged and not drawn.
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

    /** A list, not a map: a scene holds a handful of terrains, and indexed loops allocate nothing. */
    private val tracked = ArrayList<Tracked>()
    private val resolved = Channel<Resolved>(Channel.UNLIMITED)
    private val attached = Channel<Attached>(Channel.UNLIMITED)

    /** The world [trackNew] reads, set for the length of one [update]. */
    private var frameWorld: World? = null

    /** A field rather than a lambda in [update], so querying allocates nothing per frame. */
    private val trackNew: (Entity, TerrainComponent) -> Unit = { entity, terrain ->
        if (find(entity) == null) {
            val reference = frameWorld?.get<TerrainSurfaceReference>(entity)
            if (reference != null || !surfacedOnly) track(entity, terrain, reference)
        }
    }

    override fun update(world: World, delta: Float) {
        releaseRemoved(world)
        applyAttached()
        applyResolved()
        frameWorld = world
        world.queryEach(TerrainComponent::class, trackNew)
        frameWorld = null
    }

    /** Detaches every terrain this system attached and cancels what is still resolving. */
    fun detachAll() {
        tracked.forEach(Tracked::release)
        tracked.clear()
        while (true) {
            val pending = attached.tryReceive().getOrNull() ?: break
            pending.feature.detach()
        }
        while (resolved.tryReceive().getOrNull() != null) Unit
    }

    private fun releaseRemoved(world: World) {
        for (index in tracked.indices.reversed()) {
            val entry = tracked[index]
            val current = if (world.isAlive(entry.entity)) world.get<TerrainComponent>(entry.entity) else null
            if (current !== entry.terrain) {
                entry.release()
                tracked.removeAt(index)
            }
        }
    }

    /** A result for an entity that has since gone, or changed its terrain, is freed straight away. */
    private fun applyAttached() {
        while (true) {
            val result = attached.tryReceive().getOrNull() ?: return
            val entry = find(result.entity)?.takeIf { it.terrain === result.terrain }
            if (entry == null) result.feature.detach() else entry.feature = result.feature
        }
    }

    private fun applyResolved() {
        while (true) {
            val result = resolved.tryReceive().getOrNull() ?: return
            val entry = find(result.entity)?.takeIf { it.terrain === result.terrain } ?: continue
            startAttach(entry, result.surface)
        }
    }

    // TooGenericExceptionCaught: a provider that fails for any reason still leaves a drawn terrain.
    @Suppress("TooGenericExceptionCaught")
    private fun track(entity: Entity, terrain: TerrainComponent, reference: TerrainSurfaceReference?) {
        val entry = Tracked(entity, terrain).also { tracked += it }
        val provider = reference?.let { providers[it.provider] }
        if (reference == null || provider == null) {
            if (reference != null) {
                log.warn {
                    "No terrain surface provider '${reference.provider}' is installed; drawing " +
                        "base shading. The scene keeps its surface unchanged."
                }
            }
            startAttach(entry, TerrainSurface(baseShaders))
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
            resolved.trySend(Resolved(entity, terrain, surface))
        }
    }

    // TooGenericExceptionCaught: whatever stops one terrain attaching must not stop the frame.
    @Suppress("TooGenericExceptionCaught")
    private fun startAttach(entry: Tracked, surface: TerrainSurface) {
        entry.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val feature = host.attachContentFeature(
                    terrainContentFeature(surface.shaders, entry.terrain, surface.textures),
                )
                attached.trySend(Attached(entry.entity, entry.terrain, feature))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error(failure) { "Terrain on entity ${entry.entity} could not be attached and will not be drawn." }
            }
        }
    }

    private fun find(entity: Entity): Tracked? {
        for (index in tracked.indices) if (tracked[index].entity == entity) return tracked[index]
        return null
    }

    private class Tracked(val entity: Entity, val terrain: TerrainComponent) {
        var job: Job? = null
        var feature: AttachedContentFeature? = null

        fun release() {
            job?.cancel()
            feature?.detach()
        }
    }

    private class Resolved(val entity: Entity, val terrain: TerrainComponent, val surface: TerrainSurface)

    private class Attached(val entity: Entity, val terrain: TerrainComponent, val feature: AttachedContentFeature)
}
