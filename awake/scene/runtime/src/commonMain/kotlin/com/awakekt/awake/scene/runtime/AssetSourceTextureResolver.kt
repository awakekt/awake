/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.core.host.readDeviceProfile
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.decodeDispatcher
import com.awakekt.awake.core.pool.BoundedLruCache
import com.awakekt.awake.engine.platform.error.childScope
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.asReleasingGpuImageSource
import com.awakekt.awake.render.texture.releaseImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Loads the textures a scene's `sprite`s and `tilemap`s name from [files], such as a project's, so a
 * scene draws them without registering each one when it is built. A texture name is a path in [files],
 * as a `particle_emitter`'s is.
 *
 * Install it with the scene's assets (`assets { resolver(AssetSourceTextureResolver(files)) }`). Its
 * loads run in a child of the scene's `SceneSession.coroutineScope`, which the session [attach]es before
 * its first frame, so disposing the scene stops them; used outside a scene session, it is given the
 * scope its loads run under instead. The first time a frame draws something with a texture it hasn't
 * seen, it starts reading and decoding the file in [context], off the frame, and answers
 * [SceneTextureLoad.Loading]: the sprite or tilemap is not
 * drawn yet. A later frame finds it loaded, and the frame uploads it and draws it from then on. A file
 * that can't be read or decoded answers [SceneTextureLoad.Failed], and what uses it is not drawn. Each
 * is read once until it is forgotten or evicted by the LRU cache; a texture registered by name with
 * `texture(name) { ... }` is used instead of this.
 *
 * Loaded entries are retained in a [BoundedLruCache] capped by [maxCachedEntries] (scaled by
 * [DeviceProfile]). Evicted, forgotten, trimmed, or closed entries release their CPU image and
 * encoded byte payloads via [releaseImageData] while leaving already-uploaded GPU textures intact.
 *
 * Asked on the frame thread only. [close] it when the scene stops, which drops what is still loading.
 *
 * A [cooked] loader is tried first for each texture, such as one that loads the KTX2 file the texture
 * was cooked to (`awake:asset:texture-ktx-loader`'s `CookedTextureLoader`, #1211); the file itself is
 * decoded when it answers null.
 *
 * @param files Where the textures are read from.
 * @param context Where files are read and decoded, added to the session's scope; [decodeDispatcher]
 *   by default. It holds no `Job`.
 * @param cooked Loads a texture another way before its file is decoded, or answers null to decode it.
 */
class AssetSourceTextureResolver(
    private val files: AssetSource,
    private val context: CoroutineContext,
    private val cooked: (suspend (name: String) -> GpuImageSource?)?,
) : SceneAssetResolver,
    AutoCloseable {
    /** Reads each texture from [files] in [context] and decodes it, with no [cooked] loader. */
    constructor(files: AssetSource, context: CoroutineContext = decodeDispatcher) : this(files, context, null)

    /**
     * Reads each texture from [files] in a child of [scope], on its dispatcher, for a resolver used
     * outside a scene session: cancelling [scope] stops its loads as [close] does.
     */
    constructor(
        files: AssetSource,
        scope: CoroutineScope,
        cooked: (suspend (name: String) -> GpuImageSource?)? = null,
    ) : this(files, EmptyCoroutineContext, cooked) {
        loads = scope.childScope()
    }

    /** Maximum number of settled texture entries retained in CPU memory before LRU eviction. */
    var maxCachedEntries: Int = defaultMaxCachedEntries()
        set(value) {
            field = value.coerceAtLeast(1)
            textures.evictOverLimit(pinned = loading.keys, onEvict = ::releaseLoad)
        }

    // A child of the scope the session attached, or the one given; null until then.
    private var loads: CoroutineScope? = null
    private val finished = Channel<Finished>(Channel.UNLIMITED)

    // Read and written on the frame thread only; loads hand their results over through [finished].
    private val textures = BoundedLruCache<String, SceneTextureLoad> { maxCachedEntries }

    // The load each texture still waits for. A result from any other load, one forgotten since, is dropped.
    private val loading = HashMap<String, Job>()
    private var closed = false

    /** Number of texture entries currently retained in the LRU cache, for tests and diagnostics. */
    internal val cachedEntryCount: Int get() = textures.size

    /** Runs this resolver's loads in a child of the scene session's [sessionScope], in [context]. */
    override fun attach(sessionScope: CoroutineScope) {
        if (closed) return
        // Loads begun under a scope it was given are dropped, so each texture is asked for again under this one.
        loads?.let { previous ->
            forgetTextures()
            previous.cancel()
        }
        loads = sessionScope.childScope(context)
    }

    override fun texture(name: String): SceneTextureLoad {
        receiveFinished()
        val existing = textures.get(name)
        if (existing != null) return existing
        if (!closed) {
            val scope = checkNotNull(loads) {
                "AssetSourceTextureResolver loads in its scene session's scope: install it with the scene's assets, " +
                    "or give it the scope its loads run under."
            }
            textures.put(name, SceneTextureLoad.Loading, pinned = loading.keys + name, onEvict = ::releaseLoad)
            loading[name] = scope.launch {
                val result = read(name)
                finished.trySend(Finished(name, coroutineContext.job, result))
            }
            // A context that runs the load at once, as a test's does, has finished it already.
            receiveFinished()
        }
        return textures.get(name) ?: SceneTextureLoad.Loading
    }

    /** Drops the texture [name], and stops reading it if it is still loading; the next ask reads it again. */
    override fun forgetTexture(name: String) {
        textures.remove(name)?.let { releaseLoad(name, it) }
        loading.remove(name)?.cancel()
    }

    /** [forgetTexture] for every texture, such as when the files now hold another project. */
    override fun forgetTextures() {
        textures.trimAll(::releaseLoad)
        loading.values.forEach { it.cancel() }
        loading.clear()
    }

    /**
     * Releases all settled cached textures (dropping retained decoded pixels and compressed file
     * bytes) without cancelling active loads or destroying already-uploaded GPU textures.
     */
    override fun trimMemory() {
        receiveFinished()
        if (loading.isEmpty()) {
            textures.trimAll(::releaseLoad)
            return
        }
        val active = HashMap<String, SceneTextureLoad>(loading.size)
        for (name in loading.keys) {
            textures.remove(name)?.let { active[name] = it }
        }
        textures.trimAll(::releaseLoad)
        for ((name, load) in active) {
            textures.put(name, load, pinned = loading.keys, onEvict = ::releaseLoad)
        }
    }

    /** Stops what is still loading and drops it. Asking after this starts no new load. */
    override fun close() {
        if (closed) return
        closed = true
        loads?.cancel()
        finished.close()
        textures.trimAll(::releaseLoad)
        loading.clear()
    }

    private fun receiveFinished() {
        while (true) {
            val done = finished.tryReceive().getOrNull() ?: return
            if (loading[done.name] !== done.load) continue
            loading.remove(done.name)
            val load = done.result.fold({ SceneTextureLoad.Loaded(it) }, { SceneTextureLoad.Failed(it) })
            textures.put(done.name, load, pinned = loading.keys + done.name, onEvict = ::releaseLoad)
        }
    }

    private fun releaseLoad(
        @Suppress("UNUSED_PARAMETER") name: String,
        load: SceneTextureLoad,
    ) {
        (load as? SceneTextureLoad.Loaded)?.source?.releaseImageData()
    }

    // TooGenericExceptionCaught: a file that fails to read or decode, for whatever reason, only fails its texture.
    @Suppress("TooGenericExceptionCaught")
    // decodeImage: a browser keeps the decoded image for the GPU, which copies it without a readback;
    // elsewhere asReleasingGpuImageSource drops the decoded RGBA8 pixels once uploaded.
    private suspend fun read(name: String): Result<GpuImageSource> = try {
        val source = cooked?.invoke(name) ?: files.read(AssetPath(name)).getOrThrow().let { bytes ->
            decodeImage(bytes).asReleasingGpuImageSource(bytes)
        }
        Result.success(source)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private class Finished(val name: String, val load: Job, val result: Result<GpuImageSource>)

    /** Device-scaled LRU cache defaults for [AssetSourceTextureResolver]. */
    companion object {
        private const val LOW_RAM_BYTES: Long = 4L * 1024L * 1024L * 1024L

        /** Default LRU ceiling for low-RAM / 4 GB devices. */
        const val LOW_RAM_MAX_CACHED_ENTRIES: Int = 64

        /** Default LRU ceiling for devices with more than 4 GB RAM. */
        const val DEFAULT_MAX_CACHED_ENTRIES: Int = 256

        /** Picks a conservative texture cache ceiling for [profile]. */
        fun defaultMaxCachedEntries(profile: DeviceProfile = readDeviceProfile()): Int {
            val total = profile.totalMemoryBytes
            val isLowRam = profile.isLowRamDevice || (total != null && total in 1L..LOW_RAM_BYTES)
            return if (isLowRam) LOW_RAM_MAX_CACHED_ENTRIES else DEFAULT_MAX_CACHED_ENTRIES
        }
    }
}
