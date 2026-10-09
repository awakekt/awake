/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkUnionMember

/**
 * A value to clear an attachment to: a colour ([VkClearColorValue]) or a depth/stencil pair
 * ([VkClearDepthStencilValue]) (`VkClearValue`).
 */
sealed class VkClearValue

/**
 * Maps to `VkClearValue value = {.color {ref}}`.
 */
@VkUnionMember("color")
sealed class VkClearColorValue : VkClearValue() {

    /**
     * A clear colour given as four 32-bit floats, for floating-point and normalized formats.
     *
     * @property values The red, green, blue and alpha components, in that order; the array must
     * hold exactly four values.
     */
    @VkUnionMember("float32", true)
    class Float32(val values: FloatArray = FloatArray(4)) : VkClearColorValue() {

        init {
            require(values.size == 4) { "float32 array must have a size of 4" }
        }
    }

    /**
     * A clear colour given as four signed 32-bit integers, for signed-integer formats.
     *
     * @property values The red, green, blue and alpha components, in that order; the array must
     * hold exactly four values.
     */
    @VkUnionMember("int32", true)
    class Int32(val values: IntArray = IntArray(4)) : VkClearColorValue() {
        init {
            require(values.size == 4) { "int32 array must have a size of 4" }
        }
    }

    /**
     * A clear colour given as four unsigned 32-bit integers, for unsigned-integer formats.
     *
     * @property values The red, green, blue and alpha components, in that order; the array must
     * hold exactly four values.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    @VkUnionMember("uint32", true)
    class UInt32(val values: UIntArray = UIntArray(4)) : VkClearColorValue() {
        init {
            require(values.size == 4) { "uint32 array must have a size of 4" }
        }
    }

    /** Convenience constructors for clear colours. */
    companion object {
        /**
         * Creates a floating-point clear colour from its components.
         *
         * @param r The red component.
         * @param g The green component.
         * @param b The blue component.
         * @param a The alpha component.
         * @return A [Float32] clear colour holding the four components.
         */
        fun rgba(r: Float, g: Float, b: Float, a: Float) = Float32().apply {
            values[0] = r
            values[1] = g
            values[2] = b
            values[3] = a
        }

        /**
         * Creates a signed-integer clear colour from its components.
         *
         * @param r The red component.
         * @param g The green component.
         * @param b The blue component.
         * @param a The alpha component.
         * @return An [Int32] clear colour holding the four components.
         */
        fun rgba(r: Int, g: Int, b: Int, a: Int) = Int32().apply {
            values[0] = r
            values[1] = g
            values[2] = b
            values[3] = a
        }
    }
}

/**
 * Maps to `VkClearValue value = {.depthStencil {ref}}`.
 *
 * @property depth The value to clear the depth aspect to, from 0 to 1.
 * @property stencil The value to clear the stencil aspect to.
 */
@VkUnionMember("depthStencil")
data class VkClearDepthStencilValue(
    val depth: Float,
    val stencil: Int,
) : VkClearValue()
