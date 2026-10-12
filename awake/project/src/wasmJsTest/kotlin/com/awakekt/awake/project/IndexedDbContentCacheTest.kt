/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** [IndexedDbContentCache] against the real browser's IndexedDB, each test under its own namespace. */
class IndexedDbContentCacheTest {
    @Test
    fun missingKeyReturnsNull() = runTest {
        assertNull(cache().get("absent"))
    }

    @Test
    fun putThenGetRoundTrips() = runTest {
        val cache = cache()
        val bytes = ByteArray(37) { (it * 11 - 5).toByte() }

        cache.put("a-sha256", bytes)

        assertContentEquals(bytes, cache.get("a-sha256"))
    }

    @Test
    fun everyTailLengthAndSignBitRoundTrips() = runTest {
        val cache = cache()
        // 0..11 covers every remainder the 4-byte word packing leaves as a tail, in both directions.
        for (length in 0..11) {
            val bytes = ByteArray(length) { (it * 37 - 128).toByte() }

            cache.put("length-$length", bytes)

            assertContentEquals(bytes, cache.get("length-$length"), "length $length")
        }
    }

    @Test
    fun emptyContentRoundTrips() = runTest {
        val cache = cache()

        cache.put("empty", ByteArray(0))

        assertContentEquals(ByteArray(0), cache.get("empty"))
    }

    @Test
    fun puttingAgainReplacesThePreviousValue() = runTest {
        val cache = cache()
        cache.put("key", byteArrayOf(1, 2, 3))

        cache.put("key", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), cache.get("key"))
    }

    @Test
    fun differentNamespacesDoNotCollide() = runTest {
        val suffix = Random.nextInt()
        val a = IndexedDbContentCache("test-$suffix-a")
        val b = IndexedDbContentCache("test-$suffix-b")
        a.put("same-sha256", byteArrayOf(1))
        b.put("same-sha256", byteArrayOf(2))

        assertContentEquals(byteArrayOf(1), a.get("same-sha256"))
        assertContentEquals(byteArrayOf(2), b.get("same-sha256"))
    }

    @Test
    fun aFullOriginStillLoadsFromTheNetwork() = runTest {
        installAbortingIndexedDb()
        try {
            val cache = cache()

            // Returning at all is the point: an abort fires `abort`, not `error`, and used to hang.
            assertContentEquals(model, sourceOver(cache).read(AssetPath("assets/crate.glb")).getOrThrow())
            assertNull(cache.get(Sha256.digestHex(model)), "an aborted put keeps nothing")
        } finally {
            restoreIndexedDb()
        }
    }

    @Test
    fun blockedStorageStillLoadsFromTheNetwork() = runTest {
        installBlockedIndexedDb()
        try {
            val cache = cache()

            assertContentEquals(model, sourceOver(cache).read(AssetPath("assets/crate.glb")).getOrThrow())
            assertNull(cache.get(Sha256.digestHex(model)), "blocked storage reads as a miss")
        } finally {
            restoreIndexedDb()
        }
    }

    private val model = ByteArray(64) { it.toByte() }

    private fun sourceOver(cache: ContentCache): IndexedAssetSource {
        val entry = ProjectIndexEntry(
            path = "assets/crate.glb",
            sizeBytes = model.size.toLong(),
            sha256 = Sha256.digestHex(model),
            url = "files/assets/crate.glb",
        )
        return IndexedAssetSource(ProjectIndex(files = listOf(entry)), { model }, cache)
    }

    private fun cache() = IndexedDbContentCache("test-${Random.nextInt()}")
}

// Every transaction aborts with QuotaExceededError, as on an origin out of storage.
@JsFun(
    """
() => {
  globalThis.__awakeRealIndexedDb = Object.getOwnPropertyDescriptor(globalThis, 'indexedDB');
  const db = {
    objectStoreNames: { contains: () => true },
    transaction: () => {
      const tx = { error: new DOMException('The quota has been exceeded.', 'QuotaExceededError') };
      tx.objectStore = () => ({ get: () => ({}), put: () => ({}) });
      setTimeout(() => tx.onabort && tx.onabort(), 0);
      return tx;
    },
  };
  Object.defineProperty(globalThis, 'indexedDB', {
    configurable: true,
    value: { open: () => { const request = { result: db }; setTimeout(() => request.onsuccess(), 0); return request; } },
  });
}
    """,
)
private external fun installAbortingIndexedDb()

// Opening throws SecurityError, as in a sandboxed iframe or with site storage blocked.
@JsFun(
    """
() => {
  globalThis.__awakeRealIndexedDb = Object.getOwnPropertyDescriptor(globalThis, 'indexedDB');
  Object.defineProperty(globalThis, 'indexedDB', {
    configurable: true,
    value: { open: () => { throw new DOMException('Access to storage is denied.', 'SecurityError'); } },
  });
}
    """,
)
private external fun installBlockedIndexedDb()

@JsFun(
    """
() => {
  const real = globalThis.__awakeRealIndexedDb;
  if (real) Object.defineProperty(globalThis, 'indexedDB', real); else delete globalThis.indexedDB;
  delete globalThis.__awakeRealIndexedDb;
}
    """,
)
private external fun restoreIndexedDb()
