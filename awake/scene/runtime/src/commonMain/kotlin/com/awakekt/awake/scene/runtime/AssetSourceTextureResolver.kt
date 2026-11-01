/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.asGpuImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
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
 * is read once until it is forgotten; a texture registered by name with `texture(name) { ... }` is used
 * instead of this.
 *
 * A texture is kept until [forgetTexture] or [forgetTextures] drops it, which a host does through
 * [SceneAssetLibrary.forgetTexture] when a file changes, so the frame also lets go of what it uploaded.
 * The next ask reads the file again, so an edited image redraws and a failed one is tried again.
 *
 * Asked on the frame thread only. [close] it when the scene stops, which drops what is still loading.
 *
 * A [cooked] loader is tried first for each texture, such as one that loads the KTX2 file the texture
 * was cooked to (`awake:asset:texture-ktx-loader`'s `CookedTextureLoader`, #1211); the file itself is
 * decoded when it answers null.
 *
 * @param files Where the textures are read from.
 * @param context Where files are read and decoded; [Dispatchers.Default] by default.
 * @param cooked Loads a texture another way before its file is decoded, or answers null to decode it.
 */
class AssetSourceTextureResolver(
    private val files: AssetSource,
    context: CoroutineContext,
    private val cooked: (suspend (name: String) -> GpuImageSource?)?,
) : SceneAssetResolver,
    AutoCloseable {
    /** Reads each texture from [files] in [context] and decodes it, with no [cooked] loader. */
    constructor(files: AssetSource, context: CoroutineContext = Dispatchers.Default) : this(files, context, null)

    private val loads = CoroutineScope(SupervisorJob() + context)
    private val finished = Channel<Finished>(Channel.UNLIMITED)

    // Read and written on the frame thread only; loads hand their results over through [finished].
    private val textures = HashMap<String, SceneTextureLoad>()

    // The load each texture still waits for. A result from any other load, one forgotten since, is dropped.
    private val loading = HashMap<String, Job>()
    private var closed = false

    override fun texture(name: String): SceneTextureLoad {
        receiveFinished()
        if (name !in textures && !closed) {
            textures[name] = SceneTextureLoad.Loading
            loading[name] = loads.launch {
                val result = read(name)
                finished.trySend(Finished(name, coroutineContext.job, result))
            }
            // A context that runs the load at once, as a test's does, has finished it already.
            receiveFinished()
        }
        return textures[name] ?: SceneTextureLoad.Loading
    }

    /** Drops the texture [name], and stops reading it if it is still loading; the next ask reads it again. */
    override fun forgetTexture(name: String) {
        textures.remove(name)
        loading.remove(name)?.cancel()
    }

    /** [forgetTexture] for every texture, such as when the files now hold another project. */
    override fun forgetTextures() {
        textures.clear()
        loading.values.forEach { it.cancel() }
        loading.clear()
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
            if (loading[done.name] !== done.load) continue
            loading.remove(done.name)
            textures[done.name] = done.result.fold({ SceneTextureLoad.Loaded(it) }, { SceneTextureLoad.Failed(it) })
        }
    }

    // TooGenericExceptionCaught: a file that fails to read or decode, for whatever reason, only fails its texture.
    @Suppress("TooGenericExceptionCaught")
    // decodeImage: a browser keeps the decoded image for the GPU, which copies it without a readback.
    private suspend fun read(name: String): Result<GpuImageSource> = try {
        Result.success(cooked?.invoke(name) ?: decodeImage(files.read(AssetPath(name)).getOrThrow()).asGpuImageSource())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private class Finished(val name: String, val load: Job, val result: Result<GpuImageSource>)
}
