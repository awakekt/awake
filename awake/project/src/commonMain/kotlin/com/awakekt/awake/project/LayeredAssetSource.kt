/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * A read [LayeredAssetSource] refused because [path] isn't in its public set and no protected source
 * is attached yet: the file may be protected content that only a signed-in player can read. A
 * caller that can wait, as a scene switch does, waits with [LayeredAssetSource.awaitProtected]
 * instead of failing.
 *
 * @property path The path asked for.
 */
class ProtectedContentNotAttachedException(val path: String) : IllegalStateException("$path is not in the public content set, and no protected source is attached yet")

/**
 * A project's files as two layers: [publicSet], the content anyone can read, such as the manifest, the
 * sign-in scene and what that scene draws, and a protected source the app [attaches][attachProtected]
 * once the player has signed in. A project loads and plays on [publicSet] alone, and its protected scenes
 * load once the protected source arrives.
 *
 * A read goes to exactly one layer, so no file is fetched twice: to the protected source when one is
 * attached and it [has][AssetSource.exists] the path, and to [publicSet] otherwise. The protected source
 * therefore wins for a file both layers have. Each layer checks its own files, as an
 * [IndexedAssetSource] checks every file and bundle member against its index's SHA-256, so the check
 * holds whichever layer serves the read. Both layers should answer [AssetSource.exists] without
 * fetching, as an [IndexedAssetSource] does; one that reads to answer costs a fetch per lookup.
 *
 * Before the protected source is attached, a read of a path [publicSet] doesn't have fails with
 * [ProtectedContentNotAttachedException] rather than waiting, so a public scene that names an
 * optional protected file loads without it. After it is attached, a path neither layer has fails as
 * the protected source fails it. A scene switch waits instead: as a [GatedAssetSource], a path that
 * [needsProtected] is gated until the protected source is attached, so a switch to a protected scene
 * before sign-in reports that it is waiting and loads once the source arrives.
 *
 * It prefetches through whichever layer would read each path, when that layer is a
 * [PrefetchableAssetSource] that can prefetch, so loading progress counts bytes through it as it would
 * through either layer alone. Paths in a layer that can't prefetch are left to be read as usual.
 *
 * Core takes the protected source and nothing else: the token, any key and when to sign in or out
 * are the app's. Typically [publicSet] is an [IndexedAssetSource] over the public index, which ships
 * next to the page, and the protected source an [IndexedAssetSource] opened with a
 * [TokenAuthorizedFetch] after sign-in. The public set is never encrypted; protected bundles may be.
 *
 * @param publicSet The public set: what loads before sign-in.
 */
class LayeredAssetSource(private val publicSet: AssetSource) :
    GatedAssetSource,
    PrefetchableAssetSource {
    private val protectedLayer = MutableStateFlow<AssetSource?>(null)

    /** Whether a protected source is attached. */
    val hasProtected: Boolean get() = protectedLayer.value != null

    /**
     * Attaches [source], the content a signed-in player can read; it then serves every path it
     * has. Attaching again replaces the source attached before, as a new sign-in does. Coroutines
     * waiting in [awaitProtected] resume.
     */
    fun attachProtected(source: AssetSource) {
        protectedLayer.value = source
    }

    /**
     * Detaches the protected source, as a sign-out does: later reads of a path [publicSet] doesn't have
     * fail with [ProtectedContentNotAttachedException] again. What was already read stays loaded.
     */
    fun detachProtected() {
        protectedLayer.value = null
    }

    /** Suspends until a protected source is attached, and answers it; at once when one already is. */
    suspend fun awaitProtected(): AssetSource = protectedLayer.filterNotNull().first()

    /**
     * Whether a read of [path] needs the protected source, which isn't attached yet: [publicSet] doesn't
     * have it and nothing else could serve it. A caller about to load a scene waits for
     * [awaitProtected] while this is true. False once a protected source is attached.
     */
    suspend fun needsProtected(path: AssetPath): Boolean = protectedLayer.value == null && !publicSet.exists(path)

    /** [needsProtected]: the protected source isn't attached, and [publicSet] doesn't have [path]. */
    override suspend fun isGated(path: AssetPath): Boolean = needsProtected(path)

    /** [awaitProtected], for a path that [isGated]; at once for one that isn't. */
    override suspend fun awaitOpen(path: AssetPath) {
        if (needsProtected(path)) awaitProtected()
    }

    override suspend fun read(path: AssetPath): Result<ByteArray> {
        val protectedSource = protectedLayer.value
        val layer = layerFor(path, protectedSource) ?: return Result.failure(ProtectedContentNotAttachedException(path.value))
        return layer.read(path)
    }

    /** Whether a layer that would serve a read can prefetch; see [PrefetchableAssetSource.canPrefetch]. */
    override val canPrefetch: Boolean
        get() = listOfNotNull(publicSet, protectedLayer.value).any { (it as? PrefetchableAssetSource)?.canPrefetch == true }

    /**
     * Prefetches each of [paths] through the layer that would read it, both layers at once, and tells
     * [onProgress] the bytes done of the bytes reported so far: each layer's total counts from its first
     * finished download, so the total can grow while the prefetch runs. Throws before fetching anything
     * for a path neither layer has, with [ProtectedContentNotAttachedException] while no protected
     * source is attached.
     */
    override suspend fun prefetchWithProgress(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit) {
        val protectedSource = protectedLayer.value
        val byLayer = paths.groupBy { path ->
            layerFor(AssetPath(path), protectedSource) ?: throw ProtectedContentNotAttachedException(path)
        }
        val prefetches = byLayer.mapNotNull { (layer, chosen) ->
            (layer as? PrefetchableAssetSource)?.takeIf { it.canPrefetch }?.let { it to chosen }
        }
        if (prefetches.size <= 1) {
            prefetches.firstOrNull()?.let { (layer, chosen) -> layer.prefetchWithProgress(chosen, onProgress) }
            return
        }
        coroutineScope {
            val updates = Channel<LayerProgress>(Channel.UNLIMITED)
            val jobs = prefetches.mapIndexed { layer, (source, chosen) ->
                launch { source.prefetchWithProgress(chosen) { done, total -> updates.trySend(LayerProgress(layer, done, total)) } }
            }
            launch {
                jobs.joinAll()
                updates.close()
            }
            // Reported on the caller's coroutine, so onProgress never runs on two threads at once.
            val done = LongArray(prefetches.size)
            val total = LongArray(prefetches.size)
            for (update in updates) {
                done[update.layer] = update.done
                total[update.layer] = update.total
                onProgress(done.sum(), total.sum())
            }
        }
    }

    /** The layer a read of [path] goes to, or null when only a protected source not yet attached could have it. */
    private suspend fun layerFor(path: AssetPath, protectedSource: AssetSource?): AssetSource? = when {
        protectedSource != null && protectedSource.exists(path) -> protectedSource
        publicSet.exists(path) -> publicSet
        else -> protectedSource
    }

    /** Whether either layer has [path]; [publicSet] alone while no protected source is attached. */
    override suspend fun exists(path: AssetPath): Boolean = protectedLayer.value?.exists(path) == true || publicSet.exists(path)
}

/** One layer's latest prefetch progress, for [LayeredAssetSource.prefetchWithProgress]. */
private class LayerProgress(val layer: Int, val done: Long, val total: Long)
