/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

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
 * read from [assets] off the frame thread and attached through [host]. Another path or exposure
 * replaces it; leaving cubemap mode, disabling the sky or removing it detaches it. A strip that
 * fails to load is logged and leaves the sky as it was.
 */
class SkyboxCubemapSystem(
    private val host: ContentFeatureHost,
    private val scope: CoroutineScope,
    private val assets: AssetSource,
) : System {
    private val log = Logger("scene-sky")
    private val loaded = Channel<Loaded>(Channel.UNLIMITED)
    private var wanted: Key? = null
    private var shown: AttachedContentFeature? = null
    private var loading: Job? = null

    override fun update(world: World, delta: Float) {
        while (true) {
            val result = loaded.tryReceive().getOrNull() ?: break
            if (result.key == wanted) {
                shown?.detach()
                shown = result.feature
            } else {
                result.feature.detach()
            }
        }
        val sky = world.firstOrNull<Skybox>()
        val mode = (sky?.mode as? Skybox.Mode.Cubemap)?.takeIf { sky.enabled && it.assetPath.isNotEmpty() }
        val key = mode?.let { Key(it.assetPath, it.exposure) }
        if (key == wanted) return
        wanted = key
        loading?.cancel()
        if (key == null) {
            shown?.detach()
            shown = null
            return
        }
        loading = scope.launch { load(key) }
    }

    // TooGenericExceptionCaught: a strip that fails to read or decode for any reason keeps the old sky.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(key: Key) {
        try {
            val cubemap = decodeCubemapStrip(assets.read(AssetPath(key.path)).getOrThrow())
            loaded.trySend(Loaded(key, host.attachContentFeature(skyboxCubemapContentFeature(cubemap, key.exposure))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sky '${key.path}' could not load: ${failure.message}" }
        }
    }

    private data class Key(val path: String, val exposure: Float)

    private class Loaded(val key: Key, val feature: AttachedContentFeature)
}
