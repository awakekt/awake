/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

    @Test
    fun opensTheDatabaseOnceForEveryReadAndWrite() = runTest {
        countIndexedDbOpens()
        try {
            val cache = cache()

            // Four first calls at once: they share the one open rather than racing to each start one.
            coroutineScope { repeat(4) { n -> launch { cache.put("key-$n", byteArrayOf(n.toByte())) } } }
            repeat(4) { n -> assertContentEquals(byteArrayOf(n.toByte()), cache.get("key-$n")) }

            assertEquals(1, indexedDbOpens())
        } finally {
            stopCountingIndexedDbOpens()
        }
    }

    @Test
    fun anotherTabCanUpgradeTheStoreWhileACacheHasItOpen() = runTest {
        val name = "awake-content-cache-test-${Random.nextInt()}"
        IndexedDbContentCache("test", name).put("key", byteArrayOf(1))

        assertEquals("upgraded", upgradeIndexedDb(name).await<JsString>().toString())
    }

    @Test
    fun aColonInANamespaceDoesNotReachAnotherNamespace() = runTest {
        val suffix = Random.nextInt()
        IndexedDbContentCache("test-$suffix:a").put("key", byteArrayOf(1))

        // Joined with ':', namespace "x:a" and key "key" made the same entry as namespace "x" and key "a:key".
        assertNull(IndexedDbContentCache("test-$suffix").get("a:key"))
    }

    @Test
    fun aCacheWorksAgainOnceBlockedStorageIsBack() = runTest {
        val cache = cache()
        installBlockedIndexedDb()
        try {
            assertNull(cache.get("key"))
        } finally {
            restoreIndexedDb()
        }

        cache.put("key", byteArrayOf(7))

        assertContentEquals(byteArrayOf(7), cache.get("key"))
    }

    @Test
    fun retainOnlyDeletesWhatTheIndexNoLongerLists() = runTest {
        val cache = cache()
        val (kept, dropped, alsoKept) = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
        listOf(kept, dropped, alsoKept).forEach { cache.put(Sha256.digestHex(it), it) }

        val deleted = cache.retainOnly(indexOf("assets/a.bin" to kept, "assets/c.bin" to alsoKept))

        assertEquals(1, deleted)
        assertNull(cache.get(Sha256.digestHex(dropped)))
        assertContentEquals(kept, cache.get(Sha256.digestHex(kept)))
        assertContentEquals(alsoKept, cache.get(Sha256.digestHex(alsoKept)))
    }

    @Test
    fun retainOnlyLeavesOtherNamespacesAlone() = runTest {
        val suffix = Random.nextInt()
        val other = IndexedDbContentCache("test-$suffix-other")
        val bytes = byteArrayOf(5)
        other.put(Sha256.digestHex(bytes), bytes)

        assertEquals(0, IndexedDbContentCache("test-$suffix").retainOnly(ProjectIndex()))

        assertContentEquals(bytes, other.get(Sha256.digestHex(bytes)))
    }

    @Test
    fun retainOnlyDeletesNothingWhenStorageIsBlocked() = runTest {
        installBlockedIndexedDb()
        try {
            assertEquals(0, cache().retainOnly(ProjectIndex()))
        } finally {
            restoreIndexedDb()
        }
    }

    private fun indexOf(vararg files: Pair<String, ByteArray>) = ProjectIndex(
        files = files.map { (path, bytes) ->
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")
        },
    )

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

@JsFun(
    """
() => {
  const factory = globalThis.indexedDB, open = factory.open;
  globalThis.__awakeIndexedDbOpens = 0;
  factory.open = (...args) => { globalThis.__awakeIndexedDbOpens++; return open.apply(factory, args); };
}
    """,
)
private external fun countIndexedDbOpens()

@JsFun("() => globalThis.__awakeIndexedDbOpens")
private external fun indexedDbOpens(): Int

@JsFun("() => { delete globalThis.indexedDB.open; delete globalThis.__awakeIndexedDbOpens; }")
private external fun stopCountingIndexedDbOpens()

// Opens [name] at version 2, as a newer tab would: 'blocked' if a connection held on to version 1.
@JsFun(
    """
(name) => new Promise((resolve) => {
  const request = indexedDB.open(name, 2);
  request.onblocked = () => resolve('blocked');
  request.onerror = () => resolve('error: ' + request.error);
  request.onsuccess = () => { request.result.close(); indexedDB.deleteDatabase(name); resolve('upgraded'); };
})
    """,
)
private external fun upgradeIndexedDb(name: String): Promise<JsString>
