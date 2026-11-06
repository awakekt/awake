/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetLoads
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Reads and decodes every image [document]'s `particle_emitter`s name, by path, for a
 * [ParticleContentSystem]. One that fails to read or decode is logged and left out, and its
 * emitters draw nothing.
 */
// TooGenericExceptionCaught: an image that fails to read or decode for any reason only loses its emitters.
@Suppress("TooGenericExceptionCaught")
suspend fun loadParticleSprites(document: SceneDocument, assets: AssetSource): Map<String, TextureAsset> {
    val paths = document.nodes.flatMap { it.particleTextures() }.distinct()
    return buildMap {
        for (path in paths) {
            try {
                val bitmap = createBitmap(assets.read(AssetPath(path)).getOrThrow())
                put(path, TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn { "Particle sprite '$path' could not load: ${failure.message}" }
            }
        }
    }
}

/**
 * [loadParticleSprites] for a texture that only goes to the GPU: each image decoded with [decodeImage],
 * which a browser keeps for WebGPU to copy without reading it back (awakekt/awake#1184). Elsewhere the
 * pixels are on the CPU, as [loadParticleSprites] reads them. Give the result to [ParticleContentSystem].
 */
// TooGenericExceptionCaught: an image that fails to read or decode for any reason only loses its emitters.
@Suppress("TooGenericExceptionCaught")
suspend fun loadParticleImages(document: SceneDocument, assets: AssetSource): Map<String, GpuImageSource> {
    val paths = document.nodes.flatMap { it.particleTextures() }.distinct()
    return buildMap {
        for (path in paths) {
            try {
                put(path, loadParticleImage(path, assets))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn { "Particle sprite '$path' could not load: ${failure.message}" }
            }
        }
    }
}

/** Reads and decodes the one image at [path], as [loadParticleImages] does for each; throws when it can't. */
suspend fun loadParticleImage(path: String, assets: AssetSource): GpuImageSource =
    decodeImage(assets.read(AssetPath(path)).getOrThrow()).asGpuImageSource()

/**
 * The images a scene's `particle_emitter`s show, decoded by [loadParticleImage] from [assets] as they are
 * first asked for, and shared: give the same one to the scene's [ParticleContentSystem] and to whatever
 * prepares textures ahead of a spawn, so an image is read once. [preloaded] images, such as
 * [loadParticleImages] read when the scene loaded, are kept for as long as it lives. Close it when the
 * scene stops.
 *
 * @param assets Where the images are read from.
 * @param preloaded Images already decoded, kept for as long as the loads live.
 * @param context Where an image decodes when the one asking names no scope; [Dispatchers.Default] by default.
 */
fun particleImageLoads(
    assets: AssetSource,
    preloaded: Map<String, GpuImageSource> = emptyMap(),
    context: CoroutineContext = Dispatchers.Default,
): AssetLoads<GpuImageSource> = AssetLoads(context) { path -> loadParticleImage(path, assets) }.apply {
    preloaded.forEach { (path, value) -> put(path, value) }
}

private fun SceneNode.particleTextures(): List<String> =
    components.filterIsInstance<SceneParticleEmitter>().map { it.texture } + children.flatMap { it.particleTextures() }

private val log = Logger("scene-particles")
