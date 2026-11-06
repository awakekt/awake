/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Session-wrapped bundle keys open encrypted projects end-to-end without exposing raw key material. */
class BundleKeyWrappingTest {
    private val crate = "assets/models/crate.glb"
    private val scene = "scenes/main.scene.json"

    @Test
    fun wrappedKeysOpenWholeBundlesRangeReadsAndCachedMembers() = runTest {
        val project = EncryptedProject.write()
        val serverKey = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)
        val seenSpkis = mutableListOf<ByteArray>()
        val keys = BundleKeyProvider.wrapped { keyId, spki ->
            assertEquals(EncryptedProject.KEY_ID, keyId)
            seenSpkis += spki
            serverKey.wrapFor(spki)
        }

        val cache = MemoryContentCache()
        val wholeSource = IndexedAssetSource(project.index, project.server(), cache, keys = keys)
        project.files.forEach { (path, plain) ->
            assertContentEquals(plain, wholeSource.read(AssetPath(path)).getOrThrow(), path)
        }
        assertEquals(1, seenSpkis.size, "one ephemeral exchange and one unwrap for the source")

        val range: suspend (String, Long, Long) -> ByteArray = { url, offset, length ->
            project.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
        }
        val rangeSource = IndexedAssetSource(
            project.index,
            fetch = { error("whole fetch unused: $it") },
            rangeFetch = range,
            keys = BundleKeyProvider.wrapped { _, spki -> serverKey.wrapFor(spki) },
        )
        assertContentEquals(project.files.getValue(crate), rangeSource.read(AssetPath(crate)).getOrThrow())

