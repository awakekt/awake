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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A plaintext public sign-in set plays before the key exists; the sealed set opens once it's attached. */
class EncryptedLayeredSourceTest {
    private val signIn = "scenes/sign-in.scene.json"
    private val harbor = "scenes/main.scene.json"

    @Test
    fun thePublicSetNeedsNoKeyAndTheSealedSetAsksOnlyOnceAttached() = runTest {
        val publicFiles = mapOf(signIn to "{\"version\": 1, \"name\": \"sign-in\"}".encodeToByteArray())
        val public = BundledProject(publicFiles)
        val sealed = EncryptedProject.write()
        var asks = 0
        val keys = BundleKeyProvider { asks++; EncryptedProject.KEY }
        val layered = LayeredAssetSource(IndexedAssetSource(public.index, public.server()))

        assertNull(public.index.encryption, "the public sign-in set is never sealed")
        assertContentEquals(publicFiles.getValue(signIn), layered.read(AssetPath(signIn)).getOrThrow())
        layered.attachProtected(IndexedAssetSource(sealed.index, sealed.server(), keys = keys))
        assertEquals(0, asks, "attaching asks for no key")

        assertContentEquals(sealed.files.getValue(harbor), layered.read(AssetPath(harbor)).getOrThrow())
        assertEquals(1, asks)
    }
}
