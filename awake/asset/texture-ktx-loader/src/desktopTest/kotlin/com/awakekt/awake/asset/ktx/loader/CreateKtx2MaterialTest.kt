/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateKtx2MaterialTest {

    @Test
    fun aDeviceWithBc7TakesTheTextureCompressed() {
        val device = FakeDevice(FakeCompressedTextures(setOf(CompressedTextureFormat.Bc7Rgba), accepts = true))

        assertNotNull(device.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))

        assertEquals(CompressedTextureFormat.Bc7Rgba, device.compressed?.uploaded?.format)
        assertNull(device.rgba, "nothing went the RGBA8 way")
    }

    @Test
    fun aDeviceThatRefusesTheImageGetsRgba8AndOneWithoutTheCapabilityToo() {
        val refusing = FakeDevice(FakeCompressedTextures(setOf(CompressedTextureFormat.Bc7Rgba), accepts = false))
        val plain = FakeDevice(null)

        assertNotNull(refusing.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))
        assertNotNull(plain.createKtx2Material(CookedTextures.bytes("rock.ktx2"), LibKtxBasisTranscoder))

        assertEquals(CookedTextures.SIZE, refusing.rgba?.width)
        assertEquals(CookedTextures.SIZE, plain.rgba?.width)
    }

    @Test
    fun aFileThisDeviceCantTakeMakesNoMaterial() {
        assertNull(FakeDevice(null).createKtx2Material(CookedTextures.bytes("rock.astc.ktx2"), LibKtxBasisTranscoder))
    }
}
