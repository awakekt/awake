/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.io.AssetLoad
import com.awakekt.awake.core.io.AssetLoads
import com.awakekt.awake.render.texture.GpuImageSource
import kotlinx.coroutines.CoroutineScope

/**
 * The scene's decoded audio clips, by project path, as `audioClipLoads` makes them: the same ones the
 * scene's `AudioSystem` plays, so a [prewarm] decodes a tree's `audio_source` clips into them, without
 * playing them, and the first play after the spawn needs no decode. Null until the host sets it; a
 * prewarm then reports each clip in [ScenePrewarm.failed]. The host that sets it closes it when the scene
 * stops. On the frame thread.
 */
var SceneAssetLibrary.audioClips: AssetLoads<AudioClip>?
    get() = prewarms.audioClips
    set(value) {
        prewarms.audioClips = value
    }

/**
 * The scene's 3D particle images, by project path, as `particleImageLoads` makes them: the same ones the
 * scene's `ParticleContentSystem` draws, so a [prewarm] reads a tree's `particle_emitter` images into
 * them ahead of the first emitter's draw. Null until the host sets it; a prewarm then reports each image
 * in [ScenePrewarm.failed]. The host that sets it closes it when the scene stops. On the frame thread.
 */
var SceneAssetLibrary.particleImages: AssetLoads<GpuImageSource>?
    get() = prewarms.particleImages
    set(value) {
        prewarms.particleImages = value
    }

/**
 * One kind of asset a [ScenePrewarm] gets ready through the scene's [AssetLoads], such as its audio
 * clips: each path is held from the start, so the asset stays decoded until the prewarm lets go, and
 * its load runs in the session's scope, shared with whatever else asks for the same path.
 */
internal class ScenePrewarmLoads<T : Any>(
    private val loads: AssetLoads<T>?,
    paths: Set<String>,
    private val kind: String,
) {
    private val pending = LinkedHashSet(paths)
    private val held = mutableListOf<String>()

    val isDone: Boolean get() = pending.isEmpty()

    /** Holds and starts every path's load in [scope]; on the frame thread, as the prewarm is made. */
    fun start(scope: CoroutineScope) {
        val loads = loads ?: return
        for (path in pending) {
            loads.hold(path)
            held += path
            loads.request(path, scope)
        }
    }

    /** Settles each path that finished loading, or failed, since the last frame. */
    fun poll(prewarm: ScenePrewarm, scope: CoroutineScope) {
        val paths = pending.iterator()
        while (paths.hasNext() && prewarm.isPending) {
            val path = paths.next()
            val failure = when (val load = loads?.request(path, scope)) {
                AssetLoad.Loading -> continue
                is AssetLoad.Loaded -> null
                is AssetLoad.Failed -> load.cause
                null -> IllegalStateException("No $kind loads are registered on the scene's asset library, so '$path' loads when it is first used.")
            }
            paths.remove()
            prewarm.settle(path, failure)
        }
    }

    /** Lets go of every path it holds; the last hold on one still loading cancels its load. */
    fun release() {
        loads?.let { held.forEach(it::release) }
        held.clear()
    }
}
