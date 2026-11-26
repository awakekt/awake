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
import com.awakekt.awake.webgpu.writeTextureBytes

/** Queue writes precede this frame's submit, and follow every already-submitted frame. */
internal class TextureUploads(private val graphicsDevice: GraphicsDevice) : TextureUploadRecorder {
    override fun write(texture: WritableTexture, region: TextureRegion) {
        region.validate(texture)
        graphicsDevice.wgpuContext.device.queue.writeTextureBytes(
            texture = (texture as Texture).texture,
            data = region.data,
            bytesPerRow = region.width * region.pixelFormat.bytesPerTexel,
            rowsPerImage = region.height,
            width = region.width,
            height = region.height,
            originX = region.x,
            originY = region.y,
            originZ = region.layer,
        )
    }
}
