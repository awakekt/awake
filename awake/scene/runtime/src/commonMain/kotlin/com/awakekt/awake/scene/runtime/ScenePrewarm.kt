/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Assets a scene tree draws, got ready ahead of its first spawn by [prewarm], and held until [close].
 *
 * Each frame the scene's asset library builds and uploads the tree's meshes and materials, up to
 * [SceneAssetLibrary.prewarmFrameBudgetBytes] of new mesh a frame, and asks its resolvers for the
 * tree's textures, which [AssetSourceTextureResolver] reads and decodes off the frame. Once every one is
 * built, loaded or failed, the prewarm [isReady] and [await] returns: a `spawn` of the tree then draws
 * it on its first frame, sharing what this built rather than building it again, and its textures are
 * already loaded. A spawn made before then shares whatever is built, and waits for the rest as it would.
 *
 * The prewarm holds what it built in the library, as a drawn entity does, so neither the retained-mesh
 * budget nor another holder's release destroys it before the spawn: [close] lets go once the tree is
 * spawned, or is no longer wanted. Closing before it is ready stops it. The session's teardown stops
 * every prewarm and destroys what they held, as it destroys every asset; a ready prewarm that was never
 * closed is logged then as a warning naming its assets, since it kept them for the whole scene.
 *
 * It also decodes the tree's audio clips into [SceneAssetLibrary.audioClips], off the frame and without
 * playing them, and reads its 3D `particle_emitter` images into [SceneAssetLibrary.particleImages]: the
 * same loads the scene's `AudioSystem` and `ParticleContentSystem` use, so a clip plays and an emitter
 * draws on the spawn's first frame, and a clip or image they already load is loaded once. It holds each
 * until [close], so it stays decoded until the spawn. A scene that registered no such loads, or a clip
 * that is not a 16-bit PCM WAV file, settles it as failed, which [failed] names.
 *
 * glTF models the resolver has not preloaded are not prepared here yet (#1316); a model that can't be
 * built yet fails its renderable, which [failed] names.
 *
 * It lives in the session's `SceneSession.coroutineScope`: a child of the app's `AppCoroutineScope`
 * when one is registered. A coroutine cancelled while it waits in [await], such as a loading screen that closes,
 * gives the prewarm up, and the next frame closes it, so a caller that is cancelled leaks no holds.
 *
 * Made, [close]d and read on the frame thread; [await] from any coroutine.
 */
@OptIn(ExperimentalAtomicApi::class)
class ScenePrewarm internal constructor(
    private val library: SceneAssetLibrary,
    internal val runtime: SceneAppLifecycleRuntime,
    references: SceneAssetReferences,
    internal val scope: CoroutineScope,
    private val onProgress: (settled: Int, total: Int) -> Unit,
) : AutoCloseable {
    internal val pendingRenderables = ArrayDeque(references.renderables)
    internal val pendingTextures: MutableSet<String> = references.textures.toMutableSet()
    internal val heldMeshes = mutableListOf<String>()
    internal val heldMaterials = mutableListOf<String>()
    internal val audioClips = ScenePrewarmLoads(library.audioClips, references.audioClips, "audio clip")
    internal val particleImages = ScenePrewarmLoads(library.particleImages, references.particleTextures, "particle image")
    private val failures = linkedMapOf<String, Throwable>()
    private val done = CompletableDeferred<Unit>(scope.coroutineContext[Job])
    private val givenUp = AtomicBoolean(false)
    private var state = State.Pending

    /** What it prepares, for a log line: its first few assets by name, and how many more. */
    internal val label: String = references.names().let { names ->
        names.take(LABEL_NAMES).joinToString().ifEmpty { "nothing" } + if (names.size > LABEL_NAMES) " and ${names.size - LABEL_NAMES} more" else ""
    }

    /** How many renderables, textures, particle images and audio clips it prepares. */
    val total: Int = references.renderables.size + references.textures.size + references.particleTextures.size + references.audioClips.size

    /** How many of [total] are built, loaded or failed so far. */
    var settled: Int = 0
        private set

    /** Whether every asset is built, loaded or failed, and the prewarm is still open. */
    val isReady: Boolean get() = state == State.Ready

    /** What could not be prepared, by mesh and material (`mesh + material`), texture name or path, and why. */
    val failed: Map<String, Throwable> get() = failures.toMap()

    internal val isPending: Boolean get() = state == State.Pending

    /** Whether a coroutine was cancelled while it waited in [await], so the next frame closes this. */
    internal val isGivenUp: Boolean get() = givenUp.load()

    /**
     * Suspends until the prewarm [isReady]. Throws [CancellationException] when it is closed, or the
     * scene torn down, before then. Cancelling the coroutine that waits here gives the prewarm up: the
     * next frame closes it, letting go of what it holds.
     */
    suspend fun await() {
        try {
            done.await()
        } catch (cancelled: CancellationException) {
            // The prewarm itself ended, or the caller stopped waiting: only the latter gives it up.
            if (!done.isCompleted) givenUp.store(true)
            throw cancelled
        }
    }

    /** Lets go of what it holds, and stops it if it is not ready yet. Closing twice is harmless. */
    override fun close() {
        if (state == State.Closed || state == State.Disposed) return
        state = State.Closed
        library.prewarms.remove(this)
        heldMeshes.forEach(library::releaseMesh)
        heldMaterials.forEach(library::releaseMaterial)
        heldMeshes.clear()
        heldMaterials.clear()
        audioClips.release()
        particleImages.release()
        done.cancel(CancellationException("The prewarm was closed before it was ready."))
    }

    /** The library destroyed everything it held: forget the holds, so [close] releases nothing more. */
    internal fun dispose() {
        if (state == State.Closed || state == State.Disposed) return
        state = State.Disposed
        heldMeshes.clear()
        heldMaterials.clear()
        // The loads outlive the library, so their holds are let go, which stops what still decodes.
        audioClips.release()
        particleImages.release()
        done.cancel(CancellationException("The scene was torn down before the prewarm was ready."))
    }

    internal fun settle(name: String, failure: Throwable?) {
        settled++
        if (failure != null) failures[name] = failure
        onProgress(settled, total)
    }

    internal fun finishIfDone() {
        if (state != State.Pending || pendingRenderables.isNotEmpty() || pendingTextures.isNotEmpty()) return
        if (!audioClips.isDone || !particleImages.isDone) return
        state = State.Ready
        done.complete(Unit)
    }

    private enum class State { Pending, Ready, Closed, Disposed }
}

private fun SceneAssetReferences.names(): List<String> =
    renderables.map { "${it.mesh} + ${it.material}" } + textures + particleTextures + audioClips

// How many asset names a prewarm's log label lists before "and N more". Top level, so it stays private:
// a const in a private companion object still compiles to a public static field on the JVM.
private const val LABEL_NAMES = 3

/**
 * Starts getting the assets [references] names ready, as [ScenePrewarm] describes; the first frame
 * after this works on it. On the frame thread.
 *
 * @param runtime The runtime whose renderer builds the meshes and materials.
 * @param references The assets to get ready, such as `node.assetReferences()`.
 * @param onProgress Called on the frame thread each time one more asset is built, loaded or failed.
 */
fun SceneAssetLibrary.prewarm(
    runtime: SceneAppLifecycleRuntime,
    references: SceneAssetReferences,
    onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> },
): ScenePrewarm = ScenePrewarm(this, runtime, references, runtime.session.coroutineScope, onProgress).also { prewarm ->
    prewarms.add(prewarm)
    prewarm.audioClips.start(prewarm.scope)
    prewarm.particleImages.start(prewarm.scope)
    prewarm.finishIfDone()
}

