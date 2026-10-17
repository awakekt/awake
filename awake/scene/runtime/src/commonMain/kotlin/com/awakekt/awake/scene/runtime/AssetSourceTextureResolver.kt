/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Loads the textures a scene's `sprite`s and `tilemap`s name from [files], such as a project's, so a
 * scene draws them without registering each one when it is built. A texture name is a path in [files],
 * as a `particle_emitter`'s is.
 *
 * Install it with the scene's assets (`assets { resolver(AssetSourceTextureResolver(files)) }`). The
 * first time a frame draws something with a texture it hasn't seen, it starts reading and decoding the
 * file in [context], off the frame, and answers [SceneTextureLoad.Loading]: the sprite or tilemap is not
 * drawn yet. A later frame finds it loaded, and the frame uploads it and draws it from then on. A file
 * that can't be read or decoded answers [SceneTextureLoad.Failed], and what uses it is not drawn. Each
 * is read once; a texture registered by name with `texture(name) { ... }` is used instead of this.
 *
 * Asked on the frame thread only. [close] it when the scene stops, which drops what is still loading.
 *
 * @param files Where the textures are read from.
 * @param context Where files are read and decoded; [Dispatchers.Default] by default.
 */
class AssetSourceTextureResolver(
    private val files: AssetSource,
    context: CoroutineContext = Dispatchers.Default,
) : SceneAssetResolver,
    AutoCloseable {
    private val loads = CoroutineScope(SupervisorJob() + context)
    private val finished = Channel<Finished>(Channel.UNLIMITED)

    // Read and written on the frame thread only; loads hand their results over through [finished].
    private val textures = HashMap<String, SceneTextureLoad>()
    private var closed = false

    override fun texture(name: String): SceneTextureLoad {
        receiveFinished()
        if (name !in textures && !closed) {
            textures[name] = SceneTextureLoad.Loading
            loads.launch { finished.trySend(Finished(name, read(name))) }
            // A context that runs the load at once, as a test's does, has finished it already.
            receiveFinished()
        }
        return textures[name] ?: SceneTextureLoad.Loading
    }

    /** Stops what is still loading and drops it. Asking after this starts no new load. */
    override fun close() {
        if (closed) return
        closed = true
        loads.cancel()
        finished.close()
    }

    private fun receiveFinished() {
        while (true) {
            val done = finished.tryReceive().getOrNull() ?: return
            textures[done.name] = done.result.fold({ SceneTextureLoad.Loaded(it) }, { SceneTextureLoad.Failed(it) })
        }
    }

    // TooGenericExceptionCaught: a file that fails to read or decode, for whatever reason, only fails its texture.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(name: String): Result<TextureAsset> = try {
        val bitmap = createBitmap(files.read(AssetPath(name)).getOrThrow())
        Result.success(TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private class Finished(val name: String, val result: Result<TextureAsset>)
}
