/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.asset.shaderpack.SunFlareLook
import com.awakekt.awake.asset.shaderpack.sunFlareContentFeature
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.rendering.light.DayCycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Attaches and drives the scene's optical sun flare while the first [Skybox] has [Skybox.drawsSunFlare]
 * enabled (#1537 Stage C). Follows the `day_cycle` sun direction and visibility window when present,
 * or the pass's directional light otherwise. Steady frames allocate nothing.
 */
class SkyboxSunFlareSystem(
    private val host: ContentFeatureHost,
    private val scope: CoroutineScope,
    private val assets: AssetSource,
    private val occludeByDepth: Boolean = true,
) : System {
    private val log = Logger("scene-sky")
    private val loaded = Channel<Loaded>(Channel.UNLIMITED)
    private val look = SunFlareLook()
    private var cachedTexture: Pair<String?, TextureAsset?>? = null
    private var wantedKey: Key? = null
    private var pendingKey: Key? = null
    private var shown: Loaded? = null
    private var loading: Job? = null

    override fun update(world: World, delta: Float) {
        while (true) {
            val result = loaded.tryReceive().getOrNull() ?: break
            if (result.key == wantedKey) {
                shown?.feature?.detach()
                shown = result
            } else {
                result.feature.detach()
            }
            pendingKey = null
        }
        val sky = world.firstOrNull<Skybox>()?.takeIf { it.drawsSunFlare }
        syncLook(world, sky)
        val key = sky?.let { Key(it.sunFlareTexture) }
        if (key != wantedKey) {
            wantedKey = key
            loading?.cancel()
            pendingKey = null
        }
        if (key == null) {
            shown?.feature?.detach()
            shown = null
            return
        }
        if (shown?.key == key || pendingKey == key) return
        pendingKey = key
        loading?.cancel()
        loading = scope.launch { load(key) }
    }

    private fun syncLook(world: World, sky: Skybox?) {
        look.enabled = sky != null
        look.intensity = sky?.sunFlareIntensity ?: 0f
        val cycle = world.firstOrNull<DayCycle>()
        look.followsDirection = cycle != null
        if (cycle == null) {
            look.sunVisibility = 1f
            return
        }
        look.sunDirection.set(cycle.sunDirection.x, cycle.sunDirection.y, cycle.sunDirection.z)
        look.sunVisibility = cycle.sunVisibility
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun load(key: Key) {
        try {
            val image = cachedTexture?.takeIf { it.first == key.texture }?.second ?: read(key.texture)
            cachedTexture = key.texture to image
            val feature = host.attachContentFeature(sunFlareContentFeature(image, look, occludeByDepth = occludeByDepth))
            loaded.trySend(Loaded(key, feature))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sun flare could not draw: ${failure.message}" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(path: String?): TextureAsset? {
        if (path == null) return null
        return try {
            decodeBodyTexture(assets.read(AssetPath(path)).getOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn { "Sun flare texture '$path' could not load: ${failure.message}" }
            null
        }
    }

    private data class Key(val texture: String?)

    private class Loaded(val key: Key, val feature: AttachedContentFeature)
}
