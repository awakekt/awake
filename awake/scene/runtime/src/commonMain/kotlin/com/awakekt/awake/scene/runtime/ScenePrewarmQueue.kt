/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.logging.Logger

/**
 * The open [ScenePrewarm]s of one [SceneAssetLibrary], worked on oldest first, a frame at a time, on the
 * frame thread. A ready prewarm stays open, holding its assets, until it is closed or [dispose]d.
 */
internal class ScenePrewarmQueue(private val library: SceneAssetLibrary) {
    private val open = mutableListOf<ScenePrewarm>()

    /** Bytes of new mesh one [step] may upload; one mesh is uploaded whatever its size. */
    var frameBudgetBytes: Long = Long.MAX_VALUE

    fun add(prewarm: ScenePrewarm) {
        open += prewarm
    }

    fun remove(prewarm: ScenePrewarm) {
        open -= prewarm
    }

    /** Polls each pending prewarm's textures, and builds renderables until the frame's budget is spent. */
    fun step() {
        if (open.isEmpty()) return
        var spent = 0L
        // A copy: a progress callback may close a prewarm, or open another, while this runs.
        for (prewarm in open.toList()) {
            if (prewarm.isGivenUp) prewarm.close()
            if (!prewarm.isPending) continue
            pollTextures(prewarm)
            while (prewarm.isPending && prewarm.pendingRenderables.isNotEmpty() && spent < frameBudgetBytes) {
                spent += build(prewarm, prewarm.pendingRenderables.removeFirst())
            }
            prewarm.finishIfDone()
        }
    }

    /**
     * Stops every prewarm without releasing: the library is destroying what they held. A ready one that
     * was never closed is a leak, since it held its assets for the rest of the scene, so it is logged; one
     * still loading is not, as a scene switched while it loaded stops it on purpose.
     */
    fun dispose() {
        val all = open.toList()
        open.clear()
        all.forEach { prewarm ->
            if (prewarm.isReady) {
                log.warn {
                    "A prewarm of ${prewarm.label} was never closed, so it held them until the scene was torn " +
                        "down. Close it once what it prepared has spawned."
                }
            }
            prewarm.dispose()
        }
    }

    private fun pollTextures(prewarm: ScenePrewarm) {
        val names = prewarm.pendingTextures.iterator()
        while (names.hasNext() && prewarm.isPending) {
            val name = names.next()
            val failure = if (library.isTextureRegistered(name)) {
                null
            } else {
                when (val load = library.prewarmTexture(name)) {
                    SceneTextureLoad.Loading -> continue
                    is SceneTextureLoad.Loaded -> null
                    is SceneTextureLoad.Failed -> load.cause
                    null -> IllegalStateException("No scene texture named '$name' is registered or could be resolved by installed resolvers.")
                }
            }
            names.remove()
            prewarm.settle(name, failure)
        }
    }

    /**
     * Builds [key]'s mesh, material and levels, holding each for [prewarm], and returns the bytes of mesh
     * it newly uploaded: none for one already built or retained, which costs the frame nothing.
     */
    // TooGenericExceptionCaught: a factory or resolver reports a mesh it can't build in its own exception type.
    @Suppress("TooGenericExceptionCaught")
    private fun build(prewarm: ScenePrewarm, key: SceneRenderableKey): Long {
        val label = "${key.mesh} + ${key.material}"
        if (library.hasCustomRenderer(key)) {
            prewarm.settle(label, null)
            return 0
        }
        var uploaded = 0L
        try {
            uploaded += hold(prewarm, key.mesh)
            library.requireMaterial(prewarm.runtime, key.material)
            prewarm.heldMaterials += key.material
            library.levelsOf(key.mesh)?.meshes?.forEach { level -> uploaded += hold(prewarm, level) }
            prewarm.settle(label, null)
        } catch (failure: Exception) {
            log.error(failure) { "Can't prewarm $label: ${failure.message}. It builds when it is first drawn instead." }
            prewarm.settle(label, failure)
        }
        return uploaded
    }

    private fun hold(prewarm: ScenePrewarm, mesh: String): Long {
        val fresh = library.meshHolderCount(mesh) == 0 && !library.isMeshRetained(mesh)
        val built = library.requireMesh(prewarm.runtime, mesh)
        prewarm.heldMeshes += mesh
        return if (fresh) built.sizeBytes else 0
    }
}

private val log = Logger("scene-prewarm")
