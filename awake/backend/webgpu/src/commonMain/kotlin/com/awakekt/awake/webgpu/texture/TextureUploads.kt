/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.render.texture.TextureUploadRecorder
import com.awakekt.awake.render.texture.WritableTexture
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo

/** Queue writes precede this frame's submit, and follow every already-submitted frame. */
internal class TextureUploads(private val graphicsDevice: GraphicsDevice) : TextureUploadRecorder {
    override fun write(texture: WritableTexture, region: TextureRegion) {
        region.validate(texture)
        graphicsDevice.wgpuContext.device.queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = (texture as Texture).texture, origin = Origin3D(region.x.toUInt(), region.y.toUInt(), region.layer.toUInt())),
            data = fastArrayBufferOf(region.data),
            dataLayout = TexelCopyBufferLayout(bytesPerRow = (region.width * 4).toUInt(), rowsPerImage = region.height.toUInt()),
            size = Extent3D(region.width.toUInt(), region.height.toUInt(), 1u),
        )
    }
}
