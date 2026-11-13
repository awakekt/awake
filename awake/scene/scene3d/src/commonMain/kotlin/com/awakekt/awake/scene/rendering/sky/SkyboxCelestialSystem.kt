/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.asset.shaderpack.CelestialLook
import com.awakekt.awake.asset.shaderpack.celestialContentFeature
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Draws the scene's sun and moon over its sky, whatever the sky is.
 *
 * Active while the first [Skybox] [drawsCelestials][Skybox.drawsCelestials]. Its sun and moon images
 * are read from [assets] off the frame thread and attached through [host]; the moon's phase changes
 * every frame without a reload. An image that fails to load is logged and that body draws its disc.
 *
 * It draws after the sky it sits over, so give it the [cubemap] system: when the cubemap sky changes,
 * this re-attaches behind it.
 */
class SkyboxCelestialSystem(
    private val host: ContentFeatureHost,
    private val scope: CoroutineScope,
    private val assets: AssetSource,
    private val cubemap: SkyboxCubemapSystem? = null,
) : System {
    private val log = Logger("scene-sky")
    private val loaded = Channel<Loaded>(Channel.UNLIMITED)
    private val look = CelestialLook()
    private var textures: Pair<Key, Textures>? = null
    private var wanted: Key? = null
    private var pending: Pair<Key, Int>? = null
    private var shown: Loaded? = null
    private var loading: Job? = null

    override fun update(world: World, delta: Float) {
        while (true) {
            val result = loaded.tryReceive().getOrNull() ?: break
            if (result.key == wanted) {
                shown?.feature?.detach()
                shown = result
            } else {
                result.feature.detach()
            }
            pending = null
        }
        val sky = world.firstOrNull<Skybox>()?.takeIf { it.drawsCelestials }
        look.moonPhase = sky?.moonPhase ?: FULL_MOON
        val key = sky?.let { Key(it.sunTexture, it.moonTexture) }
        if (key != wanted) {
            wanted = key
            loading?.cancel()
            pending = null
        }
        if (key == null) {
            shown?.feature?.detach()
            shown = null
            return
        }
        val generation = cubemap?.generation ?: 0
        val current = shown
        if ((current?.key == key && current.generation == generation) || pending == key to generation) return
        pending = key to generation
        loading?.cancel()
        loading = scope.launch { load(key, generation) }
    }

    // TooGenericExceptionCaught: an image that fails to read or decode for any reason draws its disc.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(key: Key, generation: Int) {
        try {
            val images = textures?.takeIf { it.first == key }?.second ?: Textures(read(key.sun), read(key.moon))
            textures = key to images
            val feature = host.attachContentFeature(celestialContentFeature(images.sun, images.moon, look))
            loaded.trySend(Loaded(key, generation, feature))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sun and moon could not draw: ${failure.message}" }
        }
    }

    // TooGenericExceptionCaught: see load.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(path: String?): TextureAsset? {
        if (path == null) return null
        return try {
            decodeBodyTexture(assets.read(AssetPath(path)).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sky image '$path' could not load: ${failure.message}" }
            null
        }
    }

    private data class Key(val sun: String?, val moon: String?)

    private class Textures(val sun: TextureAsset?, val moon: TextureAsset?)

    private class Loaded(val key: Key, val generation: Int, val feature: AttachedContentFeature)
}
