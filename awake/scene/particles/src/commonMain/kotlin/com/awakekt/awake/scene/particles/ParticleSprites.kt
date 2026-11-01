/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.CancellationException

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
                put(path, decodeImage(assets.read(AssetPath(path)).getOrThrow()).asGpuImageSource())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn { "Particle sprite '$path' could not load: ${failure.message}" }
            }
        }
    }
}

private fun SceneNode.particleTextures(): List<String> =
    components.filterIsInstance<SceneParticleEmitter>().map { it.texture } + children.flatMap { it.particleTextures() }

private val log = Logger("scene-particles")
