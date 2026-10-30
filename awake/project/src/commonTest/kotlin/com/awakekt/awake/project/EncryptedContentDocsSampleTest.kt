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

/** The "Encrypted content" guide includes these samples, so they keep compiling and doing what it says. */
class EncryptedContentDocsSampleTest {
    private val scene = "scenes/main.scene.json"

    /** Stands in for the app's own sign-in API. */
    private class AccountApi(private val key: ByteArray) {
        suspend fun bundleKey(keyId: String): ByteArray = key.also { check(keyId == EncryptedProject.KEY_ID) }
    }

    /** Stands in for a store the app keeps on the device, backed by the platform's key store. */
    private class DeviceKeyStore {
        private val keys = mutableMapOf<String, ByteArray>()

        suspend fun load(keyId: String): ByteArray? = keys[keyId]

        suspend fun save(keyId: String, key: ByteArray) {
            keys[keyId] = key
        }
    }

    @Test
    fun aPublishStepSealsBundlesWithAKeyFromOutsideTheRepository() = runTest {
        val looseIndex = looseIndexOf(BundledProject.SAMPLE_FILES)
        val keyFileBytes = EncryptedProject.KEY
        val upload = mutableMapOf<String, ByteArray>()

        // --8<-- [start:write]
        // 32 random bytes, read from a file or a CI secret, never from the repository.
        val key = BundleEncryptionKey(keyId = "harbor-town-2026", bytes = keyFileBytes)
        val writer = ContentBundleWriter(encryption = key)
        val index = writer.write(looseIndex, read = { entry -> BundledProject.SAMPLE_FILES.getValue(entry.path) }) { bundle ->
            upload[bundle.url] = bundle.bytes
        }
        // index.encryption names "harbor-town-2026"; the key itself is in neither the index nor a bundle.
        // --8<-- [end:write]

        val account = AccountApi(EncryptedProject.KEY)
        // --8<-- [start:online]
        val online = BundleKeyProvider { keyId -> account.bundleKey(keyId) }
        val files = IndexedAssetSource(index, fetch = { url -> upload.getValue(url) }, keys = online)
        // --8<-- [end:online]
        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), files.read(AssetPath(scene)).getOrThrow())

        val store = DeviceKeyStore()
        // --8<-- [start:offline]
        val keys = BundleKeyProvider { keyId ->
            store.load(keyId) ?: account.bundleKey(keyId).also { store.save(keyId, it) }
        }
        // --8<-- [end:offline]
        IndexedAssetSource(index, fetch = { url -> upload.getValue(url) }, keys = keys).read(AssetPath(scene)).getOrThrow()
        val signedOut = AccountApi(ByteArray(0))
        val later = BundleKeyProvider { keyId -> store.load(keyId) ?: signedOut.bundleKey(keyId) }
        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue(scene),
            IndexedAssetSource(index, fetch = { url -> upload.getValue(url) }, keys = later).read(AssetPath(scene)).getOrThrow(),
        )
    }
}
