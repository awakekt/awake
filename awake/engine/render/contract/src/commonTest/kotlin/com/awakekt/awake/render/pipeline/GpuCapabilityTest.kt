/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GpuCapabilityTest {

    interface FakeCapability : GpuCapability {
        val featureLevel: Int
        companion object : GpuCapabilityKind<FakeCapability>
    }

    interface UnsupportedCapability : GpuCapability {
        companion object : GpuCapabilityKind<UnsupportedCapability>
    }

    private class FakeDevice(
        private val capability: FakeCapability? = null,
    ) : GpuDevice {
        override val clipSpace: ClipSpace = ClipSpace.Vulkan
        override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = error("unused")
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
        override fun destroy() {}

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = when (kind) {
            FakeCapability -> capability as? T
            else -> null
        }
    }

    @Test
    fun defaultGpuDeviceReturnsNullForAnyCapability() {
        val bareDevice = object : GpuDevice {
            override val clipSpace: ClipSpace = ClipSpace.Vulkan
            override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
            override fun createMaterial(
                texture: TextureAsset?,
                renderTarget: RenderTarget?,
                uniformFloatCount: Int,
                pbrTextures: PbrTextureSet?,
            ): Material = error("unused")
            override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
            override fun destroy() {}
        }

        assertNull(bareDevice.capability(FakeCapability))
        assertNull(bareDevice.capability(UnsupportedCapability))
    }

    @Test
    fun deviceWithCapabilityReturnsTypedInstance() {
        val instance = object : FakeCapability {
            override val featureLevel: Int = 42
        }
        val device = FakeDevice(instance)

        val queried = device.capability(FakeCapability)
        assertEquals(instance, queried)
        assertEquals(42, queried?.featureLevel)
        assertNull(device.capability(UnsupportedCapability))
    }
}
