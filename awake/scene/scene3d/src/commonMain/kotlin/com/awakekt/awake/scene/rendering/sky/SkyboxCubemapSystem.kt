/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.asset.shaderpack.SkyboxCubemapLook
import com.awakekt.awake.asset.shaderpack.skyboxCubemapContentFeature
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Draws the scene's cubemap sky.
 *
 * The first enabled [Skybox] in [Skybox.Mode.Cubemap] has its strip (see [decodeCubemapStrip])
 * read from [assets] off the frame thread and attached through [host]. With panoramas, the two a
 * `day_cycle` is blending are loaded, and another pair replaces them once loaded. Another path
 * replaces it; leaving cubemap mode, disabling the sky or removing it detaches it. A strip that
 * fails to load is logged and leaves the sky as it was. Exposure, tint and blend change every
 * frame without a reload.
 */
class SkyboxCubemapSystem(
    private val host: ContentFeatureHost,
    private val scope: CoroutineScope,
    private val assets: AssetSource,
) : System {
    private val log = Logger("scene-sky")
    private val loaded = Channel<Loaded>(Channel.UNLIMITED)
    private var wanted: Key? = null
    private var shown: Loaded? = null
    private var loading: Job? = null

    /**
     * Counts each time the sky on screen changes: a panorama attached, replaced or detached. Anything
     * drawn over the sky re-attaches when it moves, to stay after the sky in the draw order.
     */
    var generation: Int = 0
        private set

    override fun update(world: World, delta: Float) {
        while (true) {
            val result = loaded.tryReceive().getOrNull() ?: break
            if (result.key == wanted) {
                shown?.feature?.detach()
                shown = result
                generation++
            } else {
                result.feature.detach()
            }
        }
        val sky = world.firstOrNull<Skybox>()
        val mode = (sky?.mode as? Skybox.Mode.Cubemap)?.takeIf { sky.enabled && it.pathAt(it.blendFrom).isNotEmpty() }
        val key = mode?.let { Key(it.pathAt(it.blendFrom), it.pathAt(it.blendTo)) }
        if (mode != null) shown?.let { it.look(mode) }
        if (key == wanted) return
        wanted = key
        loading?.cancel()
        if (key == null) {
            if (shown != null) generation++
            shown?.feature?.detach()
            shown = null
            return
        }
        loading = scope.launch { load(key, mode?.exposure ?: 1f) }
    }

    // TooGenericExceptionCaught: a strip that fails to read or decode for any reason keeps the old sky.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(key: Key, exposure: Float) {
        try {
            val from = decodeCubemapStrip(assets.read(AssetPath(key.from)).getOrThrow())
            val to = if (key.to == key.from) from else decodeCubemapStrip(assets.read(AssetPath(key.to)).getOrThrow())
            val look = SkyboxCubemapLook(exposure)
            val feature = host.attachContentFeature(skyboxCubemapContentFeature(from, exposure, blendTo = to, look = look))
            loaded.trySend(Loaded(key, feature, look))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sky '${key.from}' could not load: ${failure.message}" }
        }
    }

    private data class Key(val from: String, val to: String)

    /** A loaded sky and the [look] it draws with, for the panorama pair in [key]. */
    private class Loaded(val key: Key, val feature: AttachedContentFeature, val look: SkyboxCubemapLook) {
        /** Copies the day's exposure, tint and blend, turned around when the pair is showing the other way. */
        fun look(mode: Skybox.Mode.Cubemap) {
            look.exposure = mode.exposure * mode.dayExposure
            look.tintR = mode.tint.r
            look.tintG = mode.tint.g
            look.tintB = mode.tint.b
            val from = mode.pathAt(mode.blendFrom)
            val to = mode.pathAt(mode.blendTo)
            when {
                key.from == from && key.to == to -> look.blend = mode.blend
                key.from == to && key.to == from -> look.blend = 1f - mode.blend
            }
        }
    }
}

/** The path of panorama [index], or the single [Skybox.Mode.Cubemap.assetPath] when there are none. */
private fun Skybox.Mode.Cubemap.pathAt(index: Int): String =
    if (panoramas.isEmpty()) assetPath else panoramas[index.coerceIn(0, panoramas.lastIndex)].path
