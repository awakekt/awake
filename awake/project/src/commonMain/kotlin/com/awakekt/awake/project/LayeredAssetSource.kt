/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

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
 * the protected source fails it.
 *
 * Core takes the protected source and nothing else: the token, any key and when to sign in or out
 * are the app's. Typically [publicSet] is an [IndexedAssetSource] over the public index, which ships
 * next to the page, and the protected source an [IndexedAssetSource] opened with a
 * [TokenAuthorizedFetch] after sign-in. The public set is never encrypted; protected bundles may be.
 *
 * @param publicSet The public set: what loads before sign-in.
 */
class LayeredAssetSource(private val publicSet: AssetSource) : AssetSource {
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

    override suspend fun read(path: AssetPath): Result<ByteArray> {
        val protectedSource = protectedLayer.value
        return when {
            protectedSource != null && protectedSource.exists(path) -> protectedSource.read(path)
            publicSet.exists(path) -> publicSet.read(path)
            else -> protectedSource?.read(path) ?: Result.failure(ProtectedContentNotAttachedException(path.value))
        }
    }

    /** Whether either layer has [path]; [publicSet] alone while no protected source is attached. */
    override suspend fun exists(path: AssetPath): Boolean = protectedLayer.value?.exists(path) == true || publicSet.exists(path)
}