        val offlineSource = IndexedAssetSource(
            project.index,
            fetch = { error("offline: $it") },
            cache = cache,
            keys = BundleKeyProvider.wrapped { _, spki -> serverKey.wrapFor(spki) },
        )
        assertContentEquals(project.files.getValue(scene), offlineSource.read(AssetPath(scene)).getOrThrow())
    }

    @Test
    fun perBundleKeyIdsShareOneSessionExchangeAndUnwrapOnlyTouchedBundles() = runTest {
        val commonKey = BundleEncryptionKey("common-2026", ByteArray(32) { (it * 3 + 1).toByte() })
        val northIslandKey = BundleEncryptionKey("north-island-2026", ByteArray(32) { (it * 7 + 9).toByte() })
        val islandPaths = setOf("scenes/pier.scene.json", "assets/models/buoy.glb")
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val index = ContentBundleWriter(
            grouping = { path -> if (path in islandPaths) "north-island" else "common" },
            encryption = commonKey,
            groupEncryption = { group -> if (group == "north-island") northIslandKey else null },
        ).write(looseIndexOf(BundledProject.SAMPLE_FILES), { BundledProject.SAMPLE_FILES.getValue(it.path) }) {
            bundles[it.url] = it
        }

        val asked = mutableListOf<String>()
        val spkis = mutableListOf<ByteArray>()
        val serverKeys = mapOf(commonKey.keyId to commonKey, northIslandKey.keyId to northIslandKey)
        val keys = BundleKeyProvider.wrapped { keyId, spki ->
            asked += keyId
            spkis += spki
            serverKeys.getValue(keyId).wrapFor(spki)
        }

        val source = IndexedAssetSource(index, { url -> bundles.getValue(url).bytes }, keys = keys)
        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), source.read(AssetPath(scene)).getOrThrow())
        assertEquals(listOf("common-2026"), asked)

        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue("scenes/pier.scene.json"),
            source.read(AssetPath("scenes/pier.scene.json")).getOrThrow(),
        )
        assertEquals(listOf("common-2026", "north-island-2026"), asked)
        assertContentEquals(spkis[0], spkis[1], "both bundle keys unwrap under the same session keypair")

        val rangeSource = IndexedAssetSource(
            index = index,
            fetch = { error("whole fetch unused: $it") },
            rangeFetch = { url, offset, length -> bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt()) },
            keys = BundleKeyProvider.wrapped { keyId, spki -> serverKeys.getValue(keyId).wrapFor(spki) },
        )
        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(crate), rangeSource.read(AssetPath(crate)).getOrThrow())
        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue("assets/models/buoy.glb"),
            rangeSource.read(AssetPath("assets/models/buoy.glb")).getOrThrow(),
        )
    }

    @Test
    fun wrappedProviderRejectsRawKeyAccessAndCopiesAndRedactsMaterial() = runTest {
        val raw = ByteArray(32) { (it * 5 + 11).toByte() }
        val hex = raw.hex()
        val wrappedProvider = BundleKeyProvider.wrapped { _, _ -> error("unreached") }

        val unsupported = assertFailsWith<UnsupportedOperationException> {
            wrappedProvider.key("harbor-town-2026")
        }
        assertTrue(unsupported.message.orEmpty().contains("non-extractable"), "$unsupported")

        val callerBuffer = raw.copyOf()
        val handle = BundleKeyHandle.fromRawBytes("harbor-town-2026", callerBuffer)
        callerBuffer.fill(0)
        assertEquals("BundleKeyHandle(keyId=harbor-town-2026, key=<redacted>)", handle.toString())
        assertFalse(handle.toString().contains(hex))

        val plain = "harbor-scene".encodeToByteArray()
        val sha256 = Sha256.digestHex(plain)
        val sealed = BundleCipher(raw, "harbor-town-2026").use { it.seal(plain, sha256) }
        assertContentEquals(plain, BundleCipher(handle).open(sealed, sha256, "scene"))
        handle.close()

        val exchange = BundleSessionKeyExchange.generate()
        val firstSpki = exchange.publicKeySpki
        val mutated = exchange.publicKeySpki.also { it.fill(0) }
        assertFalse(mutated.contentEquals(firstSpki))
        assertContentEquals(firstSpki, exchange.publicKeySpki, "publicKeySpki returns a defensive copy")
        exchange.close()

        assertFailsWith<IllegalArgumentException> { BundleKeyHandle.fromRawBytes(" ", raw) }
        assertFailsWith<IllegalArgumentException> { BundleKeyHandle.fromRawBytes("harbor-town-2026", ByteArray(31)) }
    }

    @Test
    fun tamperedCiphertextWrongKeypairAndWrongSizePayloadAreRejected() = runTest {
        val serverKey = BundleEncryptionKey("harbor-town-2026", EncryptedProject.KEY)
        val alice = BundleSessionKeyExchange.generate()
        val bob = BundleSessionKeyExchange.generate()
        try {
            val wrappedForAlice = serverKey.wrapFor(alice.publicKeySpki)
            assertTrue(wrappedForAlice.size >= BundleSessionKeyExchange.MIN_MODULUS_BITS / 8)
            val isSoftwarePlatform = alice.unwrap("harbor-town-2026", wrappedForAlice).use {
                it.platformKey is SoftwareBundleKey
            }

            val tampered = wrappedForAlice.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
            assertFailsWith<IllegalArgumentException> { alice.unwrap("harbor-town-2026", tampered) }
            assertFailsWith<IllegalArgumentException> { bob.unwrap("harbor-town-2026", wrappedForAlice) }
            assertFailsWith<IllegalArgumentException> { alice.unwrap("harbor-town-2026", ByteArray(0)) }
            assertFailsWith<IllegalArgumentException> { alice.unwrap("harbor-town-2026", byteArrayOf(1, 2, 3, 4)) }
            assertFailsWith<IllegalArgumentException> { alice.unwrap("", wrappedForAlice) }

            val shortPlaintextCiphertext = CryptographyProvider.Default.get(RSA.OAEP)
                .publicKeyDecoder(SHA256)
                .decodeFromByteArray(RSA.PublicKey.Format.DER, alice.publicKeySpki)
                .encryptor()
                .encrypt(ByteArray(16) { 5 })
            if (isSoftwarePlatform) {
                assertFailsWith<IllegalArgumentException> { alice.unwrap("harbor-town-2026", shortPlaintextCiphertext) }
            } else {
                // SubtleCrypto unwrapKey('raw', ..., 'HKDF', false) keeps unwrapped length opaque to script;
                // opening any sealed member must then fail authentication.
                alice.unwrap("harbor-town-2026", shortPlaintextCiphertext).use { nonExtractableHandle ->
                    val plain = "harbor".encodeToByteArray()
                    val sha256 = Sha256.digestHex(plain)
                    val sealed = BundleCipher(EncryptedProject.KEY, "harbor-town-2026").use { it.seal(plain, sha256) }
                    assertFailsWith<BundleAuthenticationException> {
                        BundleCipher(nonExtractableHandle).open(sealed, sha256, "harbor")
                    }
                }
            }
        } finally {
            alice.close()
            bob.close()
        }
    }

    @Test
    fun wrapForRejectsMalformedAndSub2048BitRsaPublicKeys() = runTest {
        val serverKey = BundleEncryptionKey("harbor-town-2026", EncryptedProject.KEY)

        assertFailsWith<IllegalArgumentException> { serverKey.wrapFor(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { serverKey.wrapFor(byteArrayOf(0x30, 0x03, 0x01, 0x02, 0x03)) }

        val shortKeyPair = CryptographyProvider.Default.get(RSA.OAEP)
            .keyPairGenerator(1024.bits, SHA256)
            .generateKey()
        val shortSpki = shortKeyPair.publicKey.encodeToByteArray(RSA.PublicKey.Format.DER)

        val failure = assertFailsWith<IllegalArgumentException> { serverKey.wrapFor(shortSpki) }
        assertTrue(failure.message.orEmpty().contains("2048"), "got $failure")
    }

    @Test
    fun wrongMasterKeyFailsAuthenticationEvictsPersistedStoreEntryAndRecovers() = runTest {
        val project = EncryptedProject.write()
        val wrongMaster = BundleEncryptionKey(EncryptedProject.KEY_ID, ByteArray(32) { 9 })
        val rightMaster = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)
        val store = InMemoryHandleStore()

        // Seed the store with a handle unwrapped from the wrong master key.
        BundleSessionKeyExchange.generate().use { seedExchange ->
            val badHandle = seedExchange.unwrap(EncryptedProject.KEY_ID, wrongMaster.wrapFor(seedExchange.publicKeySpki))
            store.put(badHandle)
        }

        var networkAsks = 0
        val keys = BundleKeyProvider.wrapped(store) { keyId, spki ->
            networkAsks++
            assertEquals(EncryptedProject.KEY_ID, keyId)
            rightMaster.wrapFor(spki)
        }
        val source = IndexedAssetSource(project.index, project.server(), keys = keys)

        val first = source.read(AssetPath(crate)).exceptionOrNull()
        assertTrue(first?.message.orEmpty().contains("failed authentication: the wrong key (${EncryptedProject.KEY_ID})"), "got $first")
        assertEquals(0, networkAsks, "first attempt used the pre-seeded store entry")
        assertEquals(null, store.get(EncryptedProject.KEY_ID), "forget() evicted the wrong handle from the store")
        assertEquals(1, store.deletes)

        // Second read misses the evicted store entry, fetches the valid wrapped key, persists it, and opens cleanly.
        assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        assertEquals(1, networkAsks)
        assertTrue(store.get(EncryptedProject.KEY_ID) != null)
    }

    @Test
    fun closeZeroesSoftwareBytesAndRejectsSubsequentOperations() = runTest {
        val raw = ByteArray(32) { (it * 3 + 7).toByte() }
        val plain = "harbor-manifest".encodeToByteArray()
        val sha256 = Sha256.digestHex(plain)
        val sealed = BundleCipher(raw, "harbor-town-2026").use { it.seal(plain, sha256) }

        val rawHandle = BundleKeyHandle.fromRawBytes("harbor-town-2026", raw)
        val softwareBacking = rawHandle.platformKey as SoftwareBundleKey
        assertContentEquals(raw, softwareBacking.masterCopyForTest())

        rawHandle.close()
        rawHandle.close()
        assertContentEquals(ByteArray(32), softwareBacking.masterCopyForTest(), "software key bytes zeroed on close")

        val closedCipher = BundleCipher(rawHandle)
        val afterClose = assertFailsWith<IllegalStateException> { closedCipher.open(sealed, sha256, "manifest") }
        assertTrue(afterClose.message.orEmpty().contains("closed"), "$afterClose")

        val exchange = BundleSessionKeyExchange.generate()
        val wrapped = BundleEncryptionKey("harbor-town-2026", raw).wrapFor(exchange.publicKeySpki)
        val unwrappedHandle = exchange.unwrap("harbor-town-2026", wrapped)
        exchange.close()
        exchange.close()

        val exchangeClosed = assertFailsWith<IllegalStateException> { exchange.unwrap("harbor-town-2026", wrapped) }
        assertTrue(exchangeClosed.message.orEmpty().contains("closed"), "$exchangeClosed")

        // Handles unwrapped before the exchange closed remain valid until closed themselves.
        assertContentEquals(plain, BundleCipher(unwrappedHandle).open(sealed, sha256, "manifest"))
        unwrappedHandle.close()
        (unwrappedHandle.platformKey as? SoftwareBundleKey)?.let {
            assertContentEquals(ByteArray(32), it.masterCopyForTest())
        }
        assertFailsWith<IllegalStateException> { BundleCipher(unwrappedHandle).open(sealed, sha256, "manifest") }
    }

    @Test
    fun platformBundleKeyStoreValidatesSafeNamespaces() {
        assertFailsWith<IllegalArgumentException> { platformBundleKeyStore("") }
        assertFailsWith<IllegalArgumentException> { platformBundleKeyStore("../escape") }
        assertFailsWith<IllegalArgumentException> { platformBundleKeyStore("trailing.") }
        assertFailsWith<IllegalArgumentException> { platformBundleKeyStore("CON") }
    }

    private class InMemoryHandleStore : BundleKeyStore {
        private val byId = mutableMapOf<String, BundleKeyHandle>()
        var deletes: Int = 0

        override suspend fun get(keyId: String): BundleKeyHandle? = byId[keyId]

        override suspend fun put(handle: BundleKeyHandle) {
            byId[handle.keyId] = handle
        }

        override suspend fun delete(keyId: String) {
            deletes++
            byId.remove(keyId)
        }
    }
}
