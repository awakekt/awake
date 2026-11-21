/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * A key provider passed as a trailing lambda, as a 0.60.0 call did, still compiles and reads an
 * encrypted project. 0.61.0 put `limits` after `keys`, so a trailing lambda met a `BundleMemoryLimits`
 * and the call stopped compiling; this file is that gate. The calls a 0.61.0 consumer writes compile
 * beside it, without ambiguity.
 */
class TrailingKeysLambdaTest {
    private val scene = BundledProject.SAMPLE_FILES.keys.first()

    @Test
    fun theConstructorTakesTheKeysAsATrailingLambda() = runTest {
        val project = EncryptedProject.write()
        val files = IndexedAssetSource(project.index, project.server(), null, null) { EncryptedProject.KEY }

        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), files.read(AssetPath(scene)).getOrThrow())
    }

    @Test
    fun openTakesTheKeysAsATrailingLambda() = runTest {
        val project = EncryptedProject.write()
        val indexBytes = kotlinx.serialization.json.Json.encodeToString(ProjectIndex.serializer(), project.index).encodeToByteArray()
        val server = project.server()
        val fetch: suspend (AssetPath) -> ByteArray = { path ->
            if (path.value == IndexedAssetSource.DEFAULT_INDEX_PATH) indexBytes else server(path.value)
        }
        val files = IndexedAssetSource.open(fetch = fetch, cache = null, retainCachedOnly = true, rangeFetch = null) { EncryptedProject.KEY }

        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), files.read(AssetPath(scene)).getOrThrow())
    }

    @Test
    fun theCallsA061ConsumerWritesStillResolve() = runTest {
        val project = EncryptedProject.write()
        val keys = project.keys()
        val calls = listOf(
            IndexedAssetSource(project.index, project.server(), keys = keys),
            IndexedAssetSource(project.index, project.server(), keys = keys, limits = BundleMemoryLimits()),
            IndexedAssetSource(project.index, project.server(), null, null, keys),
            IndexedAssetSource(project.index, project.server(), null, null, keys, BundleMemoryLimits()),
        )

        calls.forEach { assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), it.read(AssetPath(scene)).getOrThrow()) }
    }
}