/** [SceneAssetLibrary.prewarm] in this runtime's asset library. Throws when the scene registered none. */
fun SceneAppLifecycleRuntime.prewarm(
    references: SceneAssetReferences,
    onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> },
): ScenePrewarm = session.requireAssetLibrary().prewarm(this, references, onProgress)

/** Prewarms what [node] and its children name, ahead of `spawn(node)`. */
fun SceneAppLifecycleRuntime.prewarm(
    node: SceneNode,
    onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> },
): ScenePrewarm = prewarm(node.assetReferences(), onProgress)

/** Prewarms what every node of [document] names, ahead of loading or spawning it. */
fun SceneAppLifecycleRuntime.prewarm(
    document: SceneDocument,
    onProgress: (settled: Int, total: Int) -> Unit = { _, _ -> },
): ScenePrewarm = prewarm(document.assetReferences(), onProgress)

/**
 * Bytes of new mesh the prewarms may upload in one frame. Unlimited by default; a host that streams
 * prewarms while it plays sets a budget so one large model does not stall a frame. A mesh is never
 * split, so a frame uploads at least one when any waits.
 */
var SceneAssetLibrary.prewarmFrameBudgetBytes: Long
    get() = prewarms.frameBudgetBytes
    set(value) {
        require(value > 0) { "A prewarm frame budget is above zero bytes, not $value." }
        prewarms.frameBudgetBytes = value
    }

/**
 * Works on the open prewarms for a frame. The scene runtime calls it at the start of each frame; a host
 * that runs this library's frames itself calls it on its frame thread, between frames.
 */
fun SceneAssetLibrary.runPrewarms() = prewarms.step()
