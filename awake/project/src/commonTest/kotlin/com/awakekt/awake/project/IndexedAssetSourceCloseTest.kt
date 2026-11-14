/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndexedAssetSourceCloseTest {
    @Test
    fun closingEncryptedSourceTearsDownHandleWrappedProviderAndRejectsReads() = runTest {
        val project = EncryptedProject.write()
        val serverKey = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)
        val store = InMemoryBundleKeyStore()
        val keys = BundleKeyProvider.wrapped(store) { _, spki -> serverKey.wrapFor(spki) }
        val source = IndexedAssetSource(project.index, project.server(), cache = null, keys = keys)

        val cratePath = "assets/models/crate.glb"
        assertContentEquals(project.files.getValue(cratePath), source.read(AssetPath(cratePath)).getOrThrow())

        val storedHandle = assertNotNull(store.get(EncryptedProject.KEY_ID))
        assertFalse(storedHandle.isClosed)

        source.close()
        source.close()

        assertTrue(storedHandle.isClosed, "Closing the source must close held BundleKeyHandle instances")
        assertFailsWith<IllegalStateException> {
            keys.keyHandle(EncryptedProject.KEY_ID)
        }
        val readFailure = source.read(AssetPath("scenes/main.scene.json")).exceptionOrNull()
        assertTrue(readFailure is IllegalStateException)
        assertFailsWith<IllegalStateException> {
            source.prefetch(listOf("scenes/main.scene.json"))
        }
    }

    @Test
    fun closingSourceWithRawKeyProviderLeavesCallerOwnedKeyUntouched() = runTest {
        val project = EncryptedProject.write()
        val callerOwnedKey = project.key.copyOf()
        val source = IndexedAssetSource(
            index = project.index,
            fetch = project.server(),
            keys = BundleKeyProvider { callerOwnedKey },
        )

        val scenePath = "scenes/main.scene.json"
        assertContentEquals(project.files.getValue(scenePath), source.read(AssetPath(scenePath)).getOrThrow())

        source.close()

        assertContentEquals(project.key, callerOwnedKey)
        assertTrue(source.read(AssetPath("assets/models/crate.glb")).isFailure)
    }

    @Test
    fun closingBundleMembersZerosUncachedStagedBytesWithoutZeroingReturnedReads() = runTest {
        val files = mapOf(
            "pack/first.bin" to "first-secret-payload".encodeToByteArray(),
            "pack/second.bin" to "second-staged-secret".encodeToByteArray(),
        )
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val index = ContentBundleWriter().write(looseIndexOf(files), { files.getValue(it.path) }) {
            bundles[it.url] = it
        }
        val bundle = index.bundles.single()
        val firstEntry = index.files.single { it.key == "pack/first.bin" }
        val secondEntry = index.files.single { it.key == "pack/second.bin" }

        val members = BundleMembers(
            index = index,
            fetch = { url -> bundles.getValue(url).bytes },
            cache = null,
            rangeFetch = null,
            seals = null,
        )

        val loaded = members.load(bundle.sha256)
        val takenFirst = members.read(firstEntry, firstEntry.key)
        val unreadSecond = loaded.getValue(secondEntry.sha256)

        assertContentEquals(files.getValue("pack/first.bin"), takenFirst)
        assertContentEquals(files.getValue("pack/second.bin"), unreadSecond)

        members.close()

        assertContentEquals(files.getValue("pack/first.bin"), takenFirst)
        assertTrue(unreadSecond.all { it == 0.toByte() }, "Uncached staged bytes must be zeroed on close")
        assertFailsWith<IllegalStateException> { members.read(secondEntry, secondEntry.key) }
        assertFailsWith<IllegalStateException> { members.load(bundle.sha256) }
    }

    @Test
    fun layeredAssetSourceClosesProtectedOnReplaceDetachAndClose() = runTest {
        val publicSource = CountingCloseableAssetSource(setOf("public.txt"))
        val firstProtected = CountingCloseableAssetSource(setOf("secret.txt"))
        val secondProtected = CountingCloseableAssetSource(setOf("secret.txt"))
        val thirdProtected = CountingCloseableAssetSource(setOf("secret.txt"))

        val layered = LayeredAssetSource(publicSource)
        layered.attachProtected(firstProtected)
        layered.attachProtected(firstProtected)
        assertEquals(0, firstProtected.closeCount)

        layered.attachProtected(secondProtected)
        assertEquals(1, firstProtected.closeCount)
        assertEquals(0, secondProtected.closeCount)

        layered.detachProtected()
        assertEquals(1, secondProtected.closeCount)

        layered.attachProtected(thirdProtected)
        layered.detachProtected(close = false)
        assertEquals(0, thirdProtected.closeCount)

        layered.attachProtected(thirdProtected)
        layered.close()
        layered.close()

        assertEquals(1, thirdProtected.closeCount)
        assertEquals(1, publicSource.closeCount)
        assertTrue(layered.read(AssetPath("public.txt")).isFailure)
        assertFailsWith<IllegalStateException> { layered.attachProtected(secondProtected) }
        assertFailsWith<IllegalStateException> { layered.prefetchWithProgress(listOf("public.txt")) { _, _ -> } }
    }

    private class InMemoryBundleKeyStore : BundleKeyStore {
        private val entries = LinkedHashMap<String, BundleKeyHandle>()

        override suspend fun get(keyId: String): BundleKeyHandle? = entries[keyId]

        override suspend fun put(handle: BundleKeyHandle) {
            entries[handle.keyId] = handle
        }

        override suspend fun delete(keyId: String) {
            entries.remove(keyId)
        }
    }

    private class CountingCloseableAssetSource(private val paths: Set<String>) :
        AssetSource,
        AutoCloseable {
        var closeCount = 0
            private set

        override suspend fun read(path: AssetPath): Result<ByteArray> =
            if (path.value.removePrefix("/") in paths) {
                Result.success(byteArrayOf(1, 2, 3))
            } else {
                Result.failure(NoSuchElementException(path.value))
            }

        override suspend fun exists(path: AssetPath): Boolean = path.value.removePrefix("/") in paths

        override fun close() {
            closeCount++
        }
    }
}
