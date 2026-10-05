/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.capture

/**
 * A logical attachment a renderer may expose to a frame/debug inspector.
 *
 * [Normal] is an opt-in diagnostic/G-buffer attachment, not something every forward pass has
 * implicitly. Likewise, [Stencil] only becomes available when the target uses a depth-stencil
 * format rather than a depth-only format.
 */
enum class FramebufferAttachment {
    /** Primary color attachment. */
    Color0,
    /** Depth buffer attachment. */
    Depth,
    /** Stencil buffer attachment. */
    Stencil,
    /** Normal buffer attachment (diagnostic or G-buffer). */
    Normal,
}

/**
 * Backend-neutral CPU representation of one inspected attachment.
 *
 * Values are tightly packed, row-major, bottom-to-top-independent (the backend's normal
 * readback orientation), with [channels] float values per pixel. An unavailable attachment is
 * represented explicitly instead of being confused with a cleared attachment.
 */
data class FramebufferAttachmentData(
    /** The attachment identifier this data represents. */
    val attachment: FramebufferAttachment,
    /** Width of the attachment image in pixels. */
    val width: Int,
    /** Height of the attachment image in pixels. */
    val height: Int,
    /** Number of float channels per pixel (e.g. 1 for depth, 4 for RGBA). */
    val channels: Int,
    /** Packed float channel values for all pixels. */
    val values: FloatArray = FloatArray(0),
    /** Whether this attachment is available for inspection in the current pass. */
    val available: Boolean = true,
    /** Human-readable explanation when [available] is `false`, or `null` when available. */
    val reason: String? = null,
) {
    init {
        require(width >= 0 && height >= 0) { "Framebuffer dimensions must be non-negative." }
        require(channels >= 0) { "Framebuffer channel count must be non-negative." }
        if (available) {
            require(values.size == width * height * channels) {
                "${attachment.name} needs ${width * height * channels} values, got ${values.size}."
            }
        } else {
            require(values.isEmpty()) { "Unavailable ${attachment.name} data cannot contain pixels." }
            require(reason != null) { "Unavailable ${attachment.name} data needs a reason." }
        }
    }

    /** Factory functions for creating attachment debug data instances. */
    companion object {
        /** Creates color attachment data by normalizing bytes from an RGBA8 [asset]. */
        fun fromRgba8(asset: com.awakekt.awake.render.texture.TextureAsset): FramebufferAttachmentData {
            val values = FloatArray(asset.data.size)
            asset.data.forEachIndexed { index, byte ->
                values[index] = (byte.toInt() and 0xff) / 255f
            }
            return FramebufferAttachmentData(
                attachment = FramebufferAttachment.Color0,
                width = asset.width,
                height = asset.height,
                channels = 4,
                values = values,
            )
        }

        /** Creates a placeholder record indicating [attachment] is unavailable due to [reason]. */
        fun unavailable(attachment: FramebufferAttachment, reason: String): FramebufferAttachmentData =
            FramebufferAttachmentData(
                attachment = attachment,
                width = 0,
                height = 0,
                channels = 0,
                available = false,
                reason = reason,
            )
    }
}
